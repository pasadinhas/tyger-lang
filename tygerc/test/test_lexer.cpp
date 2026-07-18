#include "test_runner.h"
#include "lexer.h"
#include "arena.h"

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static Arena g_arena;

// Lex a source string, assert no error, and return the token list.
// The returned tokens reference g_arena and the source string.
static TokenList must_lex(const char *src) {
    Arena *a = &g_arena;
    LexResult r = lex(sv_from_cstr(src), a);
    if (r.error) {
        fprintf(stderr, "  lex error: %s\n", r.error);
        // Return empty — callers will fail their own assertions
        TokenList empty = {0};
        return empty;
    }
    return r.tokens;
}

// Assert a single-token result (plus EOF).
static void assert_single_token(const char *src, TokenType expected_type, const char *expected_value) {
    TokenList tokens = must_lex(src);
    ASSERT(tokens.len == 2); // token + EOF
    ASSERT_EQ(expected_type, tokens.data[0].type);
    if (expected_value) {
        ASSERT(sv_eq_cstr(tokens.data[0].value, expected_value));
    }
    ASSERT_EQ(TK_EOF, tokens.data[1].type);
    da_free(&tokens);
}

// ---------------------------------------------------------------------------
// Single-token tests — one per TokenType
// ---------------------------------------------------------------------------

static void test_tk_let()         { assert_single_token("let",    TK_LET,        NULL); }
static void test_tk_mut()         { assert_single_token("mut",    TK_MUT,        NULL); }
static void test_tk_fn()          { assert_single_token("fn",     TK_FN,         NULL); }
static void test_tk_extern()      { assert_single_token("extern", TK_EXTERN,     NULL); }
static void test_tk_if()          { assert_single_token("if",     TK_IF,         NULL); }
static void test_tk_else()        { assert_single_token("else",   TK_ELSE,       NULL); }
static void test_tk_return()      { assert_single_token("return", TK_RETURN,     NULL); }
static void test_tk_true()        { assert_single_token("true",   TK_TRUE,       NULL); }
static void test_tk_false()       { assert_single_token("false",  TK_FALSE,      NULL); }
static void test_tk_while()       { assert_single_token("while",  TK_WHILE,      NULL); }
static void test_tk_break()       { assert_single_token("break",  TK_BREAK,      NULL); }
static void test_tk_continue()    { assert_single_token("continue", TK_CONTINUE, NULL); }
static void test_tk_dotdotdot()   { assert_single_token("...",    TK_DOTDOTDOT,  NULL); }
static void test_tk_arrow()       { assert_single_token("->",     TK_ARROW,      NULL); }
static void test_tk_geq()         { assert_single_token(">=",     TK_GEQ,        NULL); }
static void test_tk_leq()         { assert_single_token("<=",     TK_LEQ,        NULL); }
static void test_tk_eqeq()        { assert_single_token("==",     TK_EQEQ,       NULL); }
static void test_tk_neq()         { assert_single_token("!=",     TK_NEQ,        NULL); }
static void test_tk_plus_eq()     { assert_single_token("+=",     TK_PLUS_EQ,    NULL); }
static void test_tk_minus_eq()    { assert_single_token("-=",     TK_MINUS_EQ,   NULL); }
static void test_tk_star_eq()     { assert_single_token("*=",     TK_STAR_EQ,    NULL); }
static void test_tk_slash_eq()    { assert_single_token("/=",     TK_SLASH_EQ,   NULL); }
static void test_tk_percent_eq()  { assert_single_token("%=",     TK_PERCENT_EQ, NULL); }
static void test_tk_gt()          { assert_single_token(">",      TK_GT,         NULL); }
static void test_tk_lt()          { assert_single_token("<",      TK_LT,         NULL); }
static void test_tk_eq()          { assert_single_token("=",      TK_EQ,         NULL); }
static void test_tk_plus()        { assert_single_token("+",      TK_PLUS,       NULL); }
static void test_tk_minus()       { assert_single_token("-",      TK_MINUS,      NULL); }
static void test_tk_star()        { assert_single_token("*",      TK_STAR,       NULL); }
static void test_tk_slash()       { assert_single_token("/",      TK_SLASH,      NULL); }
static void test_tk_percent()     { assert_single_token("%",      TK_PERCENT,    NULL); }
static void test_tk_lparen()      { assert_single_token("(",      TK_LPAREN,     NULL); }
static void test_tk_rparen()      { assert_single_token(")",      TK_RPAREN,     NULL); }
static void test_tk_lbrace()      { assert_single_token("{",      TK_LBRACE,     NULL); }
static void test_tk_rbrace()      { assert_single_token("}",      TK_RBRACE,     NULL); }
static void test_tk_semicolon()   { assert_single_token(";",      TK_SEMICOLON,  NULL); }
static void test_tk_colon()       { assert_single_token(":",      TK_COLON,      NULL); }
static void test_tk_comma()       { assert_single_token(",",      TK_COMMA,      NULL); }
static void test_tk_identifier()  { assert_single_token("abc",    TK_IDENTIFIER, "abc"); }
static void test_tk_number()      { assert_single_token("42",     TK_NUMBER,     "42"); }

