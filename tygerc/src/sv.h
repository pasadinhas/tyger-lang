#pragma once

#include <stddef.h>
#include <string.h>
#include <stdbool.h>
#include <stdio.h>

// SV — a non-owning view into a string.
// Does NOT guarantee null-termination.
typedef struct {
    const char *data;
    size_t      len;
} SV;

static inline SV sv_from_parts(const char *data, size_t len) {
    SV sv;
    sv.data = data;
    sv.len  = len;
    return sv;
}

static inline SV sv_from_cstr(const char *cstr) {
    return sv_from_parts(cstr, strlen(cstr));
}

// Create an SV from a string literal: SV_LIT("hello")
#define SV_LIT(s) sv_from_parts((s), sizeof(s) - 1)

// printf format helper: printf("%" SV_FMT, SV_ARG(sv))
#define SV_FMT     "%.*s"
#define SV_ARG(sv) (int)(sv).len, (sv).data

static inline bool sv_eq(SV a, SV b) {
    return a.len == b.len && memcmp(a.data, b.data, a.len) == 0;
}

static inline bool sv_eq_cstr(SV a, const char *b) {
    return sv_eq(a, sv_from_cstr(b));
}

static inline bool sv_starts_with(SV sv, SV prefix) {
    return sv.len >= prefix.len && memcmp(sv.data, prefix.data, prefix.len) == 0;
}

static inline bool sv_starts_with_cstr(SV sv, const char *prefix) {
    return sv_starts_with(sv, sv_from_cstr(prefix));
}

// Advance the view by n characters (does not bounds-check)
static inline SV sv_advance(SV sv, size_t n) {
    return sv_from_parts(sv.data + n, sv.len - n);
}

static inline bool sv_empty(SV sv) {
    return sv.len == 0;
}
