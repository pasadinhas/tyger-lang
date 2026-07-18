#pragma once

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Minimal test runner — no external dependencies.
//
// Usage:
//   TEST("my test") {
//       ASSERT(1 + 1 == 2);
//       ASSERT_EQ(42, some_function());
//       ASSERT_STR_EQ("hello", some_str());
//   }
//   int main(void) { return TEST_RESULTS(); }

static int __test_pass_count = 0;
static int __test_fail_count = 0;
static const char *__current_test = NULL;

#define TEST(name)                                                          \
    static void __test_fn_##__LINE__(void);                                 \
    static void __test_register_##__LINE__(void) __attribute__((constructor)); \
    static void __test_register_##__LINE__(void) {                          \
        __current_test = (name);                                            \
        __test_fn_##__LINE__();                                             \
    }                                                                       \
    static void __test_fn_##__LINE__(void)

// Note: the above uses __attribute__((constructor)) so tests run before main.
// We instead collect them manually to control output. Use the approach below:

// Reset and use a simpler explicit-call approach instead.
#undef TEST

// ---- Simple explicit approach ----
// Each test is a function. Call RUN_TEST(fn) from main.

#define ASSERT(cond)                                                        \
    do {                                                                    \
        if (!(cond)) {                                                      \
            fprintf(stderr, "  FAIL [%s:%d] Assertion failed: %s\n",       \
                    __FILE__, __LINE__, #cond);                             \
            __test_fail_count++;                                            \
            return;                                                         \
        }                                                                   \
    } while (0)

#define ASSERT_EQ(expected, actual)                                         \
    do {                                                                    \
        auto __e = (expected);                                              \
        auto __a = (actual);                                                \
        if (__e != __a) {                                                   \
            fprintf(stderr, "  FAIL [%s:%d] Expected %s == %s\n",          \
                    __FILE__, __LINE__, #expected, #actual);                \
            __test_fail_count++;                                            \
            return;                                                         \
        }                                                                   \
    } while (0)

#define ASSERT_STR_EQ(expected, actual)                                     \
    do {                                                                    \
        const char *__e = (expected);                                       \
        const char *__a = (actual);                                         \
        if (strcmp(__e, __a) != 0) {                                        \
            fprintf(stderr, "  FAIL [%s:%d] Expected \"%s\" but got \"%s\"\n", \
                    __FILE__, __LINE__, __e, __a);                          \
            __test_fail_count++;                                            \
            return;                                                         \
        }                                                                   \
    } while (0)

#define ASSERT_NULL(ptr)                                                    \
    do {                                                                    \
        if ((ptr) != NULL) {                                                \
            fprintf(stderr, "  FAIL [%s:%d] Expected NULL: %s\n",          \
                    __FILE__, __LINE__, #ptr);                              \
            __test_fail_count++;                                            \
            return;                                                         \
        }                                                                   \
    } while (0)

#define ASSERT_NOT_NULL(ptr)                                                \
    do {                                                                    \
        if ((ptr) == NULL) {                                                \
            fprintf(stderr, "  FAIL [%s:%d] Expected non-NULL: %s\n",      \
                    __FILE__, __LINE__, #ptr);                              \
            __test_fail_count++;                                            \
            return;                                                         \
        }                                                                   \
    } while (0)

#define RUN_TEST(fn)                                                        \
    do {                                                                    \
        int __before = __test_fail_count;                                   \
        printf("  %-50s", #fn);                                             \
        fn();                                                               \
        if (__test_fail_count == __before) {                                \
            printf("OK\n");                                                 \
            __test_pass_count++;                                            \
        }                                                                   \
    } while (0)

#define TEST_RESULTS()                                                      \
    ({                                                                      \
        printf("\n%d passed, %d failed\n",                                  \
               __test_pass_count, __test_fail_count);                       \
        __test_fail_count > 0 ? 1 : 0;                                      \
    })
