#pragma once

#include "sv.h"
#include "da.h"
#include "arena.h"

// ---------------------------------------------------------------------------
// Token types
// ---------------------------------------------------------------------------

typedef enum {
    // Keywords
    TK_LET,
    TK_MUT,
    TK_FN,
    TK_EXTERN,
    TK_IF,
    TK_ELSE,
    TK_RETURN,
    TK_TRUE,
    TK_FALSE,

    // Multi-char operators / punctuation
    TK_DOTDOTDOT,  // ...
    TK_ARROW,      // ->
    TK_GEQ,        // >=
    TK_LEQ,        // <=
    TK_EQEQ,       // ==
    TK_NEQ,        // !=
    TK_PLUS_EQ,    // +=
    TK_MINUS_EQ,   // -=
    TK_STAR_EQ,    // *=
    TK_SLASH_EQ,   // /=
    TK_PERCENT_EQ, // %=

    // Single-char operators
    TK_GT,         // >
    TK_LT,         // <
    TK_EQ,         // =
    TK_PLUS,       // +
    TK_MINUS,      // -
    TK_STAR,       // *
    TK_SLASH,      // /
    TK_PERCENT,    // %

    // Delimiters
    TK_LPAREN,     // (
    TK_RPAREN,     // )
    TK_LBRACE,     // {
    TK_RBRACE,     // }
    TK_SEMICOLON,  // ;
    TK_COLON,      // :
    TK_COMMA,      // ,

    // Literals / identifiers
    TK_IDENTIFIER,
    TK_NUMBER,
    TK_STRING,

    TK_EOF,

    TK_COUNT, // must remain last — used in static_assert exhaustiveness checks
} TokenType;

// Returns a static human-readable name for a token type.
const char *token_type_name(TokenType type);

// ---------------------------------------------------------------------------
// Token
// ---------------------------------------------------------------------------

typedef struct {
    TokenType type;
    // For TK_IDENTIFIER, TK_NUMBER: a view into the original source buffer.
    // For TK_STRING: arena-allocated (escapes already resolved, null-terminated).
    // For keywords/operators: empty (value is implicit from type).
    SV value;
} Token;

// ---------------------------------------------------------------------------
// Token list — named typedef so all uses share the same concrete type
// ---------------------------------------------------------------------------

typedef DA(Token) TokenList;

// ---------------------------------------------------------------------------
// Lex result
// ---------------------------------------------------------------------------

typedef struct {
    TokenList   tokens;
    const char *error; // NULL on success
} LexResult;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

// Lex `source` into a token list.
// String literal values are arena-allocated (escapes resolved).
// Identifiers and numbers are views into `source` (zero-copy).
// On error, result.error is non-NULL and tokens may be partial.
// Caller must da_free(&result.tokens) when done.
LexResult lex(SV source, Arena *arena);
