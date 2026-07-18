// codegen.cpp — LLVM IRBuilder-based code generator for Tyger.
//
// This file intentionally uses C++ LLVM API idioms (namespaces, method calls,
// std::string, std::map). Everything else in the compiler is C-style; this
// file is the isolated exception because the LLVM C++ API requires it.

#include "codegen.h"

// Suppress warnings from LLVM headers (not our code)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunused-parameter"
#pragma clang diagnostic ignored "-Wdeprecated-declarations"

#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/IR/IRBuilder.h>
#include <llvm/IR/Verifier.h>
#include <llvm/IR/Function.h>
#include <llvm/IR/Type.h>
#include <llvm/IR/DerivedTypes.h>
#include <llvm/Support/raw_ostream.h>
#include <llvm/Support/FileSystem.h>
#include <llvm/TargetParser/Host.h>

#pragma clang diagnostic pop

#include <map>
#include <string>
#include <cstring>
#include <cstdio>

// ---------------------------------------------------------------------------
// Codegen state
// ---------------------------------------------------------------------------

struct LoopContext {
    llvm::BasicBlock *header; // continue jumps here
    llvm::BasicBlock *exit;   // break jumps here
};

struct Codegen {
    llvm::LLVMContext               ctx;
    llvm::Module                    mod;
    llvm::IRBuilder<>               builder;
    Arena                          *arena;
    char                            errbuf[512];

    std::map<std::string, llvm::AllocaInst *> locals;
    std::vector<LoopContext>                  loop_stack;
    // Maps struct type name → LLVM StructType* (populated by gen_struct_decl)
    std::map<std::string, llvm::StructType *> struct_types;

    Codegen(const char *module_name, Arena *a)
        : ctx(), mod(module_name, ctx), builder(ctx), arena(a) {
        errbuf[0] = '\0';
    }
};

// ---------------------------------------------------------------------------
// Type mapping: Tyger Type* → llvm::Type*
// ---------------------------------------------------------------------------

// Get the LLVM StructType* for a Tyger struct type (must have been declared first)
static llvm::StructType *get_llvm_struct_type(Codegen &cg, const Type *type) {
    std::string name(type->struct_.name.data, type->struct_.name.len);
    auto it = cg.struct_types.find(name);
    if (it != cg.struct_types.end()) return it->second;
    // Shouldn't happen after typechecking, but return opaque struct as fallback
    return llvm::StructType::create(cg.ctx, name);
}

static llvm::Type *llvm_type(Codegen &cg, const Type *type) {
    if (!type) return llvm::Type::getVoidTy(cg.ctx);

    switch (type->kind) {
        case TY_BOOL:   return llvm::Type::getInt1Ty(cg.ctx);
        case TY_STRING: return llvm::PointerType::getUnqual(cg.ctx);
        case TY_PTR:    return llvm::PointerType::getUnqual(cg.ctx);
        case TY_INT:
            switch (type->integer.bits) {
                case 8:   return llvm::Type::getInt8Ty(cg.ctx);
                case 16:  return llvm::Type::getInt16Ty(cg.ctx);
                case 32:  return llvm::Type::getInt32Ty(cg.ctx);
                case 64:  return llvm::Type::getInt64Ty(cg.ctx);
                case 128: return llvm::Type::getInt128Ty(cg.ctx);
                default:  return llvm::Type::getInt64Ty(cg.ctx);
            }
        case TY_FLOAT:
            switch (type->floating.bits) {
                case 32: return llvm::Type::getFloatTy(cg.ctx);
                case 64: return llvm::Type::getDoubleTy(cg.ctx);
                default: return llvm::Type::getDoubleTy(cg.ctx);
            }
        case TY_FUNCTION:
        case TY_EXT_FUNCTION:
            // Functions are referenced by pointer in call expressions
            return llvm::PointerType::getUnqual(cg.ctx);
        case TY_STRUCT:
            // Structs are always passed as pointers
            return llvm::PointerType::getUnqual(cg.ctx);
        case TY_COUNT:
            break;
    }
    return llvm::Type::getInt64Ty(cg.ctx); // fallback
}

