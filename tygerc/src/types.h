#pragma once

#include "sv.h"
#include "da.h"
#include "arena.h"
#include <stdint.h>
#include <stdbool.h>

// ---------------------------------------------------------------------------
// TypeKind
// ---------------------------------------------------------------------------

typedef enum {
    TY_INT,
    TY_FLOAT,
    TY_BOOL,
    TY_STRING,
    TY_PTR,
    TY_FUNCTION,
    TY_EXT_FUNCTION,
    TY_STRUCT,

    TY_COUNT, // must remain last
} TypeKind;

const char *type_kind_name(TypeKind kind);

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

typedef DA(struct Type *) TypeList;
typedef DA(bool) BoolList;

// A single field in a struct type
typedef struct {
    SV             name;
    struct Type   *type;
} StructTypeField;

typedef DA(StructTypeField) StructTypeFieldList;

typedef struct Type {
    TypeKind kind;
    union {
        struct {
            bool    signed_;
            uint8_t bits;
        } integer;
        struct {
            uint8_t bits;
        } floating;
        struct {
            TypeList     params;
            BoolList     mut_params; // parallel to params: true if that param is mut
            struct Type *ret;
        } function;
        struct {
            TypeList  params;
            struct Type *ret;
            bool      variadic;
        } ext_function;
        struct {
            SV                  name;
            StructTypeFieldList fields;
        } struct_;
    };
} Type;

// ---------------------------------------------------------------------------
// Primitive singletons — use these pointers everywhere for primitives.
// They are statically allocated; never free them.
// ---------------------------------------------------------------------------

extern Type *TY_BOOL_SINGLETON;
extern Type *TY_STRING_SINGLETON;
extern Type *TY_PTR_SINGLETON;

// Signed ints
extern Type *TY_I8;
extern Type *TY_I16;
extern Type *TY_I32;
extern Type *TY_I64;
extern Type *TY_I128;

// Unsigned ints
extern Type *TY_U8;
extern Type *TY_U16;
extern Type *TY_U32;
extern Type *TY_U64;
extern Type *TY_U128;

// Floats
extern Type *TY_F32;
extern Type *TY_F64;

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

// Parse a type name string into a Type*.
// Primitives return a singleton pointer (no allocation).
// Returns NULL if the string is not a recognised type.
Type *type_from_sv(SV name);
Type *type_from_cstr(const char *name);

// Render a type as a human-readable string, arena-allocated.
const char *type_to_string(const Type *type, Arena *arena);

// Structural equality.
bool types_equal(const Type *a, const Type *b);

// Given two types on either side of a binary operator, return the result type.
// Returns NULL if the combination is not valid.
Type *coerce_types(Type *left, Type *right);

// Can a value of type `value` be assigned to a slot of type `target`?
bool is_assignable(const Type *target, const Type *value);

// Allocate a function type from the arena.
Type *make_function_type(Arena *arena, TypeList params, BoolList mut_params, Type *ret);

// Allocate an external function type from the arena.
Type *make_ext_function_type(Arena *arena, TypeList params, Type *ret, bool variadic);

// Allocate a struct type from the arena.
Type *make_struct_type(Arena *arena, SV name, StructTypeFieldList fields);

// Look up a field by name in a struct type. Returns the field index, or -1 if not found.
int struct_field_index(const Type *struct_type, SV field_name);

// Get a field's type by index (no bounds check).
Type *struct_field_type(const Type *struct_type, int index);
