#include "types.h"
#include <cstdio>
#include <cstring>

// ---------------------------------------------------------------------------
// Primitive singletons
// ---------------------------------------------------------------------------

static Type make_int_type(bool signed_, uint8_t bits) {
    Type t;
    t.kind            = TY_INT;
    t.integer.signed_ = signed_;
    t.integer.bits    = bits;
    return t;
}

static Type make_float_type(uint8_t bits) {
    Type t;
    t.kind          = TY_FLOAT;
    t.floating.bits = bits;
    return t;
}

static Type make_primitive(TypeKind kind) {
    Type t;
    t.kind = kind;
    return t;
}

// Storage for all primitives
static Type s_bool   = make_primitive(TY_BOOL);
static Type s_string = make_primitive(TY_STRING);
static Type s_ptr    = make_primitive(TY_PTR);

static Type s_i8   = make_int_type(true,  8);
static Type s_i16  = make_int_type(true,  16);
static Type s_i32  = make_int_type(true,  32);
static Type s_i64  = make_int_type(true,  64);
static Type s_i128 = make_int_type(true,  128);

static Type s_u8   = make_int_type(false, 8);
static Type s_u16  = make_int_type(false, 16);
static Type s_u32  = make_int_type(false, 32);
static Type s_u64  = make_int_type(false, 64);
static Type s_u128 = make_int_type(false, 128);

static Type s_f32  = make_float_type(32);
static Type s_f64  = make_float_type(64);

// Public pointers
Type *TY_BOOL_SINGLETON   = &s_bool;
Type *TY_STRING_SINGLETON = &s_string;
Type *TY_PTR_SINGLETON    = &s_ptr;

Type *TY_I8   = &s_i8;
Type *TY_I16  = &s_i16;
Type *TY_I32  = &s_i32;
Type *TY_I64  = &s_i64;
Type *TY_I128 = &s_i128;

Type *TY_U8   = &s_u8;
Type *TY_U16  = &s_u16;
Type *TY_U32  = &s_u32;
Type *TY_U64  = &s_u64;
Type *TY_U128 = &s_u128;

Type *TY_F32  = &s_f32;
Type *TY_F64  = &s_f64;

// ---------------------------------------------------------------------------
// type_kind_name
// ---------------------------------------------------------------------------

const char *type_kind_name(TypeKind kind) {
    static_assert(TY_COUNT == 8, "type_kind_name: TypeKind changed, update this switch");
    switch (kind) {
        case TY_INT:          return "int";
        case TY_FLOAT:        return "float";
        case TY_BOOL:         return "bool";
        case TY_STRING:       return "string";
        case TY_PTR:          return "ptr";
        case TY_FUNCTION:     return "function";
        case TY_EXT_FUNCTION: return "ext_function";
        case TY_STRUCT:       return "struct";
        case TY_COUNT:        break;
    }
    return "<unknown>";
}

// ---------------------------------------------------------------------------
// type_from_sv
// ---------------------------------------------------------------------------

Type *type_from_sv(SV name) {
    // Bool / string / ptr
    if (sv_eq_cstr(name, "boolean")) return TY_BOOL_SINGLETON;
    if (sv_eq_cstr(name, "string"))  return TY_STRING_SINGLETON;
    if (sv_eq_cstr(name, "ptr"))     return TY_PTR_SINGLETON;

    // Signed ints
    if (sv_eq_cstr(name, "i8"))   return TY_I8;
    if (sv_eq_cstr(name, "i16"))  return TY_I16;
    if (sv_eq_cstr(name, "i32"))  return TY_I32;
    if (sv_eq_cstr(name, "i64"))  return TY_I64;
    if (sv_eq_cstr(name, "i128")) return TY_I128;

    // Unsigned ints
    if (sv_eq_cstr(name, "u8"))   return TY_U8;
    if (sv_eq_cstr(name, "u16"))  return TY_U16;
    if (sv_eq_cstr(name, "u32"))  return TY_U32;
    if (sv_eq_cstr(name, "u64"))  return TY_U64;
    if (sv_eq_cstr(name, "u128")) return TY_U128;

    // Floats
    if (sv_eq_cstr(name, "f32")) return TY_F32;
    if (sv_eq_cstr(name, "f64")) return TY_F64;

    return NULL;
}

Type *type_from_cstr(const char *name) {
    return type_from_sv(sv_from_cstr(name));
}

// ---------------------------------------------------------------------------
// type_to_string
// ---------------------------------------------------------------------------