// ---------------------------------------------------------------------------
// Forward declarations
// ---------------------------------------------------------------------------

static llvm::Value *gen_node(Codegen &cg, Node *node);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

// Create an alloca in the entry block of the current function.
// Placing allocas in the entry block helps LLVM's mem2reg pass.
static llvm::AllocaInst *create_entry_alloca(Codegen & /*cg*/,
                                              llvm::Function *fn,
                                              llvm::Type *type,
                                              const char *name) {
    llvm::IRBuilder<> entry_builder(&fn->getEntryBlock(),
                                    fn->getEntryBlock().begin());
    return entry_builder.CreateAlloca(type, nullptr, name);
}

// ---------------------------------------------------------------------------
// Statement generators
// ---------------------------------------------------------------------------

static llvm::Value *gen_extern_function_decl(Codegen &cg, AstExternFunctionDecl *node) {
    // Build the LLVM function type
    std::vector<llvm::Type *> param_types;
    for (size_t i = 0; i < node->params.len; i++) {
        Type *pt = type_from_sv(node->params.data[i].type_name);
        param_types.push_back(llvm_type(cg, pt));
    }

    Type *ret_ty = type_from_sv(node->return_type_name);
    llvm::FunctionType *ft = llvm::FunctionType::get(
        llvm_type(cg, ret_ty), param_types, node->is_variadic);

    // Get or insert the declaration
    std::string name(node->name.data, node->name.len);
    llvm::Function::Create(ft, llvm::Function::ExternalLinkage, name, cg.mod);
    return nullptr;
}

static llvm::Value *gen_function_decl(Codegen &cg, AstFunctionDecl *node) {
    // Use the typechecker-annotated function type (stored on node->type)
    // which has already resolved struct types correctly.
    // Fall back to string resolution for primitives if type not annotated.
    Type *fn_tyger_type = node->type; // TY_FUNCTION, set by typechecker first pass

    std::vector<llvm::Type *> param_types;
    for (size_t i = 0; i < node->params.len; i++) {
        Type *pt = NULL;
        if (fn_tyger_type && fn_tyger_type->kind == TY_FUNCTION &&
            i < fn_tyger_type->function.params.len) {
            pt = fn_tyger_type->function.params.data[i];
        }
        if (!pt) pt = type_from_sv(node->params.data[i].type_name);
        param_types.push_back(llvm_type(cg, pt));
    }

    Type *ret_ty = NULL;
    if (fn_tyger_type && fn_tyger_type->kind == TY_FUNCTION)
        ret_ty = fn_tyger_type->function.ret;
    if (!ret_ty) ret_ty = type_from_sv(node->return_type_name);
    llvm::FunctionType *ft = llvm::FunctionType::get(
        llvm_type(cg, ret_ty), param_types, false);

    std::string name(node->name.data, node->name.len);
    llvm::Function *fn = llvm::Function::Create(
        ft, llvm::Function::ExternalLinkage, name, cg.mod);

    // Name the parameters
    size_t idx = 0;
    for (auto &arg : fn->args()) {
        std::string pname(node->params.data[idx].name.data,
                          node->params.data[idx].name.len);
        arg.setName(pname);
        idx++;
    }

    // Create entry basic block
    llvm::BasicBlock *entry_bb = llvm::BasicBlock::Create(cg.ctx, "entry", fn);
    cg.builder.SetInsertPoint(entry_bb);

    // Clear locals for this function
    cg.locals.clear();

    // Alloca trick: spill each param onto the stack so it's addressable/mutable
    idx = 0;
    for (auto &arg : fn->args()) {
        std::string pname(node->params.data[idx].name.data,
                          node->params.data[idx].name.len);

        // Get the Tyger type for this param (prefer annotated, fall back to string)
        Type *param_type = NULL;
        if (fn_tyger_type && fn_tyger_type->kind == TY_FUNCTION &&
            idx < fn_tyger_type->function.params.len) {
            param_type = fn_tyger_type->function.params.data[idx];
        }
        if (!param_type) param_type = type_from_sv(node->params.data[idx].type_name);

        // For struct params, the argument IS already a pointer — store it in a ptr slot
        if (param_type && param_type->kind == TY_STRUCT) {
            llvm::AllocaInst *ptr_slot = create_entry_alloca(
                cg, fn, llvm::PointerType::getUnqual(cg.ctx), (pname + ".ptr").c_str());
            cg.builder.CreateStore(&arg, ptr_slot);
            cg.locals[pname] = ptr_slot;
        } else {
            llvm::AllocaInst *alloca = create_entry_alloca(cg, fn, arg.getType(),
                                                            (pname + ".addr").c_str());
            cg.builder.CreateStore(&arg, alloca);
            cg.locals[pname] = alloca;
        }
        idx++;
    }

    // Generate body
    gen_node(cg, node->body);

    // If the last block has no terminator, add a default return
    llvm::BasicBlock *last_bb = cg.builder.GetInsertBlock();
    if (!last_bb->getTerminator()) {
        if (ft->getReturnType()->isVoidTy()) {
            cg.builder.CreateRetVoid();
        } else {
            cg.builder.CreateRet(llvm::Constant::getNullValue(ft->getReturnType()));
        }
    }

    return fn;
}

