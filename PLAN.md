# Tyger Compiler — C++ Rewrite Plan

## Problem Statement

Rewrite the Tyger language compiler from TypeScript to C++, using LLVM's `IRBuilder` API instead of raw IR string emission. The C++ implementation lives in `tygerc/` and will eventually replace the TypeScript version and serve as the foundation for bootstrapping (the compiler written in Tyger itself).

## Requirements

- C++ compiler implementation in the `tygerc/` subdirectory
- Plain Makefile build system using `llvm-config` for flags
- Arena allocator for AST node memory management
- Full port of what currently exists: lexer, parser, typechecker, LLVM codegen
- No interpreter (deferred)
- No structs/methods (out of scope for now)
- Dependency-free except system libraries and LLVM
- Result types / return codes for error handling — no exceptions
- Hand-rolled tests in plain C++ (no test frameworks)
- Incremental delivery — each task produces working, runnable output

## Language Features to Port

From the current TypeScript implementation:

- **Token types:** keywords (`let`, `mut`, `fn`, `extern`, `if`, `else`, `return`, `true`, `false`), operators (`+`, `-`, `*`, `/`, `%`, comparison, assignment variants), punctuation, `...`, `->`, identifiers, numbers, strings, EOF
- **AST nodes:** Program, VariableDeclaration, FunctionDeclaration, ExternalFunctionDeclaration, BlockStatement, ReturnStatement, IfStatement, AssignmentExpression, BinaryExpression, CallExpression, Identifier, NumericLiteral, StringLiteral, BooleanLiteral
- **Type system:** signed/unsigned ints (i8–i128), floats (f32/f64), boolean, string, ptr, function, external function (variadic)
- **Typechecker:** two-pass (hoist functions first), scope stack, return type checking, binary operator coercion, assignment compatibility
- **Codegen:** LLVM `IRBuilder`-based, replacing the current string-emission approach

## Style

- **C-style throughout:** plain structs + free functions, no classes, no constructors/destructors
- **`da.h`** — macro-based typed dynamic array: `DA(T)` expands to `struct { T *data; size_t len; size_t cap; }`. Helpers: `da_push`, `da_free`, `da_init`
- **`sv.h`** — string view: `struct SV { const char *data; size_t len; }`. Helpers: `sv_eq`, `sv_starts_with`, `sv_from_cstr`, `sv_from_parts`
- **`arena.h`** — bump allocator as a plain struct with `arena_init`, `arena_alloc`, `arena_free`, `arena_copy_str`
- **LLVM API** is unavoidably C++ (methods, templates) — isolated to `codegen.cpp`
- `std::string` avoided except for LLVM interop in codegen; error messages use arena-allocated C strings

## Directory Structure

```
tygerc/
├── Makefile
├── src/
│   ├── main.cpp
│   ├── da.h                      ← macro-based typed dynamic array
│   ├── sv.h                      ← string view
│   ├── arena.h / arena.cpp       ← bump allocator
│   ├── lexer.h / lexer.cpp
│   ├── ast.h                     ← AST node structs
│   ├── parser.h / parser.cpp
│   ├── types.h / types.cpp       ← Type system
│   ├── typechecker.h / typechecker.cpp
│   └── codegen.h / codegen.cpp   ← LLVM IRBuilder
└── test/
    ├── test_runner.h             ← minimal assert macros
    ├── test_lexer.cpp
    ├── test_parser.cpp
    ├── test_typechecker.cpp
    └── test_codegen.cpp
```

The AST uses a tagged union / base struct approach with an arena allocator. Each AST node is allocated from the arena; the whole tree is freed at once after codegen.

## Pipeline

```
Source File → Lexer (Token stream) → Parser (AST + Arena) → Typechecker (Type-annotated AST) → Codegen (LLVM IRBuilder) → .o / binary
```

---

## Task Breakdown

### Task 1: Project scaffold and Makefile

- **Objective:** Create the `tygerc/` directory with a working Makefile that compiles a `main.cpp` stub
- **Implementation:** Set up `tygerc/Makefile` using `llvm-config --cxxflags --ldflags --libs` to get correct flags. `main.cpp` reads a filename argument and prints it. No LLVM calls yet — just prove the build works
- **Test requirements:** `make` succeeds, `./tygerc examples/hello.ty` prints the filename
- **Demo:** `make` in `tygerc/` produces a binary that can be invoked

### Task 2: Arena allocator

- **Objective:** Implement a simple bump allocator used for all AST node allocation
- **Implementation:** `arena.h/cpp` — a growable block allocator with `alloc<T>(args...)` template method. Nodes are never individually freed; the whole arena is destroyed at end of compilation
- **Test requirements:** `test/test_arena.cpp` — allocate various types, verify values are readable, verify alignment
- **Demo:** Test binary runs and passes all arena allocation assertions

### Task 3: Lexer

