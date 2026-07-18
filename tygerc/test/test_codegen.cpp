#include "test_runner.h"
#include "lexer.h"
#include "parser.h"
#include "typechecker.h"
#include "codegen.h"
#include "arena.h"
#include <cstdio>
#include <cstring>
#include <cstdlib>

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static Arena g_arena;

// Compile a Tyger source string, link it, run it, and capture stdout.
// Returns true if compilation + run succeeded and stdout matches `expected`.
// Writes diagnostic to stderr on failure.
static bool compile_and_run(const char *src, const char *expected_output) {
    // 1. Lex
    LexResult lr = lex(sv_from_cstr(src), &g_arena);
    if (lr.error) {
        fprintf(stderr, "  lex error: %s\n", lr.error);
        return false;
    }

    // 2. Parse
    ParseResult pr = parse(&lr.tokens, &g_arena);
    if (pr.error) {
        fprintf(stderr, "  parse error: %s\n", pr.error);
        return false;
    }

    // 3. Typecheck
    Context ctx;
    context_init(&ctx, &g_arena);
    if (!typecheck((Node *)pr.program, &ctx)) {
        fprintf(stderr, "  typecheck error: %s\n", ctx.errbuf);
        return false;
    }

    // 4. Codegen → .ll file
    const char *ll_path  = "/tmp/tyger_test.ll";
    const char *bin_path = "/tmp/tyger_test_bin";

    CodegenResult cr = codegen(pr.program, ll_path, &g_arena);
    if (cr.error) {
        fprintf(stderr, "  codegen error: %s\n", cr.error);
        return false;
    }

    // 5. Compile .ll → binary using clang
    char cmd[512];
    snprintf(cmd, sizeof(cmd),
             "/opt/homebrew/opt/llvm/bin/clang %s -o %s 2>/tmp/tyger_clang_err.txt",
             ll_path, bin_path);
    if (system(cmd) != 0) {
        fprintf(stderr, "  clang compilation failed. IR:\n");
        FILE *f = fopen(ll_path, "r");
        if (f) { char line[256]; while (fgets(line, sizeof(line), f)) fprintf(stderr, "    %s", line); fclose(f); }
        fprintf(stderr, "  clang stderr:\n");
        f = fopen("/tmp/tyger_clang_err.txt", "r");
        if (f) { char line[256]; while (fgets(line, sizeof(line), f)) fprintf(stderr, "    %s", line); fclose(f); }
        return false;
    }

    // 6. Run and capture stdout
    char run_cmd[256];
    snprintf(run_cmd, sizeof(run_cmd), "%s 2>/dev/null", bin_path);
    FILE *pipe = popen(run_cmd, "r");
    if (!pipe) {
        fprintf(stderr, "  failed to run binary\n");
        return false;
    }

    char output[1024] = {0};
    size_t pos = 0;
    int c;
    while ((c = fgetc(pipe)) != EOF && pos < sizeof(output) - 1) {
        output[pos++] = (char)c;
    }
    output[pos] = '\0';
    int exit_code = pclose(pipe);
    (void)exit_code;

    if (strcmp(output, expected_output) != 0) {
        fprintf(stderr, "  output mismatch:\n");
        fprintf(stderr, "    expected: %s\n", expected_output);
        fprintf(stderr, "    got:      %s\n", output);
        return false;
    }

    return true;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

static void test_printf_hello() {
    ASSERT(compile_and_run(
        "extern fn printf(fmt: ptr, ...) -> i32;\n"
        "fn main() -> i32 {\n"
        "  printf(\"hello\\n\");\n"
        "}\n",
        "hello\n"
    ));
}

static void test_factorial() {
    ASSERT(compile_and_run(
        "extern fn printf(fmt: ptr, ...) -> i32;\n"
        "fn factorial(n: i64) -> i64 {\n"
        "  if n < 2 return 1;\n"
        "  else return n * factorial(n - 1);\n"
        "}\n"
        "fn main() -> i32 {\n"
        "  printf(\"%lld\\n\", factorial(5));\n"
        "}\n",
        "120\n"
    ));
}

static void test_arithmetic() {
    ASSERT(compile_and_run(
        "extern fn printf(fmt: ptr, ...) -> i32;\n"
        "fn main() -> i32 {\n"
        "  printf(\"%lld\\n\", 3 + 4 * 2);\n"
        "}\n",
        "11\n"
    ));
}

static void test_variable_and_assignment() {
    ASSERT(compile_and_run(
        "extern fn printf(fmt: ptr, ...) -> i32;\n"
        "fn main() -> i32 {\n"
        "  let mut x = 10;\n"
        "  x = x + 5;\n"
        "  printf(\"%lld\\n\", x);\n"
        "}\n",
        "15\n"
    ));
}

static void test_if_else() {
    ASSERT(compile_and_run(
        "extern fn printf(fmt: ptr, ...) -> i32;\n"
        "fn max(a: i64, b: i64) -> i64 {\n"
        "  if a > b return a;\n"
        "  else return b;\n"
        "}\n"
        "fn main() -> i32 {\n"
        "  printf(\"%lld\\n\", max(3, 7));\n"
        "}\n",
        "7\n"
    ));
}

static void test_multiple_functions() {
    ASSERT(compile_and_run(
        "extern fn printf(fmt: ptr, ...) -> i32;\n"
        "fn double(n: i64) -> i64 { return n * 2; }\n"
        "fn triple(n: i64) -> i64 { return n * 3; }\n"
        "fn main() -> i32 {\n"
        "  printf(\"%lld %lld\\n\", double(5), triple(5));\n"
        "}\n",
        "10 15\n"
    ));
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

int main(void) {
    arena_init(&g_arena, 0);

    printf("=== Codegen end-to-end tests ===\n");
    RUN_TEST(test_printf_hello);
    RUN_TEST(test_factorial);
    RUN_TEST(test_arithmetic);
    RUN_TEST(test_variable_and_assignment);
    RUN_TEST(test_if_else);
    RUN_TEST(test_multiple_functions);

    arena_free(&g_arena);
    return TEST_RESULTS();
}
