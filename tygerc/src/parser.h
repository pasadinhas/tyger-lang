#pragma once

#include "ast.h"
#include "lexer.h"
#include "arena.h"

// ---------------------------------------------------------------------------
// Parse result
// ---------------------------------------------------------------------------

typedef struct {
    AstProgram *program; // NULL on error
    const char *error;   // NULL on success; arena-allocated string on error
} ParseResult;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

// Parse a token list into an AST.
// All nodes are arena-allocated. The TokenList must remain valid for the
// lifetime of the returned AST (identifiers/numbers are SV views into tokens).
// On error, result.error is non-NULL and result.program is NULL.
ParseResult parse(TokenList *tokens, Arena *arena);
