#include "typechecker.h"
#include <cstdio>
#include <cstring>

// ---------------------------------------------------------------------------
// Context helpers
// ---------------------------------------------------------------------------

void context_init(Context *ctx, Arena *arena) {
    ctx->scopes.data      = NULL; ctx->scopes.len      = 0; ctx->scopes.cap      = 0;
    ctx->return_types.data = NULL; ctx->return_types.len = 0; ctx->return_types.cap = 0;
    ctx->loop_depth = 0;
    ctx->arena    = arena;
    ctx->errbuf[0] = '\0';

    // Push the global scope
    Scope global;
    global.symbols.data = NULL; global.symbols.len = 0; global.symbols.cap = 0;
    da_push(&ctx->scopes, global);
}

static void push_scope(Context *ctx) {
    Scope s;
    s.symbols.data = NULL; s.symbols.len = 0; s.symbols.cap = 0;
    da_push(&ctx->scopes, s);
}

static void pop_scope(Context *ctx) {
    if (ctx->scopes.len == 0) return;
    // Free the symbol list of the popped scope (not the types, those are arena-owned)
    da_free(&ctx->scopes.data[ctx->scopes.len - 1].symbols);
    ctx->scopes.len--;
}

// Declare a symbol in the innermost scope. Returns false if already declared.
static bool scope_declare(Context *ctx, SV name, Type *type) {
    Scope *top = &ctx->scopes.data[ctx->scopes.len - 1];
    for (size_t i = 0; i < top->symbols.len; i++) {
        if (sv_eq(top->symbols.data[i].name, name)) {
            snprintf(ctx->errbuf, sizeof(ctx->errbuf),
                     "'" SV_FMT "' is already declared in this scope",
                     SV_ARG(name));
            return false;
        }
    }
    Symbol sym;
    sym.name = name;
    sym.type = type;
    da_push(&top->symbols, sym);
    return true;
}

// Look up a name, searching from innermost to outermost scope.
static Type *scope_lookup(Context *ctx, SV name) {
    for (int i = (int)ctx->scopes.len - 1; i >= 0; i--) {
        Scope *s = &ctx->scopes.data[i];
        for (size_t j = 0; j < s->symbols.len; j++) {
            if (sv_eq(s->symbols.data[j].name, name))
                return s->symbols.data[j].type;
        }
    }
    return NULL;
}

// Push/pop expected return type (used when entering/leaving a function)
static void push_return_type(Context *ctx, Type *type) {
    da_push(&ctx->return_types, type);
}

static void pop_return_type(Context *ctx) {
    if (ctx->return_types.len > 0) ctx->return_types.len--;
}

static Type *current_return_type(Context *ctx) {
    if (ctx->return_types.len == 0) return NULL;
    return ctx->return_types.data[ctx->return_types.len - 1];
}

// ---------------------------------------------------------------------------
// Forward declaration
// ---------------------------------------------------------------------------

static bool tc_node(Node *node, Context *ctx);

// ---------------------------------------------------------------------------
// Error helper
// ---------------------------------------------------------------------------

