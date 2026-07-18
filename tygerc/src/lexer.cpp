#include "lexer.h"
#include <cctype>
#include <cstdio>
#include <cstring>

// ---------------------------------------------------------------------------
// token_type_name
// ---------------------------------------------------------------------------

const char *token_type_name(TokenType type) {
    // If TK_COUNT changes, update this switch.
    static_assert(TK_COUNT == 39, "token_type_name: token list changed, update this switch");
    switch (type) {
        case TK_LET:         return "let";
        case TK_MUT:         return "mut";
        case TK_FN:          return "fn";
        case TK_EXTERN:      return "extern";
        case TK_IF:          return "if";
        case TK_ELSE:        return "else";
        case TK_RETURN:      return "return";
        case TK_TRUE:        return "true";
        case TK_FALSE:       return "false";
        case TK_DOTDOTDOT:   return "...";
        case TK_ARROW:       return "->";
        case TK_GEQ:         return ">=";
        case TK_LEQ:         return "<=";
        case TK_EQEQ:        return "==";
        case TK_NEQ:         return "!=";
        case TK_PLUS_EQ:     return "+=";
        case TK_MINUS_EQ:    return "-=";
        case TK_STAR_EQ:     return "*=";
        case TK_SLASH_EQ:    return "/=";
        case TK_PERCENT_EQ:  return "%=";
        case TK_GT:          return ">";
        case TK_LT:          return "<";
        case TK_EQ:          return "=";
        case TK_PLUS:        return "+";
        case TK_MINUS:       return "-";
        case TK_STAR:        return "*";
        case TK_SLASH:       return "/";
        case TK_PERCENT:     return "%";
        case TK_LPAREN:      return "(";
        case TK_RPAREN:      return ")";
        case TK_LBRACE:      return "{";
        case TK_RBRACE:      return "}";
        case TK_SEMICOLON:   return ";";
        case TK_COLON:       return ":";
        case TK_COMMA:       return ",";
        case TK_IDENTIFIER:  return "Identifier";
        case TK_NUMBER:      return "Number";
        case TK_STRING:      return "String";
        case TK_EOF:         return "EOF";
        case TK_COUNT:       break; // sentinel, not a real token
    }
    return "<unknown>";
}

// ---------------------------------------------------------------------------
// Lexer state
// ---------------------------------------------------------------------------

typedef struct {
    const char *start;   // start of the source buffer
    const char *cur;     // current position
    const char *end;     // one past the last character
    uint32_t    line;    // current line (1-based)
    uint32_t    col;     // current column (1-based)
    Arena      *arena;
    char        errbuf[256];
} Lexer;

static inline char peek(const Lexer *l, int offset) {
    const char *p = l->cur + offset;
    return (p < l->end) ? *p : '\0';
}

static inline void advance(Lexer *l, int n) {
    for (int i = 0; i < n; i++) {
        if (l->cur < l->end && *l->cur == '\n') {
            l->line++;
            l->col = 1;
        } else {
            l->col++;
        }
        l->cur++;
    }
}

static inline Loc current_loc(const Lexer *l) {
    Loc loc;
    loc.line = l->line;
    loc.col  = l->col;
    return loc;
}

static inline bool at_end(const Lexer *l) {
    return l->cur >= l->end;
}

// Check if the next `len` chars match `s` exactly.
static bool match_str(const Lexer *l, const char *s, int len) {
    if (l->cur + len > l->end) return false;
    return memcmp(l->cur, s, (size_t)len) == 0;
}

static inline bool is_ident_char(char c) {
    return isalnum((unsigned char)c) || c == '_';
}

static inline bool is_digit(char c) {
    return c >= '0' && c <= '9';
}

static inline bool is_whitespace(char c) {
    return c == ' ' || c == '\n' || c == '\r' || c == '\t';
}

// Try to match a keyword. Succeeds only if the keyword is not followed
// by an identifier character (so "letters" doesn't match "let").
static bool try_keyword(Lexer *l, const char *kw, int kw_len, TokenType type, Token *out) {
    if (!match_str(l, kw, kw_len)) return false;
    if (is_ident_char(peek(l, kw_len))) return false;
    out->type  = type;
    out->value = sv_from_parts(l->cur, 0); // empty value for keywords
    advance(l, kw_len);
    return true;
}

