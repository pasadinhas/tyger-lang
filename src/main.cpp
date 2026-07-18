#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>

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
// Derive a default output stem from the source path.
// e.g. "examples/factorial.ty" -> "out/factorial"
// ---------------------------------------------------------------------------

static void default_output_stem(const char *src_path, char *stem, size_t stem_size) {
    const char *base = strrchr(src_path, '/');
    base = base ? base + 1 : src_path;

    char tmp[256];
    strncpy(tmp, base, sizeof(tmp) - 1);
    tmp[sizeof(tmp) - 1] = '\0';

    char *dot = strrchr(tmp, '.');
    if (dot) *dot = '\0';

    mkdir("out", 0755);
    snprintf(stem, stem_size, "out/%s", tmp);
}

// ---------------------------------------------------------------------------
// Print usage
// ---------------------------------------------------------------------------

static void usage(void) {
    fprintf(stderr,
            "Usage: tygerc <mode> <source.ty> [-o <path>]\n"
            "\n"
            "Modes:\n"
            "  lex           Scan the source file and print the token stream.\n"
            "  parse         Parse the source file and print the AST.\n"
            "  typecheck     Lex, parse and type-check; print the annotated AST.\n"
            "  llvm          Emit LLVM IR to stdout.\n"
            "  compile       Compile to a native binary (default output: out/<stem>).\n"
            "  run           Compile and immediately execute the binary.\n"
            "\n"
            "Options:\n"
            "  -o <path>     Output path for 'compile' and 'run' modes.\n"
            );
}

// ---------------------------------------------------------------------------
// Pipeline helpers
// ---------------------------------------------------------------------------

// Run lex and return token list. Returns false on error.
static bool do_lex(const char *source, Arena *arena, LexResult *out) {
    *out = lex(sv_from_cstr(source), arena);
    if (out->error) {
        fprintf(stderr, "tygerc: lex error: %s\n", out->error);
        return false;
    }
    return true;
}

// Run parse on an already-lexed token list. Returns false on error.
static bool do_parse(LexResult *lr, Arena *arena, ParseResult *out) {
    *out = parse(&lr->tokens, arena);
    if (out->error) {
        fprintf(stderr, "tygerc: parse error: %s\n", out->error);
        return false;
    }
    return true;
}

