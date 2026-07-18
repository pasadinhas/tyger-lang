#pragma once

#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

// Arena — bump (linear) allocator.
//
// Memory is handed out from fixed-size blocks. Individual allocations
// are never freed; the entire arena is released at once via arena_free().
// All AST nodes are allocated from a single Arena whose lifetime matches
// the compilation unit.
//
// Usage:
//   Arena arena = {0};
//   MyNode *n = (MyNode *)arena_alloc(&arena, sizeof(MyNode), alignof(MyNode));
//   arena_free(&arena);

#define ARENA_DEFAULT_BLOCK_SIZE (1024 * 1024)  // 1 MiB

typedef struct ArenaBlock {
    struct ArenaBlock *next;
    char              *cursor;  // next free byte within this block
    char              *end;     // one past the last usable byte
    // block data follows immediately in memory
} ArenaBlock;

typedef struct {
    ArenaBlock *head;
    size_t      block_size;
} Arena;

// Initialise arena with a given block size (pass 0 for the default).
static inline void arena_init(Arena *a, size_t block_size) {
    a->head       = NULL;
    a->block_size = block_size == 0 ? ARENA_DEFAULT_BLOCK_SIZE : block_size;
}

// Internal: allocate a new block of at least `min_size` bytes.
static inline void arena__new_block(Arena *a, size_t min_size) {
    size_t size = min_size > a->block_size ? min_size : a->block_size;
    void *mem   = malloc(sizeof(ArenaBlock) + size);
    assert(mem && "arena: out of memory");
    ArenaBlock *b = (ArenaBlock *)mem;
    b->next   = a->head;
    b->cursor = (char *)b + sizeof(ArenaBlock);
    b->end    = b->cursor + size;
    a->head   = b;
}

// Allocate `size` bytes with `alignment`. Returns zeroed memory.
static inline void *arena_alloc(Arena *a, size_t size, size_t alignment) {
    assert(alignment > 0 && (alignment & (alignment - 1)) == 0
           && "alignment must be a power of two");

    if (a->head == NULL) {
        arena__new_block(a, 0);
    }

    uintptr_t cursor  = (uintptr_t)a->head->cursor;
    uintptr_t aligned = (cursor + alignment - 1) & ~(uintptr_t)(alignment - 1);
    uintptr_t end     = (uintptr_t)a->head->end;

    if (aligned + size > end) {
        arena__new_block(a, size + alignment);
        cursor  = (uintptr_t)a->head->cursor;
        aligned = (cursor + alignment - 1) & ~(uintptr_t)(alignment - 1);
    }

    a->head->cursor = (char *)(aligned + size);
    void *ptr = (void *)aligned;
    memset(ptr, 0, size);
    return ptr;
}

// Convenience macro: allocate and zero a single T from the arena.
// Returns a T* already cast to the correct type.
#define arena_alloc_t(a, T) ((T *)arena_alloc((a), sizeof(T), _Alignof(T)))

// Copy a string of `len` bytes into the arena (null-terminates it).
static inline const char *arena_copy_str(Arena *a, const char *s, size_t len) {
    char *dest = (char *)arena_alloc(a, len + 1, 1);
    memcpy(dest, s, len);
    dest[len] = '\0';
    return dest;
}

// Free all blocks. The arena struct itself is not freed (caller owns it).
static inline void arena_free(Arena *a) {
    ArenaBlock *b = a->head;
    while (b) {
        ArenaBlock *next = b->next;
        free(b);
        b = next;
    }
    a->head = NULL;
}