// Try to match an exact string (operator / punctuation).
static bool try_exact(Lexer *l, const char *s, int len, TokenType type, Token *out) {
    if (!match_str(l, s, len)) return false;
    out->type  = type;
    out->value = sv_from_parts(l->cur, 0);
    advance(l, len);
    return true;
}

// Lex a number (sequence of digits).
static bool try_number(Lexer *l, Token *out) {
    if (!is_digit(peek(l, 0))) return false;
    const char *start = l->cur;
    while (is_digit(peek(l, 0))) advance(l, 1);
    out->type  = TK_NUMBER;
    out->value = sv_from_parts(start, (size_t)(l->cur - start));
    return true;
}

// Lex an identifier.
static bool try_identifier(Lexer *l, Token *out) {
    if (!is_ident_char(peek(l, 0)) || is_digit(peek(l, 0))) return false;
    const char *start = l->cur;
    while (is_ident_char(peek(l, 0))) advance(l, 1);
    out->type  = TK_IDENTIFIER;
    out->value = sv_from_parts(start, (size_t)(l->cur - start));
    return true;
}

// Lex a string literal. Resolves escape sequences.
// The resulting value is arena-allocated and null-terminated.
static bool try_string(Lexer *l, Token *out) {
    if (peek(l, 0) != '"') return false;
    advance(l, 1); // opening "

    // First pass: measure the output length
    const char *scan = l->cur;
    size_t out_len = 0;
    while (scan < l->end && *scan != '"') {
        if (*scan == '\\') {
            scan++; // skip backslash
            if (scan >= l->end) break;
        }
        out_len++;
        scan++;
    }

    // Allocate output buffer in arena
    char *buf = (char *)arena_alloc(l->arena, out_len + 1, 1);
    size_t i = 0;

    while (!at_end(l) && peek(l, 0) != '"') {
        char c = peek(l, 0);
        if (c == '\\') {
            advance(l, 1);
            char esc = peek(l, 0);
            advance(l, 1);
            switch (esc) {
                case 'n':  buf[i++] = '\n'; break;
                case 't':  buf[i++] = '\t'; break;
                case '"':  buf[i++] = '"';  break;
                case '0':  buf[i++] = '\0'; break;
                case '\\': buf[i++] = '\\'; break;
                default:
                    snprintf(l->errbuf, sizeof(l->errbuf),
                             "unknown escape sequence: \\%c", esc);
                    return false;
            }
        } else {
            buf[i++] = c;
            advance(l, 1);
        }
    }

    if (at_end(l) || peek(l, 0) != '"') {
        snprintf(l->errbuf, sizeof(l->errbuf), "unterminated string literal");
        return false;
    }
    advance(l, 1); // closing "

    buf[i] = '\0';
    out->type  = TK_STRING;
    out->value = sv_from_parts(buf, i);
    return true;
}

// ---------------------------------------------------------------------------
// lex()
// ---------------------------------------------------------------------------