static llvm::Value *gen_var_decl(Codegen &cg, AstVarDecl *node) {
    llvm::Function *fn = cg.builder.GetInsertBlock()->getParent();

    llvm::Value *init_val = gen_node(cg, node->init);
    if (!init_val) return nullptr;

    std::string name(node->name.data, node->name.len);

    // For struct variables, the init_val is already a pointer to the struct.
    // Store the pointer in a ptr-sized alloca so gen_identifier can load it.
    if (node->type && node->type->kind == TY_STRUCT) {
        llvm::AllocaInst *ptr_slot = create_entry_alloca(
            cg, fn, llvm::PointerType::getUnqual(cg.ctx), (name + ".ptr").c_str());
        cg.builder.CreateStore(init_val, ptr_slot);
        cg.locals[name] = ptr_slot;
        return ptr_slot;
    }

    llvm::Type *ty = llvm_type(cg, node->type);
    llvm::AllocaInst *alloca = create_entry_alloca(cg, fn, ty, name.c_str());
    cg.builder.CreateStore(init_val, alloca);
    cg.locals[name] = alloca;
    return alloca;
}

static llvm::Value *gen_block(Codegen &cg, AstBlock *node) {
    llvm::Value *last = nullptr;
    for (size_t i = 0; i < node->body.len; i++) {
        last = gen_node(cg, node->body.data[i]);
        // If we just emitted a terminator (e.g. return), stop generating
        if (cg.builder.GetInsertBlock()->getTerminator()) break;
    }
    return last;
}

static llvm::Value *gen_return(Codegen &cg, AstReturn *node) {
    llvm::Value *val = gen_node(cg, node->expr);
    if (!val) return nullptr;
    return cg.builder.CreateRet(val);
}