const char *type_to_string(const Type *type, Arena *arena) {
    static_assert(TY_COUNT == 8, "type_to_string: TypeKind changed, update this switch");

    if (!type) return "<null>";

    char buf[256];

    switch (type->kind) {
        case TY_BOOL:   return "boolean";
        case TY_STRING: return "string";
        case TY_PTR:    return "ptr";

        case TY_INT:
            snprintf(buf, sizeof(buf), "%c%d",
                     type->integer.signed_ ? 'i' : 'u',
                     (int)type->integer.bits);
            return arena_copy_str(arena, buf, strlen(buf));

        case TY_FLOAT:
            snprintf(buf, sizeof(buf), "f%d", (int)type->floating.bits);
            return arena_copy_str(arena, buf, strlen(buf));

        case TY_FUNCTION: {
            // (i64, i64) -> i64
            int pos = 0;
            buf[pos++] = '(';
            for (size_t i = 0; i < type->function.params.len; i++) {
                if (i > 0) { buf[pos++] = ','; buf[pos++] = ' '; }
                const char *p = type_to_string(type->function.params.data[i], arena);
                size_t plen = strlen(p);
                if (pos + (int)plen < (int)sizeof(buf) - 8) {
                    memcpy(buf + pos, p, plen);
                    pos += (int)plen;
                }
            }
            const char *ret = type_to_string(type->function.ret, arena);
            snprintf(buf + pos, sizeof(buf) - (size_t)pos, ") -> %s", ret);
            return arena_copy_str(arena, buf, strlen(buf));
        }

        case TY_EXT_FUNCTION: {
            int pos = 0;
            buf[pos++] = '(';
            for (size_t i = 0; i < type->ext_function.params.len; i++) {
                if (i > 0) { buf[pos++] = ','; buf[pos++] = ' '; }
                const char *p = type_to_string(type->ext_function.params.data[i], arena);
                size_t plen = strlen(p);
                if (pos + (int)plen < (int)sizeof(buf) - 16) {
                    memcpy(buf + pos, p, plen);
                    pos += (int)plen;
                }
            }
            if (type->ext_function.variadic) {
                if (type->ext_function.params.len > 0) { buf[pos++] = ','; buf[pos++] = ' '; }
                memcpy(buf + pos, "...", 3); pos += 3;
            }
            const char *ret = type_to_string(type->ext_function.ret, arena);
            snprintf(buf + pos, sizeof(buf) - (size_t)pos, ") -> %s", ret);
            return arena_copy_str(arena, buf, strlen(buf));
        }

        case TY_STRUCT:
            return arena_copy_str(arena, type->struct_.name.data, type->struct_.name.len);

        case TY_COUNT: break;
    }
    return "<unknown>";
}

// ---------------------------------------------------------------------------
// types_equal
// ---------------------------------------------------------------------------

bool types_equal(const Type *a, const Type *b) {
    if (a == b) return true; // same singleton or pointer
    if (!a || !b) return false;
    if (a->kind != b->kind) return false;

    static_assert(TY_COUNT == 8, "types_equal: TypeKind changed, update this switch");

    switch (a->kind) {
        case TY_BOOL:
        case TY_STRING:
        case TY_PTR:
            return true; // singletons, already handled by pointer equality above
        case TY_INT:
            return a->integer.signed_ == b->integer.signed_ &&
                   a->integer.bits   == b->integer.bits;
        case TY_FLOAT:
            return a->floating.bits == b->floating.bits;
        case TY_FUNCTION: {
            if (a->function.params.len != b->function.params.len) return false;
            if (!types_equal(a->function.ret, b->function.ret)) return false;
            for (size_t i = 0; i < a->function.params.len; i++) {
                if (!types_equal(a->function.params.data[i], b->function.params.data[i]))
                    return false;
                bool a_mut = i < a->function.mut_params.len && a->function.mut_params.data[i];
                bool b_mut = i < b->function.mut_params.len && b->function.mut_params.data[i];
                if (a_mut != b_mut) return false;
            }
            return true;
        }
        case TY_EXT_FUNCTION: {
            if (a->ext_function.variadic != b->ext_function.variadic) return false;
            if (a->ext_function.params.len != b->ext_function.params.len) return false;
            if (!types_equal(a->ext_function.ret, b->ext_function.ret)) return false;
            for (size_t i = 0; i < a->ext_function.params.len; i++) {
                if (!types_equal(a->ext_function.params.data[i], b->ext_function.params.data[i]))
                    return false;
            }
            return true;
        }
        case TY_STRUCT:
            // Struct identity is by name (nominal typing)
            return sv_eq(a->struct_.name, b->struct_.name);
        case TY_COUNT: break;
    }
    return false;
}