LexResult lex(SV source, Arena *arena) {
    LexResult result;
    result.tokens.data = NULL;
    result.tokens.len  = 0;
    result.tokens.cap  = 0;
    result.error       = NULL;

    Lexer l;
    l.start    = source.data;
    l.cur      = source.data;
    l.end      = source.data + source.len;
    l.line     = 1;
    l.col      = 1;
    l.arena    = arena;
    l.errbuf[0] = '\0';

    while (!at_end(&l)) {
        // Skip whitespace
        if (is_whitespace(peek(&l, 0))) {
            advance(&l, 1);
            continue;
        }

        // Skip line comments (//)
        if (peek(&l, 0) == '/' && peek(&l, 1) == '/') {
            while (!at_end(&l) && peek(&l, 0) != '\n') advance(&l, 1);
            continue;
        }

        // If TK_COUNT changes, update the matcher chain below.
        static_assert(TK_COUNT == 39, "lex: token list changed, update the matcher chain");

        Loc tok_loc = current_loc(&l);
        Token tok;
        tok.type  = TK_EOF;
        tok.value = sv_from_parts(l.cur, 0);
        tok.loc   = tok_loc;
        bool matched = false;

        // Keywords (must be tried before identifiers)
        if      (try_keyword(&l, "extern", 6, TK_EXTERN, &tok)) matched = true;
        else if (try_keyword(&l, "return", 6, TK_RETURN, &tok)) matched = true;
        else if (try_keyword(&l, "false",  5, TK_FALSE,  &tok)) matched = true;
        else if (try_keyword(&l, "true",   4, TK_TRUE,   &tok)) matched = true;
        else if (try_keyword(&l, "else",   4, TK_ELSE,   &tok)) matched = true;
        else if (try_keyword(&l, "let",    3, TK_LET,    &tok)) matched = true;
        else if (try_keyword(&l, "mut",    3, TK_MUT,    &tok)) matched = true;
        else if (try_keyword(&l, "fn",     2, TK_FN,     &tok)) matched = true;
        else if (try_keyword(&l, "if",     2, TK_IF,     &tok)) matched = true;

        // Multi-char operators (longer matches first)
        else if (try_exact(&l, "...", 3, TK_DOTDOTDOT,  &tok)) matched = true;
        else if (try_exact(&l, "->",  2, TK_ARROW,      &tok)) matched = true;
        else if (try_exact(&l, ">=",  2, TK_GEQ,        &tok)) matched = true;
        else if (try_exact(&l, "<=",  2, TK_LEQ,        &tok)) matched = true;
        else if (try_exact(&l, "==",  2, TK_EQEQ,       &tok)) matched = true;
        else if (try_exact(&l, "!=",  2, TK_NEQ,        &tok)) matched = true;
        else if (try_exact(&l, "+=",  2, TK_PLUS_EQ,    &tok)) matched = true;
        else if (try_exact(&l, "-=",  2, TK_MINUS_EQ,   &tok)) matched = true;
        else if (try_exact(&l, "*=",  2, TK_STAR_EQ,    &tok)) matched = true;
        else if (try_exact(&l, "/=",  2, TK_SLASH_EQ,   &tok)) matched = true;
        else if (try_exact(&l, "%=",  2, TK_PERCENT_EQ, &tok)) matched = true;

        // Single-char operators
        else if (try_exact(&l, ">", 1, TK_GT,        &tok)) matched = true;
        else if (try_exact(&l, "<", 1, TK_LT,        &tok)) matched = true;
        else if (try_exact(&l, "=", 1, TK_EQ,        &tok)) matched = true;
        else if (try_exact(&l, "+", 1, TK_PLUS,      &tok)) matched = true;
        else if (try_exact(&l, "-", 1, TK_MINUS,     &tok)) matched = true;
        else if (try_exact(&l, "*", 1, TK_STAR,      &tok)) matched = true;
        else if (try_exact(&l, "/", 1, TK_SLASH,     &tok)) matched = true;
        else if (try_exact(&l, "%", 1, TK_PERCENT,   &tok)) matched = true;
        else if (try_exact(&l, "(", 1, TK_LPAREN,    &tok)) matched = true;
        else if (try_exact(&l, ")", 1, TK_RPAREN,    &tok)) matched = true;
        else if (try_exact(&l, "{", 1, TK_LBRACE,    &tok)) matched = true;
        else if (try_exact(&l, "}", 1, TK_RBRACE,    &tok)) matched = true;
        else if (try_exact(&l, ";", 1, TK_SEMICOLON, &tok)) matched = true;
        else if (try_exact(&l, ":", 1, TK_COLON,     &tok)) matched = true;
        else if (try_exact(&l, ",", 1, TK_COMMA,     &tok)) matched = true;

        // Literals
        else if (try_string(&l, &tok))     matched = true;
        else if (try_number(&l, &tok))     matched = true;
        else if (try_identifier(&l, &tok)) matched = true;

        if (!matched) {
            if (l.errbuf[0] != '\0') {
                result.error = arena_copy_str(arena, l.errbuf, strlen(l.errbuf));
            } else {
                char tmp[64];
                snprintf(tmp, sizeof(tmp), "unexpected character: '%c' (0x%02x)",
                         peek(&l, 0), (unsigned char)peek(&l, 0));
                result.error = arena_copy_str(arena, tmp, strlen(tmp));
            }
            return result;
        }

        da_push(&result.tokens, tok);
    }

    // Emit EOF
    Token eof_tok;
    eof_tok.type  = TK_EOF;
    eof_tok.value = sv_from_parts(l.cur, 0);
    eof_tok.loc   = current_loc(&l);
    da_push(&result.tokens, eof_tok);

    return result;
}
