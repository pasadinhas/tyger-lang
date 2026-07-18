#include "test_runner.h"
#include "types.h"
#include "arena.h"

static Arena g_arena;

// ---------------------------------------------------------------------------
// type_from_sv / type_from_cstr
// ---------------------------------------------------------------------------

static void test_from_string_bool() {
    ASSERT(type_from_cstr("boolean") == TY_BOOL_SINGLETON);
}

static void test_from_string_string() {
    ASSERT(type_from_cstr("string") == TY_STRING_SINGLETON);
}

static void test_from_string_ptr() {
    ASSERT(type_from_cstr("ptr") == TY_PTR_SINGLETON);
}

static void test_from_string_signed_ints() {
    ASSERT(type_from_cstr("i8")   == TY_I8);
    ASSERT(type_from_cstr("i16")  == TY_I16);
    ASSERT(type_from_cstr("i32")  == TY_I32);
    ASSERT(type_from_cstr("i64")  == TY_I64);
    ASSERT(type_from_cstr("i128") == TY_I128);
}

static void test_from_string_unsigned_ints() {
    ASSERT(type_from_cstr("u8")   == TY_U8);
    ASSERT(type_from_cstr("u16")  == TY_U16);
    ASSERT(type_from_cstr("u32")  == TY_U32);
    ASSERT(type_from_cstr("u64")  == TY_U64);
    ASSERT(type_from_cstr("u128") == TY_U128);
}

static void test_from_string_floats() {
    ASSERT(type_from_cstr("f32") == TY_F32);
    ASSERT(type_from_cstr("f64") == TY_F64);
}

static void test_from_string_unknown() {
    ASSERT(type_from_cstr("banana") == NULL);
    ASSERT(type_from_cstr("") == NULL);
}

// ---------------------------------------------------------------------------
// type_to_string round-trips
// ---------------------------------------------------------------------------

static void test_to_string_primitives() {
    ASSERT_STR_EQ("boolean", type_to_string(TY_BOOL_SINGLETON, &g_arena));
    ASSERT_STR_EQ("string",  type_to_string(TY_STRING_SINGLETON, &g_arena));
    ASSERT_STR_EQ("ptr",     type_to_string(TY_PTR_SINGLETON, &g_arena));
}

static void test_to_string_ints() {
    ASSERT_STR_EQ("i8",   type_to_string(TY_I8,   &g_arena));
    ASSERT_STR_EQ("i64",  type_to_string(TY_I64,  &g_arena));
    ASSERT_STR_EQ("u32",  type_to_string(TY_U32,  &g_arena));
    ASSERT_STR_EQ("u128", type_to_string(TY_U128, &g_arena));
}

static void test_to_string_floats() {
    ASSERT_STR_EQ("f32", type_to_string(TY_F32, &g_arena));
    ASSERT_STR_EQ("f64", type_to_string(TY_F64, &g_arena));
}

static void test_to_string_function() {
    TypeList params = {0};
    da_push(&params, TY_I64);
    da_push(&params, TY_I64);
    Type *fn = make_function_type(&g_arena, params, TY_I64);
    ASSERT_STR_EQ("(i64, i64) -> i64", type_to_string(fn, &g_arena));
}

static void test_to_string_ext_function_variadic() {
    TypeList params = {0};
    da_push(&params, TY_PTR_SINGLETON);
    Type *fn = make_ext_function_type(&g_arena, params, TY_I32, true);
    ASSERT_STR_EQ("(ptr, ...) -> i32", type_to_string(fn, &g_arena));
}

static void test_round_trip() {
    // Parse → print → compare
    const char *names[] = { "i8","i16","i32","i64","i128",
                             "u8","u16","u32","u64","u128",
                             "f32","f64","boolean","string","ptr" };
    for (size_t i = 0; i < sizeof(names)/sizeof(names[0]); i++) {
        Type *t = type_from_cstr(names[i]);
        ASSERT_NOT_NULL(t);
        const char *s = type_to_string(t, &g_arena);
        ASSERT_STR_EQ(names[i], s);
    }
}

// ---------------------------------------------------------------------------
// types_equal
// ---------------------------------------------------------------------------

static void test_equal_same_pointer() {
    ASSERT(types_equal(TY_I64, TY_I64));
    ASSERT(types_equal(TY_BOOL_SINGLETON, TY_BOOL_SINGLETON));
}

static void test_not_equal_different_kind() {
    ASSERT(!types_equal(TY_I64, TY_F64));
    ASSERT(!types_equal(TY_I64, TY_BOOL_SINGLETON));
}

static void test_not_equal_different_bits() {
    ASSERT(!types_equal(TY_I32, TY_I64));
    ASSERT(!types_equal(TY_F32, TY_F64));
}