static llvm::Value *gen_if(Codegen &cg, AstIf *node) {
    llvm::Value *cond = gen_node(cg, node->cond);
    if (!cond) return nullptr;

    // Condition must be i1; if it's wider (shouldn't happen after typechecking
    // but be safe), truncate it
    if (!cond->getType()->isIntegerTy(1)) {
        cond = cg.builder.CreateICmpNE(
            cond, llvm::Constant::getNullValue(cond->getType()), "cond");
    }

    llvm::Function *fn     = cg.builder.GetInsertBlock()->getParent();
    llvm::BasicBlock *then_bb  = llvm::BasicBlock::Create(cg.ctx, "then",  fn);
    llvm::BasicBlock *else_bb  = llvm::BasicBlock::Create(cg.ctx, "else");
    llvm::BasicBlock *merge_bb = llvm::BasicBlock::Create(cg.ctx, "merge");

    cg.builder.CreateCondBr(cond, then_bb, else_bb);

    // Then branch
    cg.builder.SetInsertPoint(then_bb);
    gen_node(cg, node->then);
    if (!cg.builder.GetInsertBlock()->getTerminator())
        cg.builder.CreateBr(merge_bb);

    // Else branch
    fn->insert(fn->end(), else_bb);
    cg.builder.SetInsertPoint(else_bb);
    if (node->else_) {
        gen_node(cg, node->else_);
    }
    if (!cg.builder.GetInsertBlock()->getTerminator())
        cg.builder.CreateBr(merge_bb);

    // Merge
    fn->insert(fn->end(), merge_bb);
    cg.builder.SetInsertPoint(merge_bb);
    return nullptr;
}

// ---------------------------------------------------------------------------
// Expression generators
// ---------------------------------------------------------------------------

static llvm::Value *gen_identifier(Codegen &cg, AstIdentifier *node) {
    std::string name(node->name.data, node->name.len);

    // Local variable — load from alloca (or return pointer for structs)
    auto it = cg.locals.find(name);
    if (it != cg.locals.end()) {
        llvm::AllocaInst *alloca = it->second;
        // For struct types, return the pointer itself (structs are pass-by-reference)
        if (node->type && node->type->kind == TY_STRUCT) {
            // The alloca holds a ptr-to-struct; load that pointer
            return cg.builder.CreateLoad(
                llvm::PointerType::getUnqual(cg.ctx), alloca, name.c_str());
        }
        return cg.builder.CreateLoad(alloca->getAllocatedType(), alloca, name.c_str());
    }

    // Global function — look up in the module
    llvm::Function *fn = cg.mod.getFunction(name);
    if (fn) return fn;

    snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: undefined symbol '%s'", name.c_str());
    return nullptr;
}

static llvm::Value *gen_numeric_literal(Codegen &cg, AstNumericLiteral *node) {
    if (node->type && node->type->kind == TY_FLOAT) {
        return llvm::ConstantFP::get(llvm_type(cg, node->type), node->value);
    }
    // Default: i64 integer
    return llvm::ConstantInt::get(llvm::Type::getInt64Ty(cg.ctx),
                                  (uint64_t)(int64_t)node->value, true);
}

static llvm::Value *gen_string_literal(Codegen &cg, AstStringLiteral *node) {
    // arena-allocated null-terminated string value
    std::string s(node->value.data, node->value.len);
    return cg.builder.CreateGlobalString(s);
}

static llvm::Value *gen_boolean_literal(Codegen &cg, AstBooleanLiteral *node) {
    return llvm::ConstantInt::get(llvm::Type::getInt1Ty(cg.ctx),
                                  node->value ? 1 : 0);
}

