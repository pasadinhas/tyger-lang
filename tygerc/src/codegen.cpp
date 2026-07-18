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

struct Codegen {
    llvm::LLVMContext               ctx;
    llvm::Module                    mod;
    llvm::IRBuilder<>               builder;
    Arena                          *arena;
    char                            errbuf[512];

    // Maps variable name (as null-terminated C string) to its alloca.
    // Cleared on each function entry.
    std::map<std::string, llvm::AllocaInst *> locals;

    Codegen(const char *module_name, Arena *a)
        : ctx(), mod(module_name, ctx), builder(ctx), arena(a) {
        errbuf[0] = '\0';
    }
};

// ---------------------------------------------------------------------------
// Type mapping: Tyger Type* → llvm::Type*
// ---------------------------------------------------------------------------

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
    // Build param types
    std::vector<llvm::Type *> param_types;
    for (size_t i = 0; i < node->params.len; i++) {
        Type *pt = type_from_sv(node->params.data[i].type_name);
        param_types.push_back(llvm_type(cg, pt));
    }

    Type *ret_ty = type_from_sv(node->return_type_name);
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
        llvm::AllocaInst *alloca = create_entry_alloca(cg, fn, arg.getType(),
                                                        (pname + ".addr").c_str());
        cg.builder.CreateStore(&arg, alloca);
        cg.locals[pname] = alloca;
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

    llvm::Type *ty = llvm_type(cg, node->type);
    std::string name(node->name.data, node->name.len);

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

    // Local variable — load from alloca
    auto it = cg.locals.find(name);
    if (it != cg.locals.end()) {
        llvm::AllocaInst *alloca = it->second;
        return cg.builder.CreateLoad(alloca->getAllocatedType(), alloca, name.c_str());
    }

    // Global function — look up in the module
    llvm::Function *fn = cg.mod.getFunction(name);
    if (fn) return fn;

    snprintf(cg.errbuf, sizeof(cg.errbuf), "codegen: undefined symbol '%s'", name.c_str());
    return nullptr;
}

static llvm::Value *gen_numeric_literal(Codegen &cg, AstNumericLiteral *node) {
    // Default to i64
    return llvm::ConstantInt::get(llvm::Type::getInt64Ty(cg.ctx),
                                  (uint64_t)node->value, true);
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

// ---------------------------------------------------------------------------
// Main dispatch
// ---------------------------------------------------------------------------

static llvm::Value *gen_node(Codegen &cg, Node *node) {
    static_assert(NK_COUNT == 14, "gen_node: NodeKind changed, update this switch");

    if (!node) return nullptr;

    switch (node->kind) {
        case NK_PROGRAM: {
            AstProgram *p = (AstProgram *)node;
            for (size_t i = 0; i < p->body.len; i++) {
                gen_node(cg, p->body.data[i]);
                if (cg.errbuf[0]) return nullptr;
            }
            return nullptr;
        }
        case NK_EXTERN_FUNCTION_DECL: return gen_extern_function_decl(cg, (AstExternFunctionDecl *)node);
        case NK_FUNCTION_DECL:        return gen_function_decl(cg, (AstFunctionDecl *)node);
        case NK_VAR_DECL:             return gen_var_decl(cg, (AstVarDecl *)node);
        case NK_BLOCK:                return gen_block(cg, (AstBlock *)node);
        case NK_RETURN:               return gen_return(cg, (AstReturn *)node);
        case NK_IF:                   return gen_if(cg, (AstIf *)node);
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
