#pragma once

#include "lexer.h" // for Loc
#include "da.h"
#include "arena.h"

// Forward declare Type so ast.h doesn't depend on types.h
// (types.h will include ast.h instead)
struct Type;

// ---------------------------------------------------------------------------
// NodeKind — one entry per AST node type
// ---------------------------------------------------------------------------

typedef enum {
    // Statements
    NK_PROGRAM,
    NK_VAR_DECL,
    NK_FUNCTION_DECL,
    NK_EXTERN_FUNCTION_DECL,
    NK_BLOCK,
    NK_RETURN,
    NK_IF,

    // Loops
    NK_WHILE,
    NK_BREAK,
    NK_CONTINUE,

    // Expressions
    NK_IDENTIFIER,
    NK_NUMERIC_LITERAL,
    NK_STRING_LITERAL,
    NK_BOOLEAN_LITERAL,
    NK_BINARY_EXPR,
    NK_ASSIGNMENT_EXPR,
    NK_CALL_EXPR,

    NK_COUNT, // must remain last — used in static_assert exhaustiveness checks
} NodeKind;

const char *node_kind_name(NodeKind kind);

// ---------------------------------------------------------------------------
// Every node starts with this header (the "base").
// Casting between Node* and any concrete node* is safe because C guarantees
// the first member is at offset 0.
//
// `type` is filled in by the typechecker for expression nodes; NULL until then.
// `loc`  is set by the parser from the token that started this node.
// ---------------------------------------------------------------------------

#define NODE_HEADER \
    NodeKind      kind; \
    struct Type  *type; \
    Loc           loc

typedef struct Node {
    NODE_HEADER;
} Node;

// ---------------------------------------------------------------------------
// Helper: typed dynamic array typedefs (avoid anonymous-struct issues)
// ---------------------------------------------------------------------------

typedef DA(Node *) NodeList;
typedef DA(struct Param) ParamList;

// ---------------------------------------------------------------------------
// Param — function parameter (not a node itself, embedded in decls)
// ---------------------------------------------------------------------------

typedef struct Param {
    SV   name;
    SV   type_name; // the raw string e.g. "i64", resolved to Type* by typechecker
    Loc  loc;
} Param;

// ---------------------------------------------------------------------------
// Concrete node types
// ---------------------------------------------------------------------------

// program { body: Node*[] }
typedef struct {
    NODE_HEADER;
    NodeList body;
} AstProgram;

// let [mut] <name> [: <type>] = <init>;
typedef struct {
    NODE_HEADER;
    SV    name;
    SV    type_name; // optional, empty if absent
    bool  mutable_;
    Node *init;
} AstVarDecl;

// fn <name>(<params>) -> <type> <body>
typedef struct {
    NODE_HEADER;
    SV        name;
    SV        return_type_name;
    ParamList params;
    Node     *body; // always NK_BLOCK
} AstFunctionDecl;

// extern fn <name>(<params>) -> <type>;
typedef struct {
    NODE_HEADER;
    SV        name;
    SV        return_type_name;
    ParamList params;
    bool      is_variadic;
} AstExternFunctionDecl;

// { <stmts> }
typedef struct {
    NODE_HEADER;
    NodeList body;
} AstBlock;

// return <expr>;
typedef struct {
    NODE_HEADER;
    Node *expr;
} AstReturn;

// if <cond> <then> [else <else_>]
typedef struct {
    NODE_HEADER;
    Node *cond;
    Node *then;
    Node *else_; // NULL if no else branch
} AstIf;

// while <cond> <body>
typedef struct {
    NODE_HEADER;
    Node *cond;
    Node *body; // always NK_BLOCK
} AstWhile;

// break; and continue; — no extra fields beyond the header

// <name>
typedef struct {
    NODE_HEADER;
    SV name;
} AstIdentifier;

// 42
typedef struct {
    NODE_HEADER;
    SV      raw;   // original source text
    int64_t value;
} AstNumericLiteral;

// "hello"
typedef struct {
    NODE_HEADER;
    SV value; // escape-resolved, arena-allocated
} AstStringLiteral;

// true / false
typedef struct {
    NODE_HEADER;
    bool value;
} AstBooleanLiteral;

// <left> <op> <right>
typedef struct {
    NODE_HEADER;
    Node *left;
    Node *right;
    SV    op;
} AstBinaryExpr;

// <left> <op>= <right>  (=, +=, -=, ...)
typedef struct {
    NODE_HEADER;
    Node *left;
    Node *right;
    SV    op;
} AstAssignmentExpr;

// <callee>(<args>)
typedef struct {
    NODE_HEADER;
    Node    *callee;
    NodeList args;
} AstCallExpr;

// ---------------------------------------------------------------------------
// Allocation helpers — allocate a zeroed node from the arena and set its kind
// ---------------------------------------------------------------------------

#define ast_alloc(arena, T, kind_, loc_) \
    ((T *)ast_alloc_node((arena), sizeof(T), (kind_), (loc_)))

static inline Node *ast_alloc_node(Arena *a, size_t size, NodeKind kind, Loc loc) {
    Node *n = (Node *)arena_alloc(a, size, alignof(Node));
    n->kind = kind;
    n->type = NULL;
    n->loc  = loc;
    return n;
}

// ---------------------------------------------------------------------------
// Debug printer
// ---------------------------------------------------------------------------

// Write a human-readable representation of the AST rooted at `node` to `buf`.
// Returns the number of characters written (excluding null terminator).
// `indent` is the current indentation level (start with 0).
int node_to_string(const Node *node, char *buf, int buf_size, int indent);