static void test_tk_eof() {
    TokenList tokens = must_lex("");
    ASSERT(tokens.len == 1);
    ASSERT_EQ(TK_EOF, tokens.data[0].type);
    da_free(&tokens);
}

// ---------------------------------------------------------------------------
// Keyword boundary: keyword followed immediately by identifier chars
// should lex as an identifier, not a keyword.
// ---------------------------------------------------------------------------

static void test_keyword_boundary_let() {
    TokenList tokens = must_lex("letters");
    ASSERT(tokens.len == 2);
    ASSERT_EQ(TK_IDENTIFIER, tokens.data[0].type);
    ASSERT(sv_eq_cstr(tokens.data[0].value, "letters"));
    da_free(&tokens);
}

static void test_keyword_boundary_fn() {
    TokenList tokens = must_lex("fns");
    ASSERT(tokens.len == 2);
    ASSERT_EQ(TK_IDENTIFIER, tokens.data[0].type);
    da_free(&tokens);
}

// ---------------------------------------------------------------------------
// String literal tests
// ---------------------------------------------------------------------------

static void test_string_basic() {
    TokenList tokens = must_lex("\"hello\"");
    ASSERT(tokens.len == 2);
    ASSERT_EQ(TK_STRING, tokens.data[0].type);
    ASSERT(sv_eq_cstr(tokens.data[0].value, "hello"));
    da_free(&tokens);
}

static void test_string_escape_newline() {
    TokenList tokens = must_lex("\"a\\nb\"");
    ASSERT(tokens.len == 2);
    ASSERT_EQ(TK_STRING, tokens.data[0].type);
    ASSERT_EQ(3u, tokens.data[0].value.len);
    ASSERT(tokens.data[0].value.data[1] == '\n');
    da_free(&tokens);
}

static void test_string_escape_tab() {
    TokenList tokens = must_lex("\"a\\tb\"");
    ASSERT(tokens.len == 2);
    ASSERT(tokens.data[0].value.data[1] == '\t');
    da_free(&tokens);
}

static void test_string_escape_quote() {
    TokenList tokens = must_lex("\"say \\\"hi\\\"\"");
    ASSERT(tokens.len == 2);
    ASSERT(sv_eq_cstr(tokens.data[0].value, "say \"hi\""));
    da_free(&tokens);
}

// ---------------------------------------------------------------------------
// Multi-char operator disambiguation
// ---------------------------------------------------------------------------

static void test_geq_not_gt() {
    TokenList tokens = must_lex(">=");
    ASSERT_EQ(TK_GEQ, tokens.data[0].type);
    da_free(&tokens);
}

static void test_gt_alone() {
    TokenList tokens = must_lex("> ");
    ASSERT_EQ(TK_GT, tokens.data[0].type);
    da_free(&tokens);
}

static void test_arrow_not_minus() {
    TokenList tokens = must_lex("->");
    ASSERT_EQ(TK_ARROW, tokens.data[0].type);
    da_free(&tokens);
}

static void test_minus_alone() {
    TokenList tokens = must_lex("- ");
    ASSERT_EQ(TK_MINUS, tokens.data[0].type);
    da_free(&tokens);
}

// ---------------------------------------------------------------------------
// Line comment skipping
// ---------------------------------------------------------------------------

static void test_line_comment() {
    TokenList tokens = must_lex("// this is a comment\n42");
    ASSERT(tokens.len == 2);
    ASSERT_EQ(TK_NUMBER, tokens.data[0].type);
    ASSERT(sv_eq_cstr(tokens.data[0].value, "42"));
    da_free(&tokens);
}

