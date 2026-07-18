#include "test_runner.h"
#include "lexer.h"
#include "parser.h"
#include "arena.h"
#include <cstring>

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static Arena g_arena;

static AstProgram *must_parse(const char *src) {
    LexResult lex_result = lex(sv_from_cstr(src), &g_arena);
    if (lex_result.error) {
        fprintf(stderr, "  lex error: %s\n", lex_result.error);
        return NULL;
    }
    ParseResult parse_result = parse(&lex_result.tokens, &g_arena);
    // Note: we intentionally don't da_free the tokens here — the AST SV
    // values point into them. The arena owns everything.
    if (parse_result.error) {
        fprintf(stderr, "  parse error: %s\n", parse_result.error);
        return NULL;
    }
    return parse_result.program;
}

static const char *must_fail(const char *src) {
    LexResult lex_result = lex(sv_from_cstr(src), &g_arena);
    if (lex_result.error) return lex_result.error;
    ParseResult parse_result = parse(&lex_result.tokens, &g_arena);
    return parse_result.error; // expected to be non-NULL
}

// ---------------------------------------------------------------------------
// Program structure
// ---------------------------------------------------------------------------

static void test_empty_program() {
    AstProgram *p = must_parse("");
    ASSERT_NOT_NULL(p);
    ASSERT_EQ(NK_PROGRAM, p->kind);
    ASSERT_EQ(0u, p->body.len);
}

// ---------------------------------------------------------------------------
// Variable declarations
// ---------------------------------------------------------------------------

static void test_var_decl_simple() {
    AstProgram *p = must_parse("let x = 42;");
    ASSERT_NOT_NULL(p);
    ASSERT_EQ(1u, p->body.len);

    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    ASSERT_EQ(NK_VAR_DECL, decl->kind);
    ASSERT(sv_eq_cstr(decl->name, "x"));
    ASSERT(!decl->mutable_);
    ASSERT_EQ(0u, decl->type_name.len); // no type hint

    AstNumericLiteral *init = (AstNumericLiteral *)decl->init;
    ASSERT_EQ(NK_NUMERIC_LITERAL, init->kind);
    ASSERT_EQ(42LL, init->value);
}