static llvm::Value *gen_binary_expr(Codegen &cg, AstBinaryExpr *node) {
    llvm::Value *left  = gen_node(cg, node->left);
    llvm::Value *right = gen_node(cg, node->right);
    if (!left || !right) return nullptr;

    const char *op = node->op.data; // null-terminated (from token_type_name)

    bool is_float = (node->left->type && node->left->type->kind == TY_FLOAT) ||
                    (node->right->type && node->right->type->kind == TY_FLOAT);

    // Arithmetic
    if (strcmp(op, "+") == 0) return is_float ? cg.builder.CreateFAdd(left, right) : cg.builder.CreateAdd(left, right);
    if (strcmp(op, "-") == 0) return is_float ? cg.builder.CreateFSub(left, right) : cg.builder.CreateSub(left, right);
    if (strcmp(op, "*") == 0) return is_float ? cg.builder.CreateFMul(left, right) : cg.builder.CreateMul(left, right);
    if (strcmp(op, "/") == 0) return is_float ? cg.builder.CreateFDiv(left, right) : cg.builder.CreateSDiv(left, right);
    if (strcmp(op, "%") == 0) return is_float ? cg.builder.CreateFRem(left, right) : cg.builder.CreateSRem(left, right);

    // Comparisons
    bool left_signed = node->left->type && node->left->type->kind == TY_INT &&
                       node->left->type->integer.signed_;

    if (strcmp(op, "<")  == 0) return is_float ? cg.builder.CreateFCmpOLT(left, right) : (left_signed ? cg.builder.CreateICmpSLT(left, right) : cg.builder.CreateICmpULT(left, right));
    if (strcmp(op, ">")  == 0) return is_float ? cg.builder.CreateFCmpOGT(left, right) : (left_signed ? cg.builder.CreateICmpSGT(left, right) : cg.builder.CreateICmpUGT(left, right));
    if (strcmp(op, "<=") == 0) return is_float ? cg.builder.CreateFCmpOLE(left, right) : (left_signed ? cg.builder.CreateICmpSLE(left, right) : cg.builder.CreateICmpULE(left, right));
    if (strcmp(op, ">=") == 0) return is_float ? cg.builder.CreateFCmpOGE(left, right) : (left_signed ? cg.builder.CreateICmpSGE(left, right) : cg.builder.CreateICmpUGE(left, right));
    if (strcmp(op, "==") == 0) return is_float ? cg.builder.CreateFCmpOEQ(left, right) : cg.builder.CreateICmpEQ(left, right);
    if (strcmp(op, "!=") == 0) return is_float ? cg.builder.CreateFCmpONE(left, right) : cg.builder.CreateICmpNE(left, right);

    snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: unknown binary op '%s'", op);
    return nullptr;
}

static llvm::Value *gen_assignment_expr(Codegen &cg, AstAssignmentExpr *node) {
    // The left side must be an identifier referencing a local alloca
    if (node->left->kind != NK_IDENTIFIER) {
        snprintf(cg.errbuf, sizeof(cg.errbuf),
                 "codegen: assignment left-hand side must be an identifier");
        return nullptr;
    }

    AstIdentifier *ident = (AstIdentifier *)node->left;
    std::string name(ident->name.data, ident->name.len);

    auto it = cg.locals.find(name);
    if (it == cg.locals.end()) {
        snprintf(cg.errbuf, sizeof(cg.errbuf),
                 "codegen: undefined variable '%s' in assignment", name.c_str());
        return nullptr;
    }

    llvm::AllocaInst *alloca = it->second;
    llvm::Value *rhs = gen_node(cg, node->right);
    if (!rhs) return nullptr;

    const char *op = node->op.data;

    // Compound assignments: load current value, apply op, store result
    if (strcmp(op, "=") != 0) {
        llvm::Value *cur = cg.builder.CreateLoad(alloca->getAllocatedType(), alloca);
        bool is_float = node->left->type && node->left->type->kind == TY_FLOAT;
        if      (strcmp(op, "+=") == 0) rhs = is_float ? cg.builder.CreateFAdd(cur, rhs) : cg.builder.CreateAdd(cur, rhs);
        else if (strcmp(op, "-=") == 0) rhs = is_float ? cg.builder.CreateFSub(cur, rhs) : cg.builder.CreateSub(cur, rhs);
        else if (strcmp(op, "*=") == 0) rhs = is_float ? cg.builder.CreateFMul(cur, rhs) : cg.builder.CreateMul(cur, rhs);
        else if (strcmp(op, "/=") == 0) rhs = is_float ? cg.builder.CreateFDiv(cur, rhs) : cg.builder.CreateSDiv(cur, rhs);
        else if (strcmp(op, "%=") == 0) rhs = is_float ? cg.builder.CreateFRem(cur, rhs) : cg.builder.CreateSRem(cur, rhs);
    }

    cg.builder.CreateStore(rhs, alloca);
    return rhs;
}