static void test_not_equal_different_signedness() {
    ASSERT(!types_equal(TY_I64, TY_U64));
}

// ---------------------------------------------------------------------------
// coerce_types
// ---------------------------------------------------------------------------

static void test_coerce_same() {
    ASSERT(coerce_types(TY_I64, TY_I64) == TY_I64);
    ASSERT(coerce_types(TY_F64, TY_F64) == TY_F64);
}

static void test_coerce_int_widens() {
    // i32 + i64 → i64
    Type *result = coerce_types(TY_I32, TY_I64);
    ASSERT(result == TY_I64);
}

static void test_coerce_float_widens() {
    // f32 + f64 → f64
    Type *result = coerce_types(TY_F32, TY_F64);
    ASSERT(result == TY_F64);
}

static void test_coerce_int_float() {
    // i32 + f64 → f64
    Type *result = coerce_types(TY_I32, TY_F64);
    ASSERT(result == TY_F64);
}

static void test_coerce_different_signedness_fails() {
    // i64 + u64 → NULL (not allowed)
    ASSERT(coerce_types(TY_I64, TY_U64) == NULL);
}

static void test_coerce_incompatible_fails() {
    ASSERT(coerce_types(TY_I64, TY_BOOL_SINGLETON) == NULL);
    ASSERT(coerce_types(TY_STRING_SINGLETON, TY_I64) == NULL);
}

// ---------------------------------------------------------------------------
// is_assignable
// ---------------------------------------------------------------------------

static void test_assignable_same_type() {
    ASSERT(is_assignable(TY_I64, TY_I64));
    ASSERT(is_assignable(TY_BOOL_SINGLETON, TY_BOOL_SINGLETON));
}

static void test_assignable_int_widening() {
    // i32 can receive i16
    ASSERT(is_assignable(TY_I32, TY_I16));
    // i64 can receive i32
    ASSERT(is_assignable(TY_I64, TY_I32));
    // i16 cannot receive i64
    ASSERT(!is_assignable(TY_I16, TY_I64));
}

static void test_assignable_float_widening() {
    ASSERT(is_assignable(TY_F64, TY_F32));
    ASSERT(!is_assignable(TY_F32, TY_F64));
}

static void test_assignable_float_receives_int() {
    ASSERT(is_assignable(TY_F64, TY_I32));
    ASSERT(!is_assignable(TY_I64, TY_F32));
}

static void test_assignable_signed_to_wider_signed() {
    // i64 can receive u32 (signed target, strictly wider)
    ASSERT(is_assignable(TY_I64, TY_U32));
    // i32 cannot receive u32 (not strictly wider)
    ASSERT(!is_assignable(TY_I32, TY_U32));
}

static void test_assignable_incompatible() {
    ASSERT(!is_assignable(TY_I64, TY_BOOL_SINGLETON));
    ASSERT(!is_assignable(TY_STRING_SINGLETON, TY_PTR_SINGLETON));
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

int main(void) {
    arena_init(&g_arena, 0);

    printf("=== type_from_string ===\n");
    RUN_TEST(test_from_string_bool);
    RUN_TEST(test_from_string_string);
    RUN_TEST(test_from_string_ptr);
    RUN_TEST(test_from_string_signed_ints);
    RUN_TEST(test_from_string_unsigned_ints);
    RUN_TEST(test_from_string_floats);
    RUN_TEST(test_from_string_unknown);

    printf("\n=== type_to_string ===\n");
    RUN_TEST(test_to_string_primitives);
    RUN_TEST(test_to_string_ints);
    RUN_TEST(test_to_string_floats);
    RUN_TEST(test_to_string_function);
    RUN_TEST(test_to_string_ext_function_variadic);
    RUN_TEST(test_round_trip);

    printf("\n=== types_equal ===\n");
    RUN_TEST(test_equal_same_pointer);
    RUN_TEST(test_not_equal_different_kind);
    RUN_TEST(test_not_equal_different_bits);
    RUN_TEST(test_not_equal_different_signedness);

    printf("\n=== coerce_types ===\n");
    RUN_TEST(test_coerce_same);
    RUN_TEST(test_coerce_int_widens);
    RUN_TEST(test_coerce_float_widens);
    RUN_TEST(test_coerce_int_float);
    RUN_TEST(test_coerce_different_signedness_fails);
    RUN_TEST(test_coerce_incompatible_fails);

    printf("\n=== is_assignable ===\n");
    RUN_TEST(test_assignable_same_type);
    RUN_TEST(test_assignable_int_widening);
    RUN_TEST(test_assignable_float_widening);
    RUN_TEST(test_assignable_float_receives_int);
    RUN_TEST(test_assignable_signed_to_wider_signed);
    RUN_TEST(test_assignable_incompatible);

    arena_free(&g_arena);
    return TEST_RESULTS();
}
