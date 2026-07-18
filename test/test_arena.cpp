#include "test_runner.h"
#include "arena.h"
#include "sv.h"
#include "da.h"

// ---- Arena tests ----

static void test_arena_basic_alloc() {
    Arena a = {0};
    arena_init(&a, 0);

    int *x = arena_alloc_t(&a, int);
    ASSERT_NOT_NULL(x);
    *x = 42;
    ASSERT_EQ(42, *x);

    arena_free(&a);
}

static void test_arena_zero_initialised() {
    Arena a = {0};
    arena_init(&a, 0);

    int *x = arena_alloc_t(&a, int);
    // arena_alloc zeroes memory
    ASSERT_EQ(0, *x);

    arena_free(&a);
}

static void test_arena_multiple_allocs() {
    Arena a = {0};
    arena_init(&a, 0);

    int *nums[100];
    for (int i = 0; i < 100; i++) {
        nums[i] = arena_alloc_t(&a, int);
        *nums[i] = i * 7;
    }
    for (int i = 0; i < 100; i++) {
        ASSERT_EQ(i * 7, *nums[i]);
    }

    arena_free(&a);
}

static void test_arena_alignment() {
    Arena a = {0};
    arena_init(&a, 0);

    // Allocate a char to misalign the cursor
    (void)arena_alloc(&a, 1, 1);

    // Now allocate a double — must be 8-byte aligned
    double *d = arena_alloc_t(&a, double);
    ASSERT_EQ(0u, (uintptr_t)d % alignof(double));
    *d = 3.14;
    ASSERT((*d > 3.13) && (*d < 3.15));

    arena_free(&a);
}

static void test_arena_overflow_into_new_block() {
    // Use a tiny block size to force a new block allocation
    Arena a = {0};
    arena_init(&a, 64);

    // Allocate enough to fill the first block and spill into a second
    int *ptrs[20];
    for (int i = 0; i < 20; i++) {
        ptrs[i] = arena_alloc_t(&a, int);
        ASSERT_NOT_NULL(ptrs[i]);
        *ptrs[i] = i;
    }
    for (int i = 0; i < 20; i++) {
        ASSERT_EQ(i, *ptrs[i]);
    }

    arena_free(&a);
}

static void test_arena_copy_str() {
    Arena a = {0};
    arena_init(&a, 0);

    const char *src = "hello, tyger";
    const char *copy = arena_copy_str(&a, src, strlen(src));

    ASSERT_NOT_NULL(copy);
    ASSERT_STR_EQ(src, copy);
    // Must be a different pointer (actually copied)
    ASSERT(copy != src);

    arena_free(&a);
}

// ---- SV tests ----

static void test_sv_from_cstr() {
    SV sv = sv_from_cstr("hello");
    ASSERT_EQ(5u, sv.len);
    ASSERT(memcmp(sv.data, "hello", 5) == 0);
}

static void test_sv_eq() {
    SV a = sv_from_cstr("foo");
    SV b = sv_from_cstr("foo");
    SV c = sv_from_cstr("bar");
    ASSERT(sv_eq(a, b));
    ASSERT(!sv_eq(a, c));
}

static void test_sv_eq_cstr() {
    SV sv = sv_from_cstr("tyger");
    ASSERT(sv_eq_cstr(sv, "tyger"));
    ASSERT(!sv_eq_cstr(sv, "tiger"));
}

static void test_sv_starts_with() {
    SV sv = sv_from_cstr("hello world");
    ASSERT(sv_starts_with_cstr(sv, "hello"));
    ASSERT(!sv_starts_with_cstr(sv, "world"));
}

static void test_sv_advance() {
    SV sv = sv_from_cstr("hello");
    SV rest = sv_advance(sv, 2);
    ASSERT_EQ(3u, rest.len);
    ASSERT(memcmp(rest.data, "llo", 3) == 0);
}

static void test_sv_lit_macro() {
    SV sv = SV_LIT("tyger");
    ASSERT_EQ(5u, sv.len);
    ASSERT(sv_eq_cstr(sv, "tyger"));
}

// ---- DA tests ----

static void test_da_push_and_access() {
    DA(int) da = {0};
    da_push(&da, 10);
    da_push(&da, 20);
    da_push(&da, 30);
    ASSERT_EQ(3u, da.len);
    ASSERT_EQ(10, da.data[0]);
    ASSERT_EQ(20, da.data[1]);
    ASSERT_EQ(30, da.data[2]);
    da_free(&da);
}

static void test_da_grows() {
    DA(int) da = {0};
    for (int i = 0; i < 100; i++) {
        da_push(&da, i);
    }
    ASSERT_EQ(100u, da.len);
    for (int i = 0; i < 100; i++) {
        ASSERT_EQ(i, da.data[i]);
    }
    da_free(&da);
}

static void test_da_pop() {
    DA(int) da = {0};
    da_push(&da, 1);
    da_push(&da, 2);
    int v = da_pop(&da);
    ASSERT_EQ(2, v);
    ASSERT_EQ(1u, da.len);
    da_free(&da);
}

static void test_da_last() {
    DA(int) da = {0};
    da_push(&da, 5);
    da_push(&da, 9);
    ASSERT_EQ(9, da_last(&da));
    da_free(&da);
}

static void test_da_clear() {
    DA(int) da = {0};
    da_push(&da, 1);
    da_push(&da, 2);
    size_t old_cap = da.cap;
    da_clear(&da);
    ASSERT_EQ(0u, da.len);
    ASSERT_EQ(old_cap, da.cap); // capacity not lost
    da_free(&da);
}

static void test_da_structs() {
    typedef struct { int x; int y; } Point;
    DA(Point) da = {0};
    Point p1 = {1, 2};
    Point p2 = {3, 4};
    da_push(&da, p1);
    da_push(&da, p2);
    ASSERT_EQ(2u, da.len);
    ASSERT_EQ(1, da.data[0].x);
    ASSERT_EQ(4, da.data[1].y);
    da_free(&da);
}

int main(void) {
    printf("=== Arena ===\n");
    RUN_TEST(test_arena_basic_alloc);
    RUN_TEST(test_arena_zero_initialised);
    RUN_TEST(test_arena_multiple_allocs);
    RUN_TEST(test_arena_alignment);
    RUN_TEST(test_arena_overflow_into_new_block);
    RUN_TEST(test_arena_copy_str);

    printf("\n=== SV (String View) ===\n");
    RUN_TEST(test_sv_from_cstr);
    RUN_TEST(test_sv_eq);
    RUN_TEST(test_sv_eq_cstr);
    RUN_TEST(test_sv_starts_with);
    RUN_TEST(test_sv_advance);
    RUN_TEST(test_sv_lit_macro);

    printf("\n=== DA (Dynamic Array) ===\n");
    RUN_TEST(test_da_push_and_access);
    RUN_TEST(test_da_grows);
    RUN_TEST(test_da_pop);
    RUN_TEST(test_da_last);
    RUN_TEST(test_da_clear);
    RUN_TEST(test_da_structs);

    return TEST_RESULTS();
}