// Run typechecker on an already-parsed program. Returns false on error.
static bool do_typecheck(ParseResult *pr, Arena *arena, Context *ctx) {
    context_init(ctx, arena);
    if (!typecheck((Node *)pr->program, ctx)) {
        fprintf(stderr, "tygerc: type error: %s\n", ctx->errbuf);
        return false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// Mode: lex — print token stream
// ---------------------------------------------------------------------------

static int mode_lex(const char *source_path, Arena *arena) {
    const char *source = read_file(source_path, arena);
    if (!source) return 1;

    LexResult lr;
    if (!do_lex(source, arena, &lr)) return 1;

    for (size_t i = 0; i < lr.tokens.len; i++) {
        Token *t = &lr.tokens.data[i];
        printf("[%u:%u] %-16s  \"" SV_FMT "\"\n",
               t->loc.line, t->loc.col,
               token_type_name(t->type),
               SV_ARG(t->value));
    }
    return 0;
}

// ---------------------------------------------------------------------------
// Mode: parse — print AST (no typechecking)
// ---------------------------------------------------------------------------

static int mode_parse(const char *source_path, Arena *arena) {
    const char *source = read_file(source_path, arena);
    if (!source) return 1;

    LexResult lr;
    if (!do_lex(source, arena, &lr)) return 1;

    ParseResult pr;
    if (!do_parse(&lr, arena, &pr)) return 1;

    // Use the existing debug printer; allocate a generous buffer on the arena.
    const int BUF_SIZE = 1 << 20; // 1 MiB
    char *buf = (char *)arena_alloc(arena, (size_t)BUF_SIZE, 1);
    node_to_string((Node *)pr.program, buf, BUF_SIZE, 0);
    printf("%s", buf);
    return 0;
}

// ---------------------------------------------------------------------------
// Mode: typecheck — print type-annotated AST
// ---------------------------------------------------------------------------

static int mode_typecheck(const char *source_path, Arena *arena) {
    const char *source = read_file(source_path, arena);
    if (!source) return 1;

    LexResult lr;
    if (!do_lex(source, arena, &lr)) return 1;

    ParseResult pr;
    if (!do_parse(&lr, arena, &pr)) return 1;

    Context ctx;
    if (!do_typecheck(&pr, arena, &ctx)) return 1;

    // Annotate type info in the output by using the existing printer.
    // The AST printer doesn't show types yet; print the tree then confirm success.
    const int BUF_SIZE = 1 << 20;
    char *buf = (char *)arena_alloc(arena, (size_t)BUF_SIZE, 1);
    node_to_string((Node *)pr.program, buf, BUF_SIZE, 0);
    printf("%s", buf);
    fprintf(stderr, "tygerc: typecheck OK\n");
    return 0;
}

// ---------------------------------------------------------------------------
// Mode: llvm — emit LLVM IR to stdout
// ---------------------------------------------------------------------------

static int mode_llvm(const char *source_path, Arena *arena) {
    const char *source = read_file(source_path, arena);
    if (!source) return 1;

    LexResult lr;
    if (!do_lex(source, arena, &lr)) return 1;

    ParseResult pr;
    if (!do_parse(&lr, arena, &pr)) return 1;

    Context ctx;
    if (!do_typecheck(&pr, arena, &ctx)) return 1;

    // Write IR to a temp file, then cat it to stdout.
    // (The codegen API writes to a file; we use a temp path under /tmp.)
    char tmp_path[256];
    snprintf(tmp_path, sizeof(tmp_path), "/tmp/tygerc_llvm_%d.ll", (int)getpid());

    CodegenResult cr = codegen(pr.program, tmp_path, arena);
    if (cr.error) {
        fprintf(stderr, "tygerc: codegen error: %s\n", cr.error);
        return 1;
    }

    // Stream the file to stdout
    FILE *f = fopen(tmp_path, "r");
    if (!f) {
        fprintf(stderr, "tygerc: cannot read temp IR file '%s'\n", tmp_path);
        return 1;
    }
    char chunk[4096];
    size_t n;
    while ((n = fread(chunk, 1, sizeof(chunk), f)) > 0) {
        fwrite(chunk, 1, n, stdout);
    }
    fclose(f);
    remove(tmp_path);
    return 0;
}

// ---------------------------------------------------------------------------
// Mode: compile — compile to a native binary
// ---------------------------------------------------------------------------

static int mode_compile(const char *source_path, const char *output_path, Arena *arena) {
    const char *source = read_file(source_path, arena);
    if (!source) return 1;

    LexResult lr;
    if (!do_lex(source, arena, &lr)) return 1;

    ParseResult pr;
    if (!do_parse(&lr, arena, &pr)) return 1;

    Context ctx;
    if (!do_typecheck(&pr, arena, &ctx)) return 1;

    // Derive paths
    char stem[512];
    default_output_stem(source_path, stem, sizeof(stem));

    char ll_path[512];
    char bin_path[512];

    snprintf(ll_path, sizeof(ll_path), "%s.ll", stem);

    if (output_path) {
        strncpy(bin_path, output_path, sizeof(bin_path) - 1);
        bin_path[sizeof(bin_path) - 1] = '\0';
    } else {
        strncpy(bin_path, stem, sizeof(bin_path) - 1);
        bin_path[sizeof(bin_path) - 1] = '\0';
    }

    CodegenResult cr = codegen(pr.program, ll_path, arena);
    if (cr.error) {
        fprintf(stderr, "tygerc: codegen error: %s\n", cr.error);
        return 1;
    }

    char cmd[1024];
    snprintf(cmd, sizeof(cmd), "clang -Wno-override-module %s -o %s", ll_path, bin_path);
    int rc = system(cmd);
    remove(ll_path);

    if (rc != 0) {
        fprintf(stderr, "tygerc: linking failed (clang exited %d)\n", rc);
        return 1;
    }

    fprintf(stderr, "tygerc: wrote %s\n", bin_path);
    return 0;
}

// ---------------------------------------------------------------------------
// Mode: run — compile then execute
// ---------------------------------------------------------------------------

static int mode_run(const char *source_path, const char *output_path, Arena *arena) {
    // Determine where the binary goes
    char stem[512];
    default_output_stem(source_path, stem, sizeof(stem));

    char bin_path[512];
    if (output_path) {
        strncpy(bin_path, output_path, sizeof(bin_path) - 1);
        bin_path[sizeof(bin_path) - 1] = '\0';
    } else {
        strncpy(bin_path, stem, sizeof(bin_path) - 1);
        bin_path[sizeof(bin_path) - 1] = '\0';
    }

    int rc = mode_compile(source_path, bin_path, arena);
    if (rc != 0) return rc;

    // Execute
    char cmd[512];
    snprintf(cmd, sizeof(cmd), "%s", bin_path);
    rc = system(cmd);

    // Clean up the binary after running
    remove(bin_path);

    return (rc == 0) ? 0 : 1;
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

int main(int argc, char *argv[]) {
    if (argc < 3) {
        usage();
        return 1;
    }

    const char *mode        = argv[1];
    const char *source_path = argv[2];
    const char *output_path = NULL;

    // Parse optional flags after the positional arguments
    for (int i = 3; i < argc; i++) {
        if (strcmp(argv[i], "-o") == 0) {
            if (i + 1 >= argc) {
                fprintf(stderr, "tygerc: -o requires an argument\n");
                return 1;
            }
            output_path = argv[++i];
        } else {
            fprintf(stderr, "tygerc: unknown option '%s'\n", argv[i]);
            usage();
            return 1;
        }
    }

    Arena arena;
    arena_init(&arena, 0);

    int rc = 1;

    if (strcmp(mode, "lex") == 0) {
        rc = mode_lex(source_path, &arena);
    } else if (strcmp(mode, "parse") == 0) {
        rc = mode_parse(source_path, &arena);
    } else if (strcmp(mode, "typecheck") == 0) {
        rc = mode_typecheck(source_path, &arena);
    } else if (strcmp(mode, "llvm") == 0) {
        rc = mode_llvm(source_path, &arena);
    } else if (strcmp(mode, "compile") == 0) {
        rc = mode_compile(source_path, output_path, &arena);
    } else if (strcmp(mode, "run") == 0) {
        rc = mode_run(source_path, output_path, &arena);
    } else {
        fprintf(stderr, "tygerc: unknown mode '%s'\n", mode);
        usage();
        rc = 1;
    }

    arena_free(&arena);
    return rc;
}
