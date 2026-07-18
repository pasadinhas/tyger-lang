#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/stat.h>

#include "arena.h"
#include "lexer.h"
#include "parser.h"
#include "typechecker.h"
#include "codegen.h"

// ---------------------------------------------------------------------------
// Read an entire file into an arena-allocated buffer.
// ---------------------------------------------------------------------------

static const char *read_file(const char *path, Arena *arena) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "tygerc: cannot open '%s'\n", path);
        return NULL;
    }

    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    rewind(f);

    char *buf = (char *)arena_alloc(arena, (size_t)size + 1, 1);
    size_t read = fread(buf, 1, (size_t)size, f);
    fclose(f);

    if ((long)read != size) {
        fprintf(stderr, "tygerc: failed to read '%s'\n", path);
        return NULL;
    }

    buf[size] = '\0';
    return buf;
}

// ---------------------------------------------------------------------------
// Derive a default output path from the source path.
// e.g. "examples/factorial.ty" → "out/factorial"  (binary)
//                               → "out/factorial.ll" (for the temp IR file)
// ---------------------------------------------------------------------------

static void default_output_stem(const char *src_path, char *stem, size_t stem_size) {
    const char *base = strrchr(src_path, '/');
    base = base ? base + 1 : src_path;

    char tmp[256];
    strncpy(tmp, base, sizeof(tmp) - 1);
    tmp[sizeof(tmp) - 1] = '\0';

    char *dot = strrchr(tmp, '.');
    if (dot) *dot = '\0';

    // Ensure ./out/ exists
    mkdir("out", 0755);

    snprintf(stem, stem_size, "out/%s", tmp);
}

static bool ends_with_ll(const char *path) {
    size_t len = strlen(path);
    return len >= 3 && strcmp(path + len - 3, ".ll") == 0;
}

// ---------------------------------------------------------------------------
// Print usage
// ---------------------------------------------------------------------------

static void usage(void) {
    fprintf(stderr,
            "Usage: tygerc <source.ty> [options]\n"
            "\n"
            "Options:\n"
            "  -o <path>    Output path. If <path> ends in .ll, emits LLVM IR only.\n"
            "               Defaults to out/<stem> for binaries.\n"
            "  --emit-llvm  Emit LLVM IR (.ll) and stop. Implies -o out/<stem>.ll\n"
            "               unless -o is specified.\n"
            );
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

int main(int argc, char *argv[]) {
    const char *source_path = NULL;
    const char *output_path = NULL;
    bool emit_llvm = false;

    // Parse arguments
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--emit-llvm") == 0) {
            emit_llvm = true;
        } else if (strcmp(argv[i], "-o") == 0) {
            if (i + 1 >= argc) {
                fprintf(stderr, "tygerc: -o requires an argument\n");
                return 1;
            }
            output_path = argv[++i];
        } else if (argv[i][0] == '-') {
            fprintf(stderr, "tygerc: unknown option '%s'\n", argv[i]);
            usage();
            return 1;
        } else {
            if (source_path) {
                fprintf(stderr, "tygerc: multiple source files not supported\n");
                return 1;
            }
            source_path = argv[i];
        }
    }

    if (!source_path) {
        usage();
        return 1;
    }

    // If -o ends in .ll, treat as --emit-llvm
    if (output_path && ends_with_ll(output_path)) {
        emit_llvm = true;
    }

    // Derive default output stem
    char stem[512];
    default_output_stem(source_path, stem, sizeof(stem));

    // Determine the final output path and the IR path
    char ll_path[512];
    char bin_path[512];

    if (emit_llvm) {
        // IR output: use -o if given, else <stem>.ll
        if (output_path) {
            strncpy(ll_path, output_path, sizeof(ll_path) - 1);
            ll_path[sizeof(ll_path) - 1] = '\0';
        } else {
            snprintf(ll_path, sizeof(ll_path), "%s.ll", stem);
        }
        bin_path[0] = '\0'; // not used
    } else {
        // Binary output: use -o if given, else <stem>
        // The .ll is a temp file placed next to the binary, removed after linking
        snprintf(ll_path, sizeof(ll_path), "%s.ll", stem);
        if (output_path) {
            strncpy(bin_path, output_path, sizeof(bin_path) - 1);
            bin_path[sizeof(bin_path) - 1] = '\0';
        } else {
            strncpy(bin_path, stem, sizeof(bin_path) - 1);
            bin_path[sizeof(bin_path) - 1] = '\0';
        }
    }

    // -----------------------------------------------------------------------
    // Pipeline
    // -----------------------------------------------------------------------

    Arena arena;
    arena_init(&arena, 0);

    // 1. Read source
    const char *source = read_file(source_path, &arena);
    if (!source) { arena_free(&arena); return 1; }

    // 2. Lex
    LexResult lr = lex(sv_from_cstr(source), &arena);
    if (lr.error) {
        fprintf(stderr, "tygerc: lex error: %s\n", lr.error);
        arena_free(&arena);
        return 1;
    }

    // 3. Parse
    ParseResult pr = parse(&lr.tokens, &arena);
    if (pr.error) {
        fprintf(stderr, "tygerc: parse error: %s\n", pr.error);
        arena_free(&arena);
        return 1;
    }

    // 4. Typecheck
    Context ctx;
    context_init(&ctx, &arena);
    if (!typecheck((Node *)pr.program, &ctx)) {
        fprintf(stderr, "tygerc: type error: %s\n", ctx.errbuf);
        arena_free(&arena);
        return 1;
    }

    // 5. Codegen → .ll
    CodegenResult cr = codegen(pr.program, ll_path, &arena);
    if (cr.error) {
        fprintf(stderr, "tygerc: codegen error: %s\n", cr.error);
        arena_free(&arena);
        return 1;
    }

    if (emit_llvm) {
        fprintf(stderr, "tygerc: wrote %s\n", ll_path);
        arena_free(&arena);
        return 0;
    }

    // 6. Link via clang
    char cmd[1024];
    snprintf(cmd, sizeof(cmd),
             "clang -Wno-override-module %s -o %s",
             ll_path, bin_path);

    int rc = system(cmd);

    // Remove the temporary .ll file regardless of link success
    remove(ll_path);

    if (rc != 0) {
        fprintf(stderr, "tygerc: linking failed (clang exited %d)\n", rc);
        arena_free(&arena);
        return 1;
    }

    fprintf(stderr, "tygerc: wrote %s\n", bin_path);

    arena_free(&arena);
    return 0;
}