// ---------------------------------------------------------------------------
// coerce_types
// Port of TypeScript coerceTypes: given two operand types, return the result
// type for a binary arithmetic operator, or NULL if incompatible.
// ---------------------------------------------------------------------------

Type *coerce_types(Type *left, Type *right) {
    // Same type pointer — trivially compatible
    if (left == right) return left;
    if (!left || !right) return NULL;

    // float + float → wider float
    if (left->kind == TY_FLOAT && right->kind == TY_FLOAT) {
        return left->floating.bits >= right->floating.bits ? left : right;
    }

    // int + int, same signedness → wider
    if (left->kind == TY_INT && right->kind == TY_INT &&
        left->integer.signed_ == right->integer.signed_) {
        return left->integer.bits >= right->integer.bits ? left : right;
    }

    // int + int, different signedness → not allowed (NULL)
    if (left->kind == TY_INT && right->kind == TY_INT &&
        left->integer.signed_ != right->integer.signed_) {
        return NULL;
    }

    // float + int or int + float → wider float
    if ((left->kind == TY_FLOAT && right->kind == TY_INT) ||
        (left->kind == TY_INT  && right->kind == TY_FLOAT)) {
        Type *flt  = (left->kind  == TY_FLOAT) ? left  : right;
        Type *intg = (left->kind  == TY_INT)   ? left  : right;
        return flt->floating.bits >= intg->integer.bits ? flt : intg;
    }

    return NULL;
}

// ---------------------------------------------------------------------------
// is_assignable
// Port of TypeScript isAssignable.
// ---------------------------------------------------------------------------

bool is_assignable(const Type *target, const Type *value) {
    if (target == value) return true;
    if (!target || !value) return false;

    // Float target: can accept float of same/smaller bits, or int of smaller bits
    if (target->kind == TY_FLOAT) {
        if (value->kind == TY_FLOAT && target->floating.bits >= value->floating.bits)
            return true;
        if (value->kind == TY_INT && target->floating.bits >= value->integer.bits)
            return true;
        return false;
    }

    // Int target: same signedness and target is wider or equal
    if (target->kind == TY_INT && value->kind == TY_INT) {
        if (target->integer.signed_ == value->integer.signed_ &&
            target->integer.bits >= value->integer.bits)
            return true;
        // signed target with strictly wider bit-width can accept unsigned value
        if (target->integer.signed_ && !value->integer.signed_ &&
            target->integer.bits > value->integer.bits)
            return true;
        return false;
    }

    // ptr accepts ptr (pointer equality already handled above, but be explicit)
    if (target->kind == TY_PTR && value->kind == TY_PTR) return true;

    // Otherwise require structural equality
    return types_equal(target, value);
}

// ---------------------------------------------------------------------------
// make_function_type / make_ext_function_type
// ---------------------------------------------------------------------------

Type *make_function_type(Arena *arena, TypeList params, BoolList mut_params, Type *ret) {
    Type *t = arena_alloc_t(arena, Type);
    t->kind                   = TY_FUNCTION;
    t->function.params        = params;
    t->function.mut_params    = mut_params;
    t->function.ret           = ret;
    return t;
}

Type *make_ext_function_type(Arena *arena, TypeList params, Type *ret, bool variadic) {
    Type *t = arena_alloc_t(arena, Type);
    t->kind                  = TY_EXT_FUNCTION;
    t->ext_function.params   = params;
    t->ext_function.ret      = ret;
    t->ext_function.variadic = variadic;
    return t;
}

Type *make_struct_type(Arena *arena, SV name, StructTypeFieldList fields) {
    Type *t = arena_alloc_t(arena, Type);
    t->kind           = TY_STRUCT;
    t->struct_.name   = name;
    t->struct_.fields = fields;
    return t;
}

int struct_field_index(const Type *t, SV field_name) {
    for (size_t i = 0; i < t->struct_.fields.len; i++) {
        if (sv_eq(t->struct_.fields.data[i].name, field_name))
            return (int)i;
    }
    return -1;
}

Type *struct_field_type(const Type *t, int index) {
    return t->struct_.fields.data[index].type;
}