// ---------------------------------------------------------------------------
// Multi-token source file test (mirrors the TypeScript test1.ty test)
// ---------------------------------------------------------------------------

static void test_multi_token_source() {
    const char *src =
        "let x = (21 + 21) / 2;\n"
        "let y = x * 2 - 1;\n";

    TokenList tokens = must_lex(src);

    // let x = ( 21 + 21 ) / 2 ;  let y  = x  *  2  -  1  ;  EOF
    //  0  1 2 3  4  5  6 7 8 9 10  11 12 13 14 15 16 17 18 19  20
    ASSERT(tokens.len == 21);

    ASSERT_EQ(TK_LET,        tokens.data[0].type);
    ASSERT_EQ(TK_IDENTIFIER, tokens.data[1].type);
    ASSERT(sv_eq_cstr(tokens.data[1].value, "x"));
    ASSERT_EQ(TK_EQ,         tokens.data[2].type);
    ASSERT_EQ(TK_LPAREN,     tokens.data[3].type);
    ASSERT_EQ(TK_NUMBER,     tokens.data[4].type);
    ASSERT(sv_eq_cstr(tokens.data[4].value, "21"));
    ASSERT_EQ(TK_PLUS,       tokens.data[5].type);
    ASSERT_EQ(TK_NUMBER,     tokens.data[6].type);
    ASSERT(sv_eq_cstr(tokens.data[6].value, "21"));
    ASSERT_EQ(TK_RPAREN,     tokens.data[7].type);
    ASSERT_EQ(TK_SLASH,      tokens.data[8].type);
    ASSERT_EQ(TK_NUMBER,     tokens.data[9].type);
    ASSERT(sv_eq_cstr(tokens.data[9].value, "2"));
    ASSERT_EQ(TK_SEMICOLON,  tokens.data[10].type);
    ASSERT_EQ(TK_LET,        tokens.data[11].type);
    ASSERT_EQ(TK_IDENTIFIER, tokens.data[12].type);
    ASSERT(sv_eq_cstr(tokens.data[12].value, "y"));
    ASSERT_EQ(TK_EQ,         tokens.data[13].type);
    ASSERT_EQ(TK_IDENTIFIER, tokens.data[14].type);
    ASSERT(sv_eq_cstr(tokens.data[14].value, "x"));
    ASSERT_EQ(TK_STAR,       tokens.data[15].type);
    ASSERT_EQ(TK_NUMBER,     tokens.data[16].type);
    ASSERT(sv_eq_cstr(tokens.data[16].value, "2"));
    ASSERT_EQ(TK_MINUS,      tokens.data[17].type);
    ASSERT_EQ(TK_NUMBER,     tokens.data[18].type);
    ASSERT(sv_eq_cstr(tokens.data[18].value, "1"));
    ASSERT_EQ(TK_SEMICOLON,  tokens.data[19].type);
    ASSERT_EQ(TK_EOF,        tokens.data[20].type);

    da_free(&tokens);
}

// ---------------------------------------------------------------------------
// Location tests
// ---------------------------------------------------------------------------

static void test_loc_first_token() {
    TokenList tokens = must_lex("let");
    ASSERT_EQ(1u, tokens.data[0].loc.line);
    ASSERT_EQ(1u, tokens.data[0].loc.col);
    da_free(&tokens);
}

static void test_loc_second_token_same_line() {
    TokenList tokens = must_lex("let x");
    // "let" starts at col 1, "x" starts at col 5
    ASSERT_EQ(1u, tokens.data[0].loc.line);
    ASSERT_EQ(1u, tokens.data[0].loc.col);
    ASSERT_EQ(1u, tokens.data[1].loc.line);
    ASSERT_EQ(5u, tokens.data[1].loc.col);
    da_free(&tokens);
}

static void test_loc_newline_resets_col() {
    TokenList tokens = must_lex("let\nx");
    ASSERT_EQ(1u, tokens.data[0].loc.line);
    ASSERT_EQ(1u, tokens.data[0].loc.col);
    ASSERT_EQ(2u, tokens.data[1].loc.line);
    ASSERT_EQ(1u, tokens.data[1].loc.col);
    da_free(&tokens);
}