static void test_var_decl_mutable() {
    AstProgram *p = must_parse("let mut x = 1;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    ASSERT(decl->mutable_);
}

static void test_var_decl_with_type_hint() {
    AstProgram *p = must_parse("let x: i64 = 0;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    ASSERT(sv_eq_cstr(decl->type_name, "i64"));
}

// ---------------------------------------------------------------------------
// Literals
// ---------------------------------------------------------------------------

static void test_string_literal() {
    AstProgram *p = must_parse("let s = \"hello\";");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    AstStringLiteral *lit = (AstStringLiteral *)decl->init;
    ASSERT_EQ(NK_STRING_LITERAL, lit->kind);
    ASSERT(sv_eq_cstr(lit->value, "hello"));
}

static void test_boolean_literal_true() {
    AstProgram *p = must_parse("let b = true;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    AstBooleanLiteral *lit = (AstBooleanLiteral *)decl->init;
    ASSERT_EQ(NK_BOOLEAN_LITERAL, lit->kind);
    ASSERT(lit->value == true);
}

static void test_boolean_literal_false() {
    AstProgram *p = must_parse("let b = false;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    AstBooleanLiteral *lit = (AstBooleanLiteral *)decl->init;
    ASSERT(lit->value == false);
}

// ---------------------------------------------------------------------------
// Binary expressions / precedence
// ---------------------------------------------------------------------------

static void test_binary_add() {
    AstProgram *p = must_parse("let x = 1 + 2;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    AstBinaryExpr *bin = (AstBinaryExpr *)decl->init;
    ASSERT_EQ(NK_BINARY_EXPR, bin->kind);
    ASSERT(sv_eq_cstr(bin->op, "+"));
    ASSERT_EQ(NK_NUMERIC_LITERAL, bin->left->kind);
    ASSERT_EQ(NK_NUMERIC_LITERAL, bin->right->kind);
}

static void test_precedence_mul_over_add() {
    // 1 + 2 * 3  =>  Add(1, Mul(2, 3))
    AstProgram *p = must_parse("let x = 1 + 2 * 3;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    AstBinaryExpr *add = (AstBinaryExpr *)decl->init;
    ASSERT(sv_eq_cstr(add->op, "+"));
    ASSERT_EQ(NK_BINARY_EXPR, add->right->kind);
    AstBinaryExpr *mul = (AstBinaryExpr *)add->right;
    ASSERT(sv_eq_cstr(mul->op, "*"));
}

static void test_precedence_parens() {
    // (1 + 2) * 3  =>  Mul(Add(1,2), 3)
    AstProgram *p = must_parse("let x = (1 + 2) * 3;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    AstBinaryExpr *mul = (AstBinaryExpr *)decl->init;
    ASSERT(sv_eq_cstr(mul->op, "*"));
    ASSERT_EQ(NK_BINARY_EXPR, mul->left->kind);
    AstBinaryExpr *add = (AstBinaryExpr *)mul->left;
    ASSERT(sv_eq_cstr(add->op, "+"));
}

static void test_comparison() {
    AstProgram *p = must_parse("let x = a < b;");
    ASSERT_NOT_NULL(p);
    AstVarDecl *decl = (AstVarDecl *)p->body.data[0];
    AstBinaryExpr *bin = (AstBinaryExpr *)decl->init;
    ASSERT_EQ(NK_BINARY_EXPR, bin->kind);
    ASSERT(sv_eq_cstr(bin->op, "<"));
}

// ---------------------------------------------------------------------------
// Assignment expressions
// ---------------------------------------------------------------------------

static void test_assignment_expr() {
    AstProgram *p = must_parse("x = 5;");
    ASSERT_NOT_NULL(p);
    AstAssignmentExpr *assign = (AstAssignmentExpr *)p->body.data[0];
    ASSERT_EQ(NK_ASSIGNMENT_EXPR, assign->kind);
    ASSERT(sv_eq_cstr(assign->op, "="));
}

static void test_compound_assignment() {
    AstProgram *p = must_parse("x += 1;");
    ASSERT_NOT_NULL(p);
    AstAssignmentExpr *assign = (AstAssignmentExpr *)p->body.data[0];
    ASSERT_EQ(NK_ASSIGNMENT_EXPR, assign->kind);
    ASSERT(sv_eq_cstr(assign->op, "+="));
}

// ---------------------------------------------------------------------------
// Function declarations
// ---------------------------------------------------------------------------

static void test_function_decl() {
    AstProgram *p = must_parse("fn add(a: i64, b: i64) -> i64 { return a; }");
    ASSERT_NOT_NULL(p);
    ASSERT_EQ(1u, p->body.len);

    AstFunctionDecl *fn = (AstFunctionDecl *)p->body.data[0];
    ASSERT_EQ(NK_FUNCTION_DECL, fn->kind);
    ASSERT(sv_eq_cstr(fn->name, "add"));
    ASSERT(sv_eq_cstr(fn->return_type_name, "i64"));
    ASSERT_EQ(2u, fn->params.len);
    ASSERT(sv_eq_cstr(fn->params.data[0].name, "a"));
    ASSERT(sv_eq_cstr(fn->params.data[0].type_name, "i64"));
    ASSERT(sv_eq_cstr(fn->params.data[1].name, "b"));

    AstBlock *body = (AstBlock *)fn->body;
    ASSERT_EQ(NK_BLOCK, body->kind);
    ASSERT_EQ(1u, body->body.len);
    ASSERT_EQ(NK_RETURN, body->body.data[0]->kind);
}

static void test_function_no_params() {
    AstProgram *p = must_parse("fn main() -> i32 { }");
    ASSERT_NOT_NULL(p);
    AstFunctionDecl *fn = (AstFunctionDecl *)p->body.data[0];
    ASSERT_EQ(0u, fn->params.len);
}

// ---------------------------------------------------------------------------
// Extern function declarations
// ---------------------------------------------------------------------------

static void test_extern_function_decl() {
    AstProgram *p = must_parse("extern fn puts(s: ptr) -> i32;");
    ASSERT_NOT_NULL(p);
    AstExternFunctionDecl *fn = (AstExternFunctionDecl *)p->body.data[0];
    ASSERT_EQ(NK_EXTERN_FUNCTION_DECL, fn->kind);
    ASSERT(sv_eq_cstr(fn->name, "puts"));
    ASSERT(sv_eq_cstr(fn->return_type_name, "i32"));
    ASSERT_EQ(1u, fn->params.len);
    ASSERT(!fn->is_variadic);
}

static void test_extern_function_variadic() {
    AstProgram *p = must_parse("extern fn printf(fmt: ptr, ...) -> i32;");
    ASSERT_NOT_NULL(p);
    AstExternFunctionDecl *fn = (AstExternFunctionDecl *)p->body.data[0];
    ASSERT(fn->is_variadic);
    ASSERT_EQ(1u, fn->params.len); // only 'fmt', not '...'
}

// ---------------------------------------------------------------------------
// Return statement
// ---------------------------------------------------------------------------

static void test_return_statement() {
    AstProgram *p = must_parse("fn f() -> i64 { return 42; }");
    ASSERT_NOT_NULL(p);
    AstFunctionDecl *fn = (AstFunctionDecl *)p->body.data[0];
    AstBlock *body = (AstBlock *)fn->body;
    AstReturn *ret = (AstReturn *)body->body.data[0];
    ASSERT_EQ(NK_RETURN, ret->kind);
    ASSERT_EQ(NK_NUMERIC_LITERAL, ret->expr->kind);
    ASSERT_EQ(42LL, ((AstNumericLiteral *)ret->expr)->value);
}

// ---------------------------------------------------------------------------
// If statement
// ---------------------------------------------------------------------------

static void test_if_no_else() {
    AstProgram *p = must_parse("fn f() -> i64 { if x < 2 return 1; }");
    ASSERT_NOT_NULL(p);
    AstFunctionDecl *fn = (AstFunctionDecl *)p->body.data[0];
    AstBlock *body = (AstBlock *)fn->body;
    AstIf *if_node = (AstIf *)body->body.data[0];
    ASSERT_EQ(NK_IF, if_node->kind);
    ASSERT_NOT_NULL(if_node->cond);
    ASSERT_NOT_NULL(if_node->then);
    ASSERT_NULL(if_node->else_);
}

static void test_if_with_else() {
    AstProgram *p = must_parse("fn f() -> i64 { if x < 2 return 1; else return 2; }");
    ASSERT_NOT_NULL(p);
    AstFunctionDecl *fn = (AstFunctionDecl *)p->body.data[0];
    AstBlock *body = (AstBlock *)fn->body;
    AstIf *if_node = (AstIf *)body->body.data[0];
    ASSERT_NOT_NULL(if_node->else_);
}

// ---------------------------------------------------------------------------
// Call expressions
// ---------------------------------------------------------------------------

static void test_call_no_args() {
    AstProgram *p = must_parse("f();");
    ASSERT_NOT_NULL(p);
    AstCallExpr *call = (AstCallExpr *)p->body.data[0];
    ASSERT_EQ(NK_CALL_EXPR, call->kind);
    ASSERT_EQ(0u, call->args.len);
    ASSERT_EQ(NK_IDENTIFIER, call->callee->kind);
    ASSERT(sv_eq_cstr(((AstIdentifier *)call->callee)->name, "f"));
}

static void test_call_with_args() {
    AstProgram *p = must_parse("add(1, 2);");
    ASSERT_NOT_NULL(p);
    AstCallExpr *call = (AstCallExpr *)p->body.data[0];
    ASSERT_EQ(2u, call->args.len);
    ASSERT_EQ(NK_NUMERIC_LITERAL, call->args.data[0]->kind);
    ASSERT_EQ(NK_NUMERIC_LITERAL, call->args.data[1]->kind);
}

// ---------------------------------------------------------------------------
// factorial.ty — full example file
// ---------------------------------------------------------------------------

static void test_factorial_example() {
    const char *src =
        "extern fn printf(format: ptr, ...) -> i32;\n"
        "fn factorial(n: i64) -> i64 {\n"
        "  if n < 2 return 1;\n"
        "  else return n * factorial(n - 1);\n"
        "}\n"
        "fn main() -> i32 {\n"
        "  printf(\"factorial(5) = %d\\n\", factorial(5));\n"
        "}\n";

    AstProgram *p = must_parse(src);
    ASSERT_NOT_NULL(p);
    ASSERT_EQ(3u, p->body.len);

    ASSERT_EQ(NK_EXTERN_FUNCTION_DECL, p->body.data[0]->kind);
    ASSERT_EQ(NK_FUNCTION_DECL,        p->body.data[1]->kind);
    ASSERT_EQ(NK_FUNCTION_DECL,        p->body.data[2]->kind);

    AstFunctionDecl *factorial = (AstFunctionDecl *)p->body.data[1];
    ASSERT(sv_eq_cstr(factorial->name, "factorial"));
    ASSERT_EQ(1u, factorial->params.len);
    ASSERT(sv_eq_cstr(factorial->params.data[0].name, "n"));
}

// ---------------------------------------------------------------------------
// Source locations
// ---------------------------------------------------------------------------

static void test_loc_on_function_decl() {
    AstProgram *p = must_parse("fn f() -> i32 { }");
    ASSERT_NOT_NULL(p);
    ASSERT_EQ(1u, p->body.data[0]->loc.line);
    ASSERT_EQ(1u, p->body.data[0]->loc.col);
}

static void test_loc_on_second_line() {
    AstProgram *p = must_parse("let x = 1;\nlet y = 2;");
    ASSERT_NOT_NULL(p);
    ASSERT_EQ(1u, p->body.data[0]->loc.line);
    ASSERT_EQ(2u, p->body.data[1]->loc.line);
}

// ---------------------------------------------------------------------------
// Error cases
// ---------------------------------------------------------------------------

static void test_error_missing_semicolon() {
    const char *err = must_fail("let x = 42");
    ASSERT_NOT_NULL(err);
}

static void test_error_missing_equals() {
    const char *err = must_fail("let x 42;");
    ASSERT_NOT_NULL(err);
}

static void test_error_trailing_comma_params() {
    const char *err = must_fail("fn f(a: i64,) -> i64 { }");
    ASSERT_NOT_NULL(err);
}

static void test_error_trailing_comma_args() {
    const char *err = must_fail("f(1, 2,);");
    ASSERT_NOT_NULL(err);
}

// ---------------------------------------------------------------------------
// Debug printer smoke test
// ---------------------------------------------------------------------------

static void test_node_to_string() {
    AstProgram *p = must_parse("fn main() -> i32 { return 0; }");
    ASSERT_NOT_NULL(p);
    char buf[4096];
    int n = node_to_string((Node *)p, buf, sizeof(buf), 0);
    ASSERT(n > 0);
    // Should mention Program and FunctionDecl somewhere
    ASSERT(strstr(buf, "Program") != NULL);
    ASSERT(strstr(buf, "FunctionDecl") != NULL);
    ASSERT(strstr(buf, "main") != NULL);
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

int main(void) {
    arena_init(&g_arena, 0);

    printf("=== Program structure ===\n");
    RUN_TEST(test_empty_program);

    printf("\n=== Variable declarations ===\n");
    RUN_TEST(test_var_decl_simple);
    RUN_TEST(test_var_decl_mutable);
    RUN_TEST(test_var_decl_with_type_hint);

    printf("\n=== Literals ===\n");
    RUN_TEST(test_string_literal);
    RUN_TEST(test_boolean_literal_true);
    RUN_TEST(test_boolean_literal_false);

    printf("\n=== Binary expressions ===\n");
    RUN_TEST(test_binary_add);
    RUN_TEST(test_precedence_mul_over_add);
    RUN_TEST(test_precedence_parens);
    RUN_TEST(test_comparison);

    printf("\n=== Assignment expressions ===\n");
    RUN_TEST(test_assignment_expr);
    RUN_TEST(test_compound_assignment);

    printf("\n=== Function declarations ===\n");
    RUN_TEST(test_function_decl);
    RUN_TEST(test_function_no_params);

    printf("\n=== Extern function declarations ===\n");
    RUN_TEST(test_extern_function_decl);
    RUN_TEST(test_extern_function_variadic);

    printf("\n=== Return statement ===\n");
    RUN_TEST(test_return_statement);

    printf("\n=== If statement ===\n");
    RUN_TEST(test_if_no_else);
    RUN_TEST(test_if_with_else);

    printf("\n=== Call expressions ===\n");
    RUN_TEST(test_call_no_args);
    RUN_TEST(test_call_with_args);

    printf("\n=== Full example ===\n");
    RUN_TEST(test_factorial_example);

    printf("\n=== Source locations ===\n");
    RUN_TEST(test_loc_on_function_decl);
    RUN_TEST(test_loc_on_second_line);

    printf("\n=== Error cases ===\n");
    RUN_TEST(test_error_missing_semicolon);
    RUN_TEST(test_error_missing_equals);
    RUN_TEST(test_error_trailing_comma_params);
    RUN_TEST(test_error_trailing_comma_args);

    printf("\n=== Debug printer ===\n");
    RUN_TEST(test_node_to_string);

    arena_free(&g_arena);
    return TEST_RESULTS();
}