static llvm::Value *gen_call_expr(Codegen &cg, AstCallExpr *node) {
    llvm::Value *callee_val = gen_node(cg, node->callee);
    if (!callee_val) return nullptr;

    llvm::Function *callee_fn = llvm::dyn_cast<llvm::Function>(callee_val);
    if (!callee_fn) {
        snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: callee is not a function");
        return nullptr;
    }

    std::vector<llvm::Value *> args;
    for (size_t i = 0; i < node->args.len; i++) {
        llvm::Value *arg = gen_node(cg, node->args.data[i]);
        if (!arg) return nullptr;
        args.push_back(arg);
    }

    // Use the function's own return type for the call instruction
    llvm::Type *ret_type = callee_fn->getReturnType();
    llvm::CallInst *call = cg.builder.CreateCall(callee_fn->getFunctionType(),
                                                   callee_fn, args);
    (void)ret_type;
    return call;
}

static llvm::Value *gen_struct_decl(Codegen &cg, AstStructDecl *node) {
    // Build the LLVM struct type from field types
    std::vector<llvm::Type *> field_types;
    Type *struct_type = node->type; // annotated by typechecker

    for (size_t i = 0; i < struct_type->struct_.fields.len; i++) {
        Type *ft = struct_type->struct_.fields.data[i].type;
        // For struct fields that are themselves structs, use the concrete struct type
        if (ft->kind == TY_STRUCT) {
            field_types.push_back(get_llvm_struct_type(cg, ft));
        } else {
            field_types.push_back(llvm_type(cg, ft));
        }
    }

    std::string name(node->name.data, node->name.len);
    llvm::StructType *st = llvm::StructType::create(cg.ctx, field_types, name);
    cg.struct_types[name] = st;
    return nullptr;
}

static llvm::Value *gen_struct_literal(Codegen &cg, AstStructLiteral *node) {
    // The struct type must have been registered by gen_struct_decl
    std::string name(node->struct_name.data, node->struct_name.len);
    auto it = cg.struct_types.find(name);
    if (it == cg.struct_types.end()) {
        snprintf(cg.errbuf, sizeof(cg.errbuf),
                 "codegen: struct '%s' not registered", name.c_str());
        return nullptr;
    }
    llvm::StructType *st = it->second;

    // Allocate space for the struct on the stack
    llvm::Function *fn = cg.builder.GetInsertBlock()->getParent();
    llvm::AllocaInst *alloca = create_entry_alloca(cg, fn, st, (name + ".tmp").c_str());

    // Fill in each field in declaration order (node->type has the canonical field order)
    Type *struct_type = node->type;
    for (size_t i = 0; i < struct_type->struct_.fields.len; i++) {
        SV field_name = struct_type->struct_.fields.data[i].name;

        // Find the matching initialiser (may be in any order)
        Node *val_node = nullptr;
        for (size_t j = 0; j < node->fields.len; j++) {
            if (sv_eq(node->fields.data[j].name, field_name)) {
                val_node = node->fields.data[j].value;
                break;
            }
        }
        if (!val_node) {
            snprintf(cg.errbuf, sizeof(cg.errbuf),
                     "codegen: missing field in struct literal");
            return nullptr;
        }

        llvm::Value *val = gen_node(cg, val_node);
        if (!val) return nullptr;

        // GEP to the field pointer and store
        llvm::Value *field_ptr = cg.builder.CreateStructGEP(st, alloca, (unsigned)i);
        cg.builder.CreateStore(val, field_ptr);
    }

    return alloca; // return pointer to the struct
}

