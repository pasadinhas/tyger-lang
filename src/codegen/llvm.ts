import type {
  Program,
  Statement,
  BinaryExpression,
  NumericLiteral,
  FunctionDeclaration,
  ReturnStatement,
  CallExpression,
  Identifier,
  ExternalFunctionDeclaration,
  StringLiteral,
} from "../frontend/ast.ts";
import { type Type } from "../frontend/types.ts";

export function llvmType(type: Type): string {
  switch (type.kind) {
    case "int":
      return `i${type.bits}`;
    case "float":
      return `f${type.bits}`;
    case "boolean":
      return "i1";
    case "ptr":
    case "string":
    case "function":
    case "external_function":
    case "TypeVar":
      return "ptr";
    default:
      return `unknown[${JSON.stringify(type)}]`;
  }
}

export class LLVMCodeGen {
  header: string[] = [];
  output: string[] = [];
  tempCount = 0;
  labelCount = 0;

  // Maps variable names to the register holding their pointer (the alloca)
  symbols = new Map<string, string>();

  // Global string literals
  stringGlobals = new Map<string, string>();

  // Helper to generate unique register names
  newReg() {
    return `%t${++this.tempCount}`;
  }

  // Helper to generate unique block labels
  newLabel(name: string) {
    return `${name}_${++this.labelCount}`;
  }

  emit(line: string) {
    this.output.push("  " + line);
  }

  compile(program: Program): string {
    // 1. Module Header (targeting Apple Silicon)
    this.header.push('target datalayout = "e-m:o-i64:64-i128:128-n32:64-S128"');
    this.header.push('target triple = "arm64-apple-macosx14.0.0"');

    // 2. Generate each top-level statement
    for (const stmt of program.body) {
      this.gen(stmt);
    }

    return this.header.join("\n") + "\n" + this.output.join("\n");
  }

  gen(node: Statement): string {
    switch (node.kind) {
      case "NumericLiteral":
        return (node as NumericLiteral).value.toString();

      case "Identifier": {
        const name = (node as Identifier).name;
        const pointer = this.symbols.get(name);
        if (!pointer) throw `Undefined variable: ${name}`;
        const reg = this.newReg();
        this.emit(`${reg} = load i64, ptr ${pointer}`);
        return reg;
      }

      case "StringLiteral": {
        const value = (node as StringLiteral).value;
        let globalName: string;

        // Reuse the same global if the string appears twice
        if (this.stringGlobals.has(value)) {
          globalName = this.stringGlobals.get(value)!;
        } else {
          globalName = `@.str.${this.stringGlobals.size}`;
          this.stringGlobals.set(value, globalName);
          
          // Add the global definition to the top of the file
          // Note: we add the \00 null terminator for C compatibility
          const escapedValue = value.replace(/\n/g, '\\0A');
          const len = value.length + 1;
          this.header.push(`${globalName} = private unnamed_addr constant [${len} x i8] c"${escapedValue}\\00", align 1`);
        }

        // Now, you can return a "constant struct" if LLVM supports it,
        // or just return the pointer if you're keeping it simple for now.
        return globalName;
      }

      case "BinaryExpression": {
        const bin = node as BinaryExpression;
        const left = this.gen(bin.left);
        const right = this.gen(bin.right);
        const res = this.newReg();

        const opMap: any = { "+": "add", "-": "sub", "*": "mul", "/": "sdiv" };
        const compMap: any = { "<": "slt", ">": "sgt", "==": "eq" };

        if (opMap[bin.operator]) {
          this.emit(`${res} = ${opMap[bin.operator]} i64 ${left}, ${right}`);
        } else if (compMap[bin.operator]) {
          // Comparisons return i1 (boolean)
          this.emit(`${res} = icmp ${compMap[bin.operator]} i64 ${left}, ${right}`);
        }
        return res;
      }

      case "FunctionDeclaration":
        this.genFunctionDeclaration(node as FunctionDeclaration);
        return "";

      case "ExternalFunctionDeclaration":
        this.genExternalFunctionDeclaration(node as ExternalFunctionDeclaration);
        return "";

      case "ReturnStatement": {
        const val = this.gen((node as ReturnStatement).expression);
        this.emit(`ret i64 ${val}`);
        return "";
      }

      case "CallExpression": {
        const call = node as CallExpression;
        const calleeName = (call.callee as Identifier).name;
        const args = call.arguments.map((arg) => `${llvmType(arg.type!)} ${this.gen(arg)}`).join(", ");
        const res = this.newReg();
        this.emit(`${res} = call ${llvmType(call.type!)} ${calleeName === "printf" ? "(ptr, ...)" : ""} @${calleeName}(${args})`);
        return res;
      }

      // Note: Assuming an IfStatement exists in your AST based on your factorial example
      case "IfStatement":
        this.genIfStatement(node as any);
        return "";

      default:
        throw `LLVM Codegen not implemented for: ${node.kind}`;
    }
  }

  genExternalFunctionDeclaration(externalFunctionDeclaration: ExternalFunctionDeclaration) {
    const returnType = externalFunctionDeclaration.typeHint;
    const identifier = externalFunctionDeclaration.identifier;
    const params = externalFunctionDeclaration.params.map(param => param.typeHint).join(", ")
    const variadic = externalFunctionDeclaration.isVariadic ? ", ..." : "";
    this.output.push(`\ndeclare ${returnType} @${identifier}(${params}${variadic})\n`);
  }

  genFunctionDeclaration(fn: FunctionDeclaration) {
    this.tempCount = 0; // Reset registers for each function
    const params = fn.params.map((p) => `${p.typeHint} %${p.name}`).join(", ");

    this.output.push(`define i64 @${fn.identifier}(${params}) {`);
    this.output.push("entry:");

    // The Alloca Trick: Move parameters to stack memory immediately
    // so they are mutable and have pointers.
    for (const p of fn.params) {
      const ptr = `%${p.name}.addr`;
      this.emit(`${ptr} = alloca i64`);
      this.emit(`store i64 %${p.name}, ptr ${ptr}`);
      this.symbols.set(p.name, ptr);
    }

    for (const stmt of fn.body.body) {
      this.gen(stmt);
    }

    // Default return if none provided
    if (this.output[this.output.length - 1].trim().indexOf("ret") !== 0) {
      this.emit("ret i64 0");
    }
    this.output.push("}\n");
  }

  genIfStatement(node: any) {
    const condReg = this.gen(node.condition);
    const thenLabel = this.newLabel("then");
    const elseLabel = this.newLabel("else");
    const mergeLabel = this.newLabel("merge");

    this.emit(`br i1 ${condReg}, label %${thenLabel}, label %${elseLabel}`);

    // Then Branch
    this.output.push(`${thenLabel}:`);
    this.gen(node.then);
    this.emit(`br label %${mergeLabel}`);

    // Else Branch
    this.output.push(`${elseLabel}:`);
    if (node.else) this.gen(node.else);
    this.emit(`br label %${mergeLabel}`);

    // Merge
    this.output.push(`${mergeLabel}:`);
  }
}