- **Objective:** Port the TypeScript lexer to C++, producing the same token stream
- **Implementation:** `lexer.h/cpp` — `TokenType` enum, `Token` struct `{TokenType type; std::string value;}`, `lex(std::string_view src)` returning `std::vector<Token>`. Match the same keyword/operator/identifier/number/string logic including escape sequences
- **Test requirements:** `test/test_lexer.cpp` — one test per token type (mirrors the TypeScript single-token tests), plus a multi-token source file test
- **Demo:** Test binary lexes all token types correctly and handles the existing `.ty` example files

### Task 4: AST node definitions

- **Objective:** Define all AST node types in C++ using arena-allocated structs
- **Implementation:** `ast.h` — `NodeKind` enum, base `Node` struct with a `kind` field. Each node type is a plain struct (no vtable) with the same fields as the TypeScript interfaces. Cast via `node->kind`. `Expression` nodes carry a `Type*` field (nullable until typechecked). No heap allocation — all nodes come from the arena. Include a `node_to_string()` debug printer to aid test output
- **Test requirements:** No separate test — validated implicitly by parser tests in Task 5
- **Demo:** N/A (integrated into Task 5)

### Task 5: Parser

- **Objective:** Port the recursive descent parser to C++, producing a well-formed AST
- **Implementation:** `parser.h/cpp` — `parse(std::vector<Token>&, Arena&)` returning `Program*`. Same recursive descent structure: `parseStatement`, `parseExpression`, precedence chain down to `parsePrimary`. All nodes allocated from the arena. Errors reported via `Result<T>` return type with an error string
- **Test requirements:** `test/test_parser.cpp` — parse each `.ty` example file, assert on the shape of the resulting AST (node kinds, identifier names, operator strings). Include a negative test for a known syntax error
- **Demo:** Parser successfully builds an AST from `examples/factorial.ty` and the debug printer outputs a recognisable tree

### Task 6: Type system

- **Objective:** Port the `Type` tagged union and all type utilities to C++
- **Implementation:** `types.h/cpp` — `TypeKind` enum, `Type` struct with a union for `int` (signed, bits), `float` (bits), `function` (param list + return), `external_function` (same + variadic flag). Implement `type_from_string`, `type_to_string`, `coerce_types`, `is_assignable`. Types are arena-allocated or stored as static singletons for primitives
- **Test requirements:** `test/test_types.cpp` — verify `type_from_string` round-trips, verify `coerce_types` for int/float combinations, verify `is_assignable` edge cases (sign widening, float widening)
- **Demo:** All type utility tests pass

### Task 7: Typechecker

- **Objective:** Port the typechecker with scope stack and two-pass function hoisting
- **Implementation:** `typechecker.h/cpp` — `Context` class with a `std::vector<Scope>` scope stack. `typecheck(Node*, Context&)` dispatches on `node->kind`. First pass hoists all `FunctionDeclaration` and `ExternalFunctionDeclaration` into global scope. Return type collection per function. Errors returned as `Result<void>` with descriptive messages
- **Test requirements:** `test/test_typechecker.cpp` — typecheck all example `.ty` files and assert no errors; craft three negative tests: undeclared identifier, type mismatch in assignment, wrong argument count
- **Demo:** Typechecker accepts all current `.ty` examples and rejects the invalid cases with clear error messages

### Task 8: LLVM codegen with IRBuilder

- **Objective:** Replace the raw IR string emitter with proper LLVM `IRBuilder`-based codegen
- **Implementation:** `codegen.h/cpp` — `Codegen` class wrapping `llvm::LLVMContext`, `llvm::Module`, `llvm::IRBuilder<>`. Walk the type-annotated AST, emit values via `IRBuilder`. Map Tyger types to `llvm::Type*` via a helper. Use `CreateAlloca` + `CreateStore`/`CreateLoad` for variables (the alloca trick). Handle: functions, extern declarations, arithmetic/comparison binary ops, if/else (basic blocks + `CreateBr`/`CreateCondBr`), call expressions, string globals (`CreateGlobalStringPtr`)
- **Test requirements:** `test/test_codegen.cpp` — compile `factorial.ty` and `printf.ty`, run the output, assert on stdout (invoke the binary as a subprocess and check the result)
- **Demo:** `./tygerc examples/factorial.ty` produces and executes correct output: `factorial(5) = 120`

### Task 9: Wire up `main.cpp` and end-to-end test

- **Objective:** Connect all stages in `main.cpp` and verify the full pipeline on all example files
- **Implementation:** `main.cpp` reads the source file, runs lex → parse → typecheck → codegen → emit `.o` and invoke `clang` to link. Print errors to stderr with a non-zero exit code. Add a `make test` target that runs all test binaries and the end-to-end examples
- **Test requirements:** `make test` passes all unit tests and end-to-end examples without manual steps
- **Demo:** A single `make && make test` from `tygerc/` compiles the compiler and validates it against all example `.ty` files

---

## Future Work (out of scope for this rewrite)

- Interpreter / REPL
- Structs, methods, `mut` parameters
- Bootstrapping: rewriting this compiler in Tyger itself