static llvm::Value *gen_field_access(Codegen &cg, AstFieldAccess *node) {
    // Generate the object (should return a pointer to the struct)
    llvm::Value *obj_ptr = gen_node(cg, node->object);
    if (!obj_ptr) return nullptr;

    // Get the struct type
    Type *struct_type = node->object->type;
    if (!struct_type || struct_type->kind != TY_STRUCT) {
        snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: field access on non-struct");
        return nullptr;
    }

    std::string sname(struct_type->struct_.name.data, struct_type->struct_.name.len);
    auto it = cg.struct_types.find(sname);
    if (it == cg.struct_types.end()) {
        snprintf(cg.errbuf, sizeof(cg.errbuf),
                 "codegen: struct '%s' not registered", sname.c_str());
        return nullptr;
    }
    llvm::StructType *st = it->second;

    int idx = struct_field_index(struct_type, node->field);
    if (idx < 0) {
        snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: unknown struct field");
        return nullptr;
    }

    // GEP to the field and load its value
    llvm::Value *field_ptr = cg.builder.CreateStructGEP(st, obj_ptr, (unsigned)idx);
    Type *field_type = struct_field_type(struct_type, idx);
    llvm::Type *llvm_ft = llvm_type(cg, field_type);

    // For primitive fields, load the value; for struct fields, return the pointer
    if (field_type->kind == TY_STRUCT) {
        return field_ptr;
    }
    return cg.builder.CreateLoad(llvm_ft, field_ptr);
}

static llvm::Value *gen_while(Codegen &cg, AstWhile *node) {
    llvm::Function *fn = cg.builder.GetInsertBlock()->getParent();

    llvm::BasicBlock *header_bb = llvm::BasicBlock::Create(cg.ctx, "while.header", fn);
    llvm::BasicBlock *body_bb   = llvm::BasicBlock::Create(cg.ctx, "while.body");
    llvm::BasicBlock *exit_bb   = llvm::BasicBlock::Create(cg.ctx, "while.exit");

    // Push loop context so break/continue know where to jump
    cg.loop_stack.push_back({header_bb, exit_bb});

    // Fall into the header
    cg.builder.CreateBr(header_bb);

    // Header: evaluate condition
    cg.builder.SetInsertPoint(header_bb);
    llvm::Value *cond = gen_node(cg, node->cond);
    if (!cond) return nullptr;
    if (!cond->getType()->isIntegerTy(1)) {
        cond = cg.builder.CreateICmpNE(
            cond, llvm::Constant::getNullValue(cond->getType()), "while.cond");
    }
    cg.builder.CreateCondBr(cond, body_bb, exit_bb);

    // Body
    fn->insert(fn->end(), body_bb);
    cg.builder.SetInsertPoint(body_bb);
    gen_node(cg, node->body);
    // If body didn't terminate (no break/return), loop back to header
    if (!cg.builder.GetInsertBlock()->getTerminator())
        cg.builder.CreateBr(header_bb);

    // Exit
    fn->insert(fn->end(), exit_bb);
    cg.builder.SetInsertPoint(exit_bb);

    cg.loop_stack.pop_back();
    return nullptr;
}

static llvm::Value *gen_break(Codegen &cg, Node * /*node*/) {
    if (cg.loop_stack.empty()) {
        snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: break outside of loop");
        return nullptr;
    }
    cg.builder.CreateBr(cg.loop_stack.back().exit);
    return nullptr;
}

static llvm::Value *gen_continue(Codegen &cg, Node * /*node*/) {
    if (cg.loop_stack.empty()) {
        snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: continue outside of loop");
        return nullptr;
    }
    cg.builder.CreateBr(cg.loop_stack.back().header);
    return nullptr;
}

// ---------------------------------------------------------------------------
// Main dispatch
// ---------------------------------------------------------------------------

