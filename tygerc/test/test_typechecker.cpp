#include "test_runner.h"
#include "lexer.h"
#include "parser.h"
#include "typechecker.h"
#include "arena.h"
#include <cstring>

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static Arena g_arena;

// Parse and typecheck a source string. Returns true on success.
// On failure, writes the error to `err_out` if non-NULL.
static bool must_typecheck(const char *src, const char **err_out) {
    LexResult lr = lex(sv_from_cstr(src), &g_arena);
    if (lr.error) { if (err_out) *err_out = lr.error; return false; }

    ParseResult pr = parse(&lr.tokens, &g_arena);
    if (pr.error) { if (err_out) *err_out = pr.error; return false; }

    Context ctx;
    context_init(&ctx, &g_arena);
    bool ok = typecheck((Node *)pr.program, &ctx);
    if (!ok && err_out) *err_out = arena_copy_str(&g_arena, ctx.errbuf, strlen(ctx.errbuf));
    return ok;
}

static bool tc_ok(const char *src) {
    return must_typecheck(src, NULL);
}

static bool tc_fails(const char *src) {
    return !must_typecheck(src, NULL);
}

// ---------------------------------------------------------------------------
// Valid programs — must typecheck without error
// ---------------------------------------------------------------------------

static void test_empty_program() {
    ASSERT(tc_ok(""));
}

static void test_numeric_literal() {
    ASSERT(tc_ok("let x = 42;"));
}

static void test_var_decl_with_type_hint() {
    ASSERT(tc_ok("let x: i64 = 42;"));
}

static void test_var_decl_boolean() {
    ASSERT(tc_ok("let b = true;"));
}

static void test_var_decl_string() {
    ASSERT(tc_ok("let s = \"hello\";"));
}

static void test_binary_arithmetic() {
    ASSERT(tc_ok("let x = 1 + 2;"));
    ASSERT(tc_ok("let x = 10 - 3 * 2;"));
}

static void test_comparison() {
    ASSERT(tc_ok("let b = 1 < 2;"));
    ASSERT(tc_ok("let b = 1 == 1;"));
}

static void test_function_decl() {
    ASSERT(tc_ok("fn add(a: i64, b: i64) -> i64 { return a; }"));
}

static void test_function_with_call() {
    ASSERT(tc_ok(
        "fn square(n: i64) -> i64 { return n * n; }\n"
        "fn main() -> i64 { return square(5); }\n"
    ));
}

static void test_extern_function() {
    ASSERT(tc_ok("extern fn puts(s: ptr) -> i32;"));
}

static void test_factorial() {
    ASSERT(tc_ok(
        "extern fn printf(fmt: ptr, ...) -> i32;\n"
        "fn factorial(n: i64) -> i64 {\n"
        "  if n < 2 return 1;\n"
        "  else return n * factorial(n - 1);\n"
        "}\n"
        "fn main() -> i32 {\n"
        "  printf(\"factorial(5) = %d\\n\", factorial(5));\n"
        "}\n"
    ));
}

static void test_if_statement() {
    ASSERT(tc_ok(
        "fn f(x: i64) -> i64 {\n"
        "  if x < 0 return 0;\n"
        "  else return x;\n"
        "}\n"
    ));
}

static void test_assignment() {
    ASSERT(tc_ok(
        "fn f() -> i64 {\n"
        "  let mut x = 1;\n"
        "  x = 2;\n"
        "  return x;\n"
        "}\n"
    ));
}

static void test_mutual_recursion() {
    // Both functions can call each other because of the two-pass hoisting
    ASSERT(tc_ok(
        "fn is_even(n: i64) -> boolean { return is_odd(n); }\n"
        "fn is_odd(n: i64) -> boolean  { return is_even(n); }\n"
    ));
}

// ---------------------------------------------------------------------------
// Type annotation check — verify the typechecker annotates node->type
// ---------------------------------------------------------------------------

