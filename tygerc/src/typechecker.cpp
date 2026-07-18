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
static bool scope_declare(Context *ctx, SV name, Type *type, bool mutable_) {
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
    sym.name     = name;
    sym.type     = type;
    sym.mutable_ = mutable_;
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

// Check if a symbol is declared as mutable.
static bool scope_is_mutable(Context *ctx, SV name) {
    for (int i = (int)ctx->scopes.len - 1; i >= 0; i--) {
        Scope *s = &ctx->scopes.data[i];
        for (size_t j = 0; j < s->symbols.len; j++) {
            if (sv_eq(s->symbols.data[j].name, name))
                return s->symbols.data[j].mutable_;
        }
    }
    return false;
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

// Resolve a type name: first try primitives, then scope (for struct types).
static Type *resolve_type(Context *ctx, SV name) {
    Type *t = type_from_sv(name);
    if (t) return t;
    // Check scope for struct types
    t = scope_lookup(ctx, name);
    if (t && t->kind == TY_STRUCT) return t;
    return NULL;
}

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
    static_assert(NK_COUNT == 20, "tc_program: NodeKind changed");

    // First pass: hoist function, extern function, and struct declarations
    for (size_t i = 0; i < node->body.len; i++) {
        Node *stmt = node->body.data[i];

        if (stmt->kind == NK_STRUCT_DECL) {
            AstStructDecl *sd = (AstStructDecl *)stmt;
            // Build the struct type
            StructTypeFieldList fields;
            fields.data = NULL; fields.len = 0; fields.cap = 0;
            for (size_t f = 0; f < sd->fields.len; f++) {
                StructField *sf = &sd->fields.data[f];
                Type *ft = resolve_type(ctx, sf->type_name);
                if (!ft) TC_ERR(ctx, sf->loc,
                                "unknown field type '" SV_FMT "'", SV_ARG(sf->type_name));
                StructTypeField stf;
                stf.name = sf->name;
                stf.type = ft;
                da_push(&fields, stf);
            }
            Type *struct_type = make_struct_type(ctx->arena, sd->name, fields);
            stmt->type = struct_type;
            if (!scope_declare(ctx, sd->name, struct_type, false)) return false;
        }

        if (stmt->kind == NK_FUNCTION_DECL) {
            AstFunctionDecl *fn = (AstFunctionDecl *)stmt;
            Type *ret = resolve_type(ctx, fn->return_type_name);
            if (!ret) TC_ERR(ctx, fn->loc,
                             "unknown return type '" SV_FMT "'", SV_ARG(fn->return_type_name));

            TypeList params;
            params.data = NULL; params.len = 0; params.cap = 0;
            BoolList mut_params;
            mut_params.data = NULL; mut_params.len = 0; mut_params.cap = 0;
            for (size_t p = 0; p < fn->params.len; p++) {
                Type *pt = resolve_type(ctx, fn->params.data[p].type_name);
                if (!pt) TC_ERR(ctx, fn->params.data[p].loc,
                                "unknown parameter type '" SV_FMT "'",
                                SV_ARG(fn->params.data[p].type_name));
                da_push(&params, pt);
                bool is_mut = fn->params.data[p].mutable_;
                da_push(&mut_params, is_mut);
            }
            Type *fn_type = make_function_type(ctx->arena, params, mut_params, ret);
            if (!scope_declare(ctx, fn->name, fn_type, false)) return false;
            stmt->type = fn_type; // annotate the node for codegen
        }

        if (stmt->kind == NK_EXTERN_FUNCTION_DECL) {
            AstExternFunctionDecl *fn = (AstExternFunctionDecl *)stmt;
            Type *ret = resolve_type(ctx, fn->return_type_name);
            if (!ret) TC_ERR(ctx, fn->loc,
                             "unknown return type '" SV_FMT "'", SV_ARG(fn->return_type_name));

            TypeList params;
            params.data = NULL; params.len = 0; params.cap = 0;
            for (size_t p = 0; p < fn->params.len; p++) {
                Type *pt = resolve_type(ctx, fn->params.data[p].type_name);
                if (!pt) TC_ERR(ctx, fn->params.data[p].loc,
                                "unknown parameter type '" SV_FMT "'",
                                SV_ARG(fn->params.data[p].type_name));
                da_push(&params, pt);
            }
            Type *fn_type = make_ext_function_type(ctx->arena, params, ret, fn->is_variadic);
            if (!scope_declare(ctx, fn->name, fn_type, false)) return false;
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
        declared_type = resolve_type(ctx, node->type_name);
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
    if (!scope_declare(ctx, node->name, declared_type, node->mutable_)) return false;
    return true;
}

static bool tc_function_decl(AstFunctionDecl *node, Context *ctx) {
    Type *ret = resolve_type(ctx, node->return_type_name);
    if (!ret) TC_ERR(ctx, node->loc,
                     "unknown return type '" SV_FMT "'", SV_ARG(node->return_type_name));

    push_scope(ctx);
    push_return_type(ctx, ret);

    // Declare parameters in the function's scope
    for (size_t i = 0; i < node->params.len; i++) {
        Param *p = &node->params.data[i];
        Type *pt = resolve_type(ctx, p->type_name);
        if (!pt) TC_ERR(ctx, p->loc,
                        "unknown parameter type '" SV_FMT "'", SV_ARG(p->type_name));
        if (!scope_declare(ctx, p->name, pt, p->mutable_)) return false;
    }

    bool ok = tc_node(node->body, ctx);
    pop_return_type(ctx);
    pop_scope(ctx);
    return ok;
}

static bool tc_extern_function_decl(AstExternFunctionDecl *node, Context *ctx) {
    // Already hoisted in the first pass of tc_program.
    // Validate types are known.
    if (!resolve_type(ctx, node->return_type_name))
        TC_ERR(ctx, node->loc,
               "unknown return type '" SV_FMT "'", SV_ARG(node->return_type_name));
    for (size_t i = 0; i < node->params.len; i++) {
        if (!resolve_type(ctx, node->params.data[i].type_name))
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
    // If the raw value contains a dot, it's a float literal → default to f64
    bool is_float = false;
    for (size_t i = 0; i < node->raw.len; i++) {
        if (node->raw.data[i] == '.') { is_float = true; break; }
    }
    node->type = is_float ? TY_F64 : TY_I64;
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

            // mut parameter check: can only pass a let mut variable to a mut param
            bool param_is_mut = i < callee_type->function.mut_params.len &&
                                 callee_type->function.mut_params.data[i];
            if (param_is_mut) {
                Node *arg_node = node->args.data[i];
                if (arg_node->kind != NK_IDENTIFIER)
                    TC_ERR(ctx, arg_node->loc,
                           "argument %zu: cannot pass a non-variable expression to a mut parameter",
                           i + 1);
                AstIdentifier *id = (AstIdentifier *)arg_node;
                if (!scope_is_mutable(ctx, id->name))
                    TC_ERR(ctx, arg_node->loc,
                           "argument %zu: cannot pass immutable variable '" SV_FMT "' to mut parameter",
                           i + 1, SV_ARG(id->name));
            }
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

static bool tc_struct_decl(AstStructDecl *node, Context *ctx) {
    // Already hoisted in the first pass of tc_program; type is on node->type.
    // Second pass: validate that all field types are known (already done in hoist).
    // Nothing more to do here — just succeed.
    return true;
}

static bool tc_struct_literal(AstStructLiteral *node, Context *ctx) {
    // Look up the struct type
    Type *struct_type = scope_lookup(ctx, node->struct_name);
    if (!struct_type)
        TC_ERR(ctx, node->loc, "unknown struct '" SV_FMT "'", SV_ARG(node->struct_name));
    if (struct_type->kind != TY_STRUCT)
        TC_ERR(ctx, node->loc, "'" SV_FMT "' is not a struct", SV_ARG(node->struct_name));

    size_t num_fields = struct_type->struct_.fields.len;

    // Check we got exactly the right number of fields
    if (node->fields.len != num_fields)
        TC_ERR(ctx, node->loc,
               "struct '" SV_FMT "' has %zu field(s) but %zu were provided",
               SV_ARG(node->struct_name), num_fields, node->fields.len);

    // Check each provided field — name must exist, type must match, no duplicates
    bool seen[64] = {false}; // support up to 64 fields without heap allocation
    for (size_t i = 0; i < node->fields.len; i++) {
        StructInit *init = &node->fields.data[i];
        int idx = struct_field_index(struct_type, init->name);
        if (idx < 0)
            TC_ERR(ctx, init->loc,
                   "struct '" SV_FMT "' has no field '" SV_FMT "'",
                   SV_ARG(node->struct_name), SV_ARG(init->name));
        if (seen[idx])
            TC_ERR(ctx, init->loc,
                   "field '" SV_FMT "' provided more than once",
                   SV_ARG(init->name));
        seen[idx] = true;

        if (!tc_node(init->value, ctx)) return false;

        Type *expected = struct_field_type(struct_type, idx);
        if (!is_assignable(expected, init->value->type))
            TC_ERR(ctx, init->loc,
                   "field '" SV_FMT "': cannot assign '%s' to '%s'",
                   SV_ARG(init->name),
                   type_to_string(init->value->type, ctx->arena),
                   type_to_string(expected, ctx->arena));
    }

    node->type = struct_type;
    return true;
}

static bool tc_field_access(AstFieldAccess *node, Context *ctx) {
    if (!tc_node(node->object, ctx)) return false;

    Type *obj_type = node->object->type;
    if (!obj_type || obj_type->kind != TY_STRUCT)
        TC_ERR(ctx, node->loc,
               "cannot access field '" SV_FMT "' on non-struct type '%s'",
               SV_ARG(node->field),
               type_to_string(obj_type, ctx->arena));

    int idx = struct_field_index(obj_type, node->field);
    if (idx < 0)
        TC_ERR(ctx, node->loc,
               "struct '" SV_FMT "' has no field '" SV_FMT "'",
               SV_ARG(obj_type->struct_.name), SV_ARG(node->field));

    node->type = struct_field_type(obj_type, idx);
    return true;
}

// ---------------------------------------------------------------------------
// Dispatch
// ---------------------------------------------------------------------------

static bool tc_node(Node *node, Context *ctx) {
    static_assert(NK_COUNT == 20, "tc_node: NodeKind changed, update this switch");

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
        case NK_STRUCT_DECL:          return tc_struct_decl((AstStructDecl *)node, ctx);
        case NK_STRUCT_LITERAL:       return tc_struct_literal((AstStructLiteral *)node, ctx);
        case NK_FIELD_ACCESS:         return tc_field_access((AstFieldAccess *)node, ctx);        case NK_IDENTIFIER:           return tc_identifier((AstIdentifier *)node, ctx);
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