static llvm::Value *gen_node(Codegen &cg, Node *node) {
    static_assert(NK_COUNT == 20, "gen_node: NodeKind changed, update this switch");

    if (!node) return nullptr;

    switch (node->kind) {
        case NK_PROGRAM: {
            AstProgram *p = (AstProgram *)node;
            // First pass: register all struct types so function decls can reference them
            for (size_t i = 0; i < p->body.len; i++) {
                if (p->body.data[i]->kind == NK_STRUCT_DECL) {
                    gen_node(cg, p->body.data[i]);
                    if (cg.errbuf[0]) return nullptr;
                }
            }
            // Second pass: everything else
            for (size_t i = 0; i < p->body.len; i++) {
                if (p->body.data[i]->kind != NK_STRUCT_DECL) {
                    gen_node(cg, p->body.data[i]);
                    if (cg.errbuf[0]) return nullptr;
                }
            }
            return nullptr;
        }
        case NK_EXTERN_FUNCTION_DECL: return gen_extern_function_decl(cg, (AstExternFunctionDecl *)node);
        case NK_FUNCTION_DECL:        return gen_function_decl(cg, (AstFunctionDecl *)node);
        case NK_VAR_DECL:             return gen_var_decl(cg, (AstVarDecl *)node);
        case NK_BLOCK:                return gen_block(cg, (AstBlock *)node);
        case NK_RETURN:               return gen_return(cg, (AstReturn *)node);
        case NK_IF:                   return gen_if(cg, (AstIf *)node);
        case NK_WHILE:                return gen_while(cg, (AstWhile *)node);
        case NK_BREAK:                return gen_break(cg, node);
        case NK_CONTINUE:             return gen_continue(cg, node);
        case NK_STRUCT_DECL:          return gen_struct_decl(cg, (AstStructDecl *)node);
        case NK_STRUCT_LITERAL:       return gen_struct_literal(cg, (AstStructLiteral *)node);
        case NK_FIELD_ACCESS:         return gen_field_access(cg, (AstFieldAccess *)node);
        case NK_IDENTIFIER:           return gen_identifier(cg, (AstIdentifier *)node);
        case NK_NUMERIC_LITERAL:      return gen_numeric_literal(cg, (AstNumericLiteral *)node);
        case NK_STRING_LITERAL:       return gen_string_literal(cg, (AstStringLiteral *)node);
        case NK_BOOLEAN_LITERAL:      return gen_boolean_literal(cg, (AstBooleanLiteral *)node);
        case NK_BINARY_EXPR:          return gen_binary_expr(cg, (AstBinaryExpr *)node);
        case NK_ASSIGNMENT_EXPR:      return gen_assignment_expr(cg, (AstAssignmentExpr *)node);
        case NK_CALL_EXPR:            return gen_call_expr(cg, (AstCallExpr *)node);
        case NK_COUNT:                break;
    }

    snprintf(cg.errbuf, sizeof(cg.errbuf),
             "codegen: unhandled node kind %d", (int)node->kind);
    return nullptr;
}

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

CodegenResult codegen(AstProgram *program, const char *out_path, Arena *arena) {
    CodegenResult result;
    result.error = nullptr;

    Codegen cg("tyger_module", arena);

    // Let clang determine the target triple when compiling the .ll file.
    // Embedding a triple here causes version mismatch warnings on newer OS.

    gen_node(cg, (Node *)program);

    if (cg.errbuf[0]) {
        result.error = arena_copy_str(arena, cg.errbuf, strlen(cg.errbuf));
        return result;
    }

    // Verify the module
    std::string verify_err;
    llvm::raw_string_ostream verify_os(verify_err);
    if (llvm::verifyModule(cg.mod, &verify_os)) {
        verify_os.flush();
        result.error = arena_copy_str(arena, verify_err.c_str(), verify_err.size());
        return result;
    }

    // Write IR to file
    std::error_code ec;
    llvm::raw_fd_ostream out(out_path, ec, llvm::sys::fs::OF_Text);
    if (ec) {
        std::string msg = "cannot open output file: " + ec.message();
        result.error = arena_copy_str(arena, msg.c_str(), msg.size());
        return result;
    }
    cg.mod.print(out, nullptr);

    return result;
}
