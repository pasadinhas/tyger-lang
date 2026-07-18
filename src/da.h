#pragma once

#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

// DA — macro-based typed dynamic array.
//
// Usage:
//   DA(int) nums = {0};
//   da_push(&nums, 42);
//   da_push(&nums, 7);
//   printf("%d\n", nums.data[0]);  // 42
//   da_free(&nums);
//
// The DA(T) macro expands to an anonymous struct:
//   struct { T *data; size_t len; size_t cap; }
//
// All macros work with any DA regardless of element type, since
// they operate on the struct fields directly.

#define DA(T) struct { T *data; size_t len; size_t cap; }

#define DA_INIT_CAP 8

// Push a single item onto the dynamic array, growing if needed.
#define da_push(da, item)                                               \
    do {                                                                \
        if ((da)->len >= (da)->cap) {                                   \
            size_t new_cap = (da)->cap == 0                             \
                ? DA_INIT_CAP                                           \
                : (da)->cap * 2;                                        \
            (da)->data = (decltype((da)->data))realloc(                 \
                (da)->data,                                             \
                new_cap * sizeof(*(da)->data));                         \
            assert((da)->data && "da_push: out of memory");             \
            (da)->cap = new_cap;                                        \
        }                                                               \
        (da)->data[(da)->len++] = (item);                               \
    } while (0)

// Free heap memory. Does not free items if they are pointers.
#define da_free(da)                                                     \
    do {                                                                \
        free((da)->data);                                               \
        (da)->data = NULL;                                              \
        (da)->len  = 0;                                                 \
        (da)->cap  = 0;                                                 \
    } while (0)

// Reset length to zero without freeing memory (reuse capacity).
#define da_clear(da)  ((da)->len = 0)

// Access last element (undefined if empty).
#define da_last(da)   ((da)->data[(da)->len - 1])

// Pop last element (undefined if empty).
#define da_pop(da)    ((da)->data[--(da)->len])

// True if empty.
#define da_empty(da)  ((da)->len == 0)

// Reserve at least `n` total capacity.
#define da_reserve(da, n)                                               \
    do {                                                                \
        if ((da)->cap < (size_t)(n)) {                                  \
            (da)->data = (decltype((da)->data))realloc(                 \
                (da)->data,                                             \
                (size_t)(n) * sizeof(*(da)->data));                     \
            assert((da)->data && "da_reserve: out of memory");          \
            (da)->cap = (size_t)(n);                                    \
        }                                                               \
    } while (0)