#define TC_ERR(ctx, loc, fmt, ...) \
    do { \
        snprintf((ctx)->errbuf, sizeof((ctx)->errbuf), \
                 "[%u:%u] " fmt, (loc).line, (loc).col, ##__VA_ARGS__); \
        return false; \
    } while (0)

// ---------------------------------------------------------------------------
// Individual node typecheckers
// ---------------------------------------------------------------------------

static bool tc_program(AstProgram *node, Context *ctx) {
    static_assert(NK_COUNT == 17, "tc_program: NodeKind changed");

    // First pass: hoist function and extern function declarations
    for (size_t i = 0; i < node->body.len; i++) {
        Node *stmt = node->body.data[i];

        if (stmt->kind == NK_FUNCTION_DECL) {
            AstFunctionDecl *fn = (AstFunctionDecl *)stmt;
            Type *ret = type_from_sv(fn->return_type_name);
            if (!ret) TC_ERR(ctx, fn->loc,
                             "unknown return type '" SV_FMT "'", SV_ARG(fn->return_type_name));

            TypeList params;
            params.data = NULL; params.len = 0; params.cap = 0;
            for (size_t p = 0; p < fn->params.len; p++) {
                Type *pt = type_from_sv(fn->params.data[p].type_name);
                if (!pt) TC_ERR(ctx, fn->params.data[p].loc,
                                "unknown parameter type '" SV_FMT "'",
                                SV_ARG(fn->params.data[p].type_name));
                da_push(&params, pt);
            }
            Type *fn_type = make_function_type(ctx->arena, params, ret);
            if (!scope_declare(ctx, fn->name, fn_type)) return false;
        }

        if (stmt->kind == NK_EXTERN_FUNCTION_DECL) {
            AstExternFunctionDecl *fn = (AstExternFunctionDecl *)stmt;
            Type *ret = type_from_sv(fn->return_type_name);
            if (!ret) TC_ERR(ctx, fn->loc,
                             "unknown return type '" SV_FMT "'", SV_ARG(fn->return_type_name));

            TypeList params;
            params.data = NULL; params.len = 0; params.cap = 0;
            for (size_t p = 0; p < fn->params.len; p++) {
                Type *pt = type_from_sv(fn->params.data[p].type_name);
                if (!pt) TC_ERR(ctx, fn->params.data[p].loc,
                                "unknown parameter type '" SV_FMT "'",
                                SV_ARG(fn->params.data[p].type_name));
                da_push(&params, pt);
            }
            Type *fn_type = make_ext_function_type(ctx->arena, params, ret, fn->is_variadic);
            if (!scope_declare(ctx, fn->name, fn_type)) return false;
        }
    }

    // Second pass: typecheck all statements
    for (size_t i = 0; i < node->body.len; i++) {
        if (!tc_node(node->body.data[i], ctx)) return false;
    }
    return true;
}

static bool tc_var_decl(AstVarDecl *node, Context *ctx) {
    // Check the initialiser first
    if (!tc_node(node->init, ctx)) return false;
    Type *init_type = node->init->type;

    Type *declared_type;
    if (node->type_name.len > 0) {
        declared_type = type_from_sv(node->type_name);
        if (!declared_type)
            TC_ERR(ctx, node->loc,
                   "unknown type '" SV_FMT "'", SV_ARG(node->type_name));
        if (!is_assignable(declared_type, init_type))
            TC_ERR(ctx, node->loc,
                   "cannot assign '%s' to variable of type '%s'",
                   type_to_string(init_type, ctx->arena),
                   type_to_string(declared_type, ctx->arena));
    } else {
        declared_type = init_type;
    }

    node->type = declared_type;
    if (!scope_declare(ctx, node->name, declared_type)) return false;
    return true;
}

static bool tc_function_decl(AstFunctionDecl *node, Context *ctx) {
    Type *ret = type_from_sv(node->return_type_name);
    if (!ret) TC_ERR(ctx, node->loc,
                     "unknown return type '" SV_FMT "'", SV_ARG(node->return_type_name));

    push_scope(ctx);
    push_return_type(ctx, ret);

    // Declare parameters in the function's scope
    for (size_t i = 0; i < node->params.len; i++) {
        Param *p = &node->params.data[i];
        Type *pt = type_from_sv(p->type_name);
        if (!pt) TC_ERR(ctx, p->loc,
                        "unknown parameter type '" SV_FMT "'", SV_ARG(p->type_name));
        if (!scope_declare(ctx, p->name, pt)) return false;
    }

    bool ok = tc_node(node->body, ctx);
    pop_return_type(ctx);
    pop_scope(ctx);
    return ok;
}

static bool tc_extern_function_decl(AstExternFunctionDecl *node, Context *ctx) {
    // Already hoisted in the first pass of tc_program.
    // Validate types are known.
    if (!type_from_sv(node->return_type_name))
        TC_ERR(ctx, node->loc,
               "unknown return type '" SV_FMT "'", SV_ARG(node->return_type_name));
    for (size_t i = 0; i < node->params.len; i++) {
        if (!type_from_sv(node->params.data[i].type_name))
            TC_ERR(ctx, node->params.data[i].loc,
                   "unknown parameter type '" SV_FMT "'",
                   SV_ARG(node->params.data[i].type_name));
    }
    return true;
}

static bool tc_block(AstBlock *node, Context *ctx) {
    push_scope(ctx);
    for (size_t i = 0; i < node->body.len; i++) {
        if (!tc_node(node->body.data[i], ctx)) {
            pop_scope(ctx);
            return false;
        }
    }
    pop_scope(ctx);
    return true;
}

static bool tc_return(AstReturn *node, Context *ctx) {
    if (!tc_node(node->expr, ctx)) return false;

    Type *expected = current_return_type(ctx);
    if (!expected) TC_ERR(ctx, node->loc, "return outside of a function");

    if (!is_assignable(expected, node->expr->type))
        TC_ERR(ctx, node->loc,
               "cannot return '%s' from function expecting '%s'",
               type_to_string(node->expr->type, ctx->arena),
               type_to_string(expected, ctx->arena));

    node->type = node->expr->type;
    return true;
}

static bool tc_if(AstIf *node, Context *ctx) {
    if (!tc_node(node->cond, ctx)) return false;

    if (node->cond->type != TY_BOOL_SINGLETON)
        TC_ERR(ctx, node->cond->loc,
               "if condition must be boolean, got '%s'",
               type_to_string(node->cond->type, ctx->arena));

    if (!tc_node(node->then, ctx)) return false;
    if (node->else_ && !tc_node(node->else_, ctx)) return false;
    return true;
}

static bool tc_while(AstWhile *node, Context *ctx) {
    if (!tc_node(node->cond, ctx)) return false;

    if (node->cond->type != TY_BOOL_SINGLETON)
        TC_ERR(ctx, node->cond->loc,
               "while condition must be boolean, got '%s'",
               type_to_string(node->cond->type, ctx->arena));

    ctx->loop_depth++;
    bool ok = tc_node(node->body, ctx);
    ctx->loop_depth--;
    return ok;
}

static bool tc_break(Node *node, Context *ctx) {
    if (ctx->loop_depth == 0)
        TC_ERR(ctx, node->loc, "break outside of a loop");
    return true;
}

static bool tc_continue(Node *node, Context *ctx) {
    if (ctx->loop_depth == 0)
        TC_ERR(ctx, node->loc, "continue outside of a loop");
    return true;
}

static bool tc_identifier(AstIdentifier *node, Context *ctx) {
    Type *type = scope_lookup(ctx, node->name);
    if (!type)
        TC_ERR(ctx, node->loc, "undefined symbol '" SV_FMT "'", SV_ARG(node->name));
    node->type = type;
    return true;
}

static bool tc_numeric_literal(AstNumericLiteral *node, Context * /*ctx*/) {
    node->type = TY_I64;
    return true;
}

static bool tc_string_literal(AstStringLiteral *node, Context * /*ctx*/) {
    node->type = TY_STRING_SINGLETON;
    return true;
}

static bool tc_boolean_literal(AstBooleanLiteral *node, Context * /*ctx*/) {
    node->type = TY_BOOL_SINGLETON;
    return true;
}

static bool tc_binary_expr(AstBinaryExpr *node, Context *ctx) {
    if (!tc_node(node->left, ctx))  return false;
    if (!tc_node(node->right, ctx)) return false;

    Type *lt = node->left->type;
    Type *rt = node->right->type;

    // Arithmetic operators
    if (sv_eq_cstr(node->op, "+") || sv_eq_cstr(node->op, "-") ||
        sv_eq_cstr(node->op, "*") || sv_eq_cstr(node->op, "/") ||
        sv_eq_cstr(node->op, "%")) {
        Type *result = coerce_types(lt, rt);
        if (!result)
            TC_ERR(ctx, node->loc,
                   "cannot apply '%s' to '%s' and '%s'",
                   node->op.data,
                   type_to_string(lt, ctx->arena),
                   type_to_string(rt, ctx->arena));
        node->type = result;
        return true;
    }

    // Comparison operators → boolean result
    if (sv_eq_cstr(node->op, "<")  || sv_eq_cstr(node->op, ">") ||
        sv_eq_cstr(node->op, "<=") || sv_eq_cstr(node->op, ">=")) {
        if ((lt->kind != TY_INT && lt->kind != TY_FLOAT) ||
            (rt->kind != TY_INT && rt->kind != TY_FLOAT))
            TC_ERR(ctx, node->loc,
                   "comparison requires numeric operands, got '%s' and '%s'",
                   type_to_string(lt, ctx->arena),
                   type_to_string(rt, ctx->arena));
        node->type = TY_BOOL_SINGLETON;
        return true;
    }

    // Equality operators → boolean result (any types allowed)
    if (sv_eq_cstr(node->op, "==") || sv_eq_cstr(node->op, "!=")) {
        node->type = TY_BOOL_SINGLETON;
        return true;
    }

    TC_ERR(ctx, node->loc, "unknown binary operator '" SV_FMT "'", SV_ARG(node->op));
}

static bool tc_assignment_expr(AstAssignmentExpr *node, Context *ctx) {
    if (!tc_node(node->left, ctx))  return false;
    if (!tc_node(node->right, ctx)) return false;

    Type *lt = node->left->type;
    Type *rt = node->right->type;

    if (!is_assignable(lt, rt))
        TC_ERR(ctx, node->loc,
               "cannot assign '%s' to '%s'",
               type_to_string(rt, ctx->arena),
               type_to_string(lt, ctx->arena));

    node->type = lt;
    return true;
}

static bool tc_call_expr(AstCallExpr *node, Context *ctx) {
    if (!tc_node(node->callee, ctx)) return false;

    Type *callee_type = node->callee->type;

    if (callee_type->kind == TY_FUNCTION) {
        size_t expected = callee_type->function.params.len;
        size_t got      = node->args.len;
        if (expected != got)
            TC_ERR(ctx, node->loc,
                   "expected %zu argument(s) but got %zu", expected, got);

        for (size_t i = 0; i < node->args.len; i++) {
            if (!tc_node(node->args.data[i], ctx)) return false;
            Type *param = callee_type->function.params.data[i];
            Type *arg   = node->args.data[i]->type;
            if (!is_assignable(param, arg))
                TC_ERR(ctx, node->args.data[i]->loc,
                       "argument %zu: cannot pass '%s' as '%s'",
                       i + 1,
                       type_to_string(arg, ctx->arena),
                       type_to_string(param, ctx->arena));
        }

        node->type = callee_type->function.ret;
        return true;
    }

    if (callee_type->kind == TY_EXT_FUNCTION) {
        size_t min_args = callee_type->ext_function.params.len;
        bool   variadic = callee_type->ext_function.variadic;

        if (variadic ? node->args.len < min_args : node->args.len != min_args)
            TC_ERR(ctx, node->loc,
                   "expected %s%zu argument(s) but got %zu",
                   variadic ? "at least " : "",
                   min_args, node->args.len);

        for (size_t i = 0; i < node->args.len; i++) {
            if (!tc_node(node->args.data[i], ctx)) return false;

            // Only validate fixed params; skip type check for variadic tail
            if (i >= min_args) continue;

            Type *param = callee_type->ext_function.params.data[i];
            Type *arg   = node->args.data[i]->type;

            // ptr params accept anything (e.g. printf format string)
            if (param->kind == TY_PTR) continue;

            if (!is_assignable(param, arg))
                TC_ERR(ctx, node->args.data[i]->loc,
                       "argument %zu: cannot pass '%s' as '%s'",
                       i + 1,
                       type_to_string(arg, ctx->arena),
                       type_to_string(param, ctx->arena));
        }

        node->type = callee_type->ext_function.ret;
        return true;
    }

    TC_ERR(ctx, node->loc,
           "expression is not callable (type: '%s')",
           type_to_string(callee_type, ctx->arena));
}

// ---------------------------------------------------------------------------
// Dispatch
// ---------------------------------------------------------------------------

static bool tc_node(Node *node, Context *ctx) {
    static_assert(NK_COUNT == 17, "tc_node: NodeKind changed, update this switch");

    if (!node) {
        snprintf(ctx->errbuf, sizeof(ctx->errbuf), "internal: NULL node passed to tc_node");
        return false;
    }

    switch (node->kind) {
        case NK_PROGRAM:              return tc_program((AstProgram *)node, ctx);
        case NK_VAR_DECL:             return tc_var_decl((AstVarDecl *)node, ctx);
        case NK_FUNCTION_DECL:        return tc_function_decl((AstFunctionDecl *)node, ctx);
        case NK_EXTERN_FUNCTION_DECL: return tc_extern_function_decl((AstExternFunctionDecl *)node, ctx);
        case NK_BLOCK:                return tc_block((AstBlock *)node, ctx);
        case NK_RETURN:               return tc_return((AstReturn *)node, ctx);
        case NK_IF:                   return tc_if((AstIf *)node, ctx);
        case NK_WHILE:                return tc_while((AstWhile *)node, ctx);
        case NK_BREAK:                return tc_break(node, ctx);
        case NK_CONTINUE:             return tc_continue(node, ctx);
        case NK_IDENTIFIER:           return tc_identifier((AstIdentifier *)node, ctx);
        case NK_NUMERIC_LITERAL:      return tc_numeric_literal((AstNumericLiteral *)node, ctx);
        case NK_STRING_LITERAL:       return tc_string_literal((AstStringLiteral *)node, ctx);
        case NK_BOOLEAN_LITERAL:      return tc_boolean_literal((AstBooleanLiteral *)node, ctx);
        case NK_BINARY_EXPR:          return tc_binary_expr((AstBinaryExpr *)node, ctx);
        case NK_ASSIGNMENT_EXPR:      return tc_assignment_expr((AstAssignmentExpr *)node, ctx);
        case NK_CALL_EXPR:            return tc_call_expr((AstCallExpr *)node, ctx);
        case NK_COUNT:                break;
    }

    snprintf(ctx->errbuf, sizeof(ctx->errbuf),
             "internal: unhandled node kind %d", (int)node->kind);
    return false;
}

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

bool typecheck(Node *node, Context *ctx) {
    return tc_node(node, ctx);
}
