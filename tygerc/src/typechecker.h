#pragma once

#include "ast.h"
#include "types.h"
#include "arena.h"

// ---------------------------------------------------------------------------
// Symbol — a name/type binding in a scope
// ---------------------------------------------------------------------------

typedef struct {
    SV    name;
    Type *type;
} Symbol;

typedef DA(Symbol) SymbolList;

// ---------------------------------------------------------------------------
// Scope — a flat list of symbols
// ---------------------------------------------------------------------------

typedef struct {
    SymbolList symbols;
} Scope;

typedef DA(Scope) ScopeStack;
typedef DA(Type *) ReturnTypeStack;

// ---------------------------------------------------------------------------
// Context — typechecker state
// ---------------------------------------------------------------------------

typedef struct {
    ScopeStack      scopes;       // stack of scopes; scopes.data[0] is global
    ReturnTypeStack return_types; // stack of expected return types (one per function)
    Arena          *arena;
    char            errbuf[512];
} Context;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

// Initialise a Context. Must be called before typecheck().
void context_init(Context *ctx, Arena *arena);

// Typecheck an AST node in-place (annotates node->type on expressions).
// Returns true on success, false on error (ctx->errbuf contains the message).
bool typecheck(Node *node, Context *ctx);