static void test_node_type_annotated() {
    LexResult lr = lex(sv_from_cstr("let x = 42;"), &g_arena);
    ASSERT_NULL(lr.error);
    ParseResult pr = parse(&lr.tokens, &g_arena);
    ASSERT_NULL(pr.error);

    Context ctx;
    context_init(&ctx, &g_arena);
    ASSERT(typecheck((Node *)pr.program, &ctx));

    // The initialiser of the var decl should be annotated as i64
    AstVarDecl *decl = (AstVarDecl *)pr.program->body.data[0];
    ASSERT_NOT_NULL(decl->init->type);
    ASSERT(decl->init->type == TY_I64);
}

// ---------------------------------------------------------------------------
// Error cases — must fail typechecking
// ---------------------------------------------------------------------------

static void test_error_undeclared_identifier() {
    ASSERT(tc_fails("let x = y;"));
}

static void test_error_type_mismatch_hint() {
    // i32 variable cannot hold a boolean
    ASSERT(tc_fails("let x: i32 = true;"));
}

static void test_error_wrong_arg_count() {
    ASSERT(tc_fails(
        "fn f(a: i64) -> i64 { return a; }\n"
        "fn g() -> i64 { return f(1, 2); }\n"
    ));
}

static void test_error_wrong_arg_count_too_few() {
    ASSERT(tc_fails(
        "fn f(a: i64, b: i64) -> i64 { return a; }\n"
        "fn g() -> i64 { return f(1); }\n"
    ));
}

static void test_error_return_type_mismatch() {
    ASSERT(tc_fails("fn f() -> i64 { return true; }"));
}

static void test_error_if_non_boolean_condition() {
    ASSERT(tc_fails("fn f() -> i64 { if 1 return 0; }"));
}

static void test_error_redeclare_in_same_scope() {
    ASSERT(tc_fails("let x = 1; let x = 2;"));
}

static void test_error_call_non_function() {
    ASSERT(tc_fails(
        "fn f() -> i64 {\n"
        "  let x = 1;\n"
        "  return x(2);\n"  // x is i64, not callable
        "}\n"
    ));
}

static void test_error_arithmetic_on_bool() {
    ASSERT(tc_fails("let x = true + 1;"));
}

static void test_error_unknown_type() {
    ASSERT(tc_fails("let x: Banana = 1;"));
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

int main(void) {
    arena_init(&g_arena, 0);

    printf("=== Valid programs ===\n");
    RUN_TEST(test_empty_program);
    RUN_TEST(test_numeric_literal);
    RUN_TEST(test_var_decl_with_type_hint);
    RUN_TEST(test_var_decl_boolean);
    RUN_TEST(test_var_decl_string);
    RUN_TEST(test_binary_arithmetic);
    RUN_TEST(test_comparison);
    RUN_TEST(test_function_decl);
    RUN_TEST(test_function_with_call);
    RUN_TEST(test_extern_function);
    RUN_TEST(test_factorial);
    RUN_TEST(test_if_statement);
    RUN_TEST(test_assignment);
    RUN_TEST(test_mutual_recursion);

    printf("\n=== Type annotation ===\n");
    RUN_TEST(test_node_type_annotated);

    printf("\n=== Error cases ===\n");
    RUN_TEST(test_error_undeclared_identifier);
    RUN_TEST(test_error_type_mismatch_hint);
    RUN_TEST(test_error_wrong_arg_count);
    RUN_TEST(test_error_wrong_arg_count_too_few);
    RUN_TEST(test_error_return_type_mismatch);
    RUN_TEST(test_error_if_non_boolean_condition);
    RUN_TEST(test_error_redeclare_in_same_scope);
    RUN_TEST(test_error_call_non_function);
    RUN_TEST(test_error_arithmetic_on_bool);
    RUN_TEST(test_error_unknown_type);

    arena_free(&g_arena);
    return TEST_RESULTS();
}