static void test_loc_multiple_lines() {
    TokenList tokens = must_lex("let x = 1;\nlet y = 2;");
    // "let" on line 1
    ASSERT_EQ(1u, tokens.data[0].loc.line);
    ASSERT_EQ(1u, tokens.data[0].loc.col);
    // second "let" on line 2
    ASSERT_EQ(2u, tokens.data[5].loc.line);
    ASSERT_EQ(1u, tokens.data[5].loc.col);
    da_free(&tokens);
}

static void test_loc_eof() {
    TokenList tokens = must_lex("x");
    // EOF follows after "x" (1 char), so col 2
    ASSERT_EQ(1u, tokens.data[1].loc.line);
    ASSERT_EQ(2u, tokens.data[1].loc.col);
    da_free(&tokens);
}

static void test_error_unexpected_char() {
    LexResult r = lex(sv_from_cstr("@"), &g_arena);
    ASSERT_NOT_NULL(r.error);
    da_free(&r.tokens);
}

static void test_error_unterminated_string() {
    LexResult r = lex(sv_from_cstr("\"hello"), &g_arena);
    ASSERT_NOT_NULL(r.error);
    da_free(&r.tokens);
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

int main(void) {
    arena_init(&g_arena, 0);

    printf("=== Single token tests ===\n");
    RUN_TEST(test_tk_let);
    RUN_TEST(test_tk_mut);
    RUN_TEST(test_tk_fn);
    RUN_TEST(test_tk_extern);
    RUN_TEST(test_tk_if);
    RUN_TEST(test_tk_else);
    RUN_TEST(test_tk_return);
    RUN_TEST(test_tk_true);
    RUN_TEST(test_tk_false);
    RUN_TEST(test_tk_while);
    RUN_TEST(test_tk_break);
    RUN_TEST(test_tk_continue);
    RUN_TEST(test_tk_dotdotdot);
    RUN_TEST(test_tk_arrow);
    RUN_TEST(test_tk_geq);
    RUN_TEST(test_tk_leq);
    RUN_TEST(test_tk_eqeq);
    RUN_TEST(test_tk_neq);
    RUN_TEST(test_tk_plus_eq);
    RUN_TEST(test_tk_minus_eq);
    RUN_TEST(test_tk_star_eq);
    RUN_TEST(test_tk_slash_eq);
    RUN_TEST(test_tk_percent_eq);
    RUN_TEST(test_tk_gt);
    RUN_TEST(test_tk_lt);
    RUN_TEST(test_tk_eq);
    RUN_TEST(test_tk_plus);
    RUN_TEST(test_tk_minus);
    RUN_TEST(test_tk_star);
    RUN_TEST(test_tk_slash);
    RUN_TEST(test_tk_percent);
    RUN_TEST(test_tk_lparen);
    RUN_TEST(test_tk_rparen);
    RUN_TEST(test_tk_lbrace);
    RUN_TEST(test_tk_rbrace);
    RUN_TEST(test_tk_semicolon);
    RUN_TEST(test_tk_colon);
    RUN_TEST(test_tk_comma);
    RUN_TEST(test_tk_identifier);
    RUN_TEST(test_tk_number);
    RUN_TEST(test_tk_eof);

    printf("\n=== Keyword boundary tests ===\n");
    RUN_TEST(test_keyword_boundary_let);
    RUN_TEST(test_keyword_boundary_fn);

    printf("\n=== String literal tests ===\n");
    RUN_TEST(test_string_basic);
    RUN_TEST(test_string_escape_newline);
    RUN_TEST(test_string_escape_tab);
    RUN_TEST(test_string_escape_quote);

    printf("\n=== Operator disambiguation tests ===\n");
    RUN_TEST(test_geq_not_gt);
    RUN_TEST(test_gt_alone);
    RUN_TEST(test_arrow_not_minus);
    RUN_TEST(test_minus_alone);

    printf("\n=== Comment tests ===\n");
    RUN_TEST(test_line_comment);

    printf("\n=== Multi-token source test ===\n");
    RUN_TEST(test_multi_token_source);

    printf("\n=== Location tests ===\n");
    RUN_TEST(test_loc_first_token);
    RUN_TEST(test_loc_second_token_same_line);
    RUN_TEST(test_loc_newline_resets_col);
    RUN_TEST(test_loc_multiple_lines);
    RUN_TEST(test_loc_eof);

    printf("\n=== Error tests ===\n");
    RUN_TEST(test_error_unexpected_char);
    RUN_TEST(test_error_unterminated_string);

    arena_free(&g_arena);
    return TEST_RESULTS();
}
