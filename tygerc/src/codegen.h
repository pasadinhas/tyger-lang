#pragma once

#include "ast.h"
#include "types.h"
#include "arena.h"

// ---------------------------------------------------------------------------
// Codegen result
// ---------------------------------------------------------------------------

typedef struct {
    const char *error; // NULL on success, arena-allocated string on error
} CodegenResult;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

// Generate LLVM IR from a fully type-annotated AST.
// Writes the IR to `out_path` as a .ll file.
// Returns a result with error == NULL on success.
CodegenResult codegen(AstProgram *program, const char *out_path, Arena *arena);
