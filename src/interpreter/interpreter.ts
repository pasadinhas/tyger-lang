import { assert } from "../assert.ts";
import type {
  Program,
  Statement,
  NumericLiteral,
  BinaryExpression,
  Identifier,
  VariableDeclaration,
} from "../ast/ast.ts";

type RuntimeValueType = "number" | "boolean";

interface RuntimeVariable {
  type: RuntimeValueType;
  value: number | boolean;
  mutable: boolean;
}

interface NumberRuntimeValue extends RuntimeVariable {
  type: "number";
  value: number;
}

interface BooleanRuntimeValue extends RuntimeVariable {
  type: "boolean";
  value: boolean;
}

export class RuntimeScope {
  private parent?: RuntimeScope;
  private variables: Map<string, RuntimeVariable>;

  constructor(parent?: RuntimeScope) {
    this.parent = parent;
    this.variables = new Map();
  }

  public declareVariable(name: string, value: RuntimeVariable): RuntimeVariable {
    if (this.variables.has(name)) {
      throw `Cannot re-declare variable: ${name}`;
    }

    this.variables.set(name, value);
    return value;
  }

  public assignVariable(name: string, value: RuntimeVariable): RuntimeVariable {
    const declaration = this.lookupVariable(name);
    if (!declaration.mutable) {
      throw `Cannot assign a value to a non-mutable variable: ${name}`;
    }

    if (declaration.type !== value.type) {
      throw `Cannot assign value of type ${value.type} to variable of type ${declaration.type}`;
    }

    declaration.value = value.value;
    return declaration;
  }

  public lookupVariable(name: string): RuntimeVariable {
    return this.resolve(name).variables.get(name) as RuntimeVariable;
  }

  public hasVariableNoRecursion(name: string): boolean {
    return this.variables.has(name);
  }

  private resolve(name: string): RuntimeScope {
    if (this.variables.has(name)) {
      return this;
    }

    if (this.parent == undefined) {
      throw `Cannot resolve variable: ${name}`;
    }

    return this.parent.resolve(name);
  }
}

export function evaluate(statement: Statement, scope: RuntimeScope) {
  switch (statement.kind) {
    case "Program":
      return evaluateProgram(statement as Program, scope);
    case "VariableDeclaration":
      return evaluateVariableDeclaration(statement as VariableDeclaration, scope);
    case "NumericLiteral":
      return evaluateNumericLiteral(statement as NumericLiteral, scope);
    case "BinaryExpression":
      return evaluateBinaryExpression(statement as BinaryExpression, scope);
    case "Identifier":
      return evaluateIdentifier(statement as Identifier, scope);
  }
}

function evaluateProgram(program: Program, scope: RuntimeScope) {
  let lastEvaluation: any = null;
  for (const statement of program.body) {
    lastEvaluation = evaluate(statement, scope);
  }
  return lastEvaluation;
}

function evaluateVariableDeclaration(variableDeclaration: VariableDeclaration, scope: RuntimeScope) {
  if (scope.hasVariableNoRecursion(variableDeclaration.identifier)) {
    throw `Variable ${variableDeclaration.identifier} is already declared in this scope.`;
  }

  const value = evaluate(variableDeclaration.initializer, scope);
  scope.declareVariable(variableDeclaration.identifier, {
    type: "number",
    mutable: variableDeclaration.mutable,
    value: value,
  });
}

function evaluateNumericLiteral(numericLiteral: NumericLiteral, scope: RuntimeScope) {
  return numericLiteral.value;
}

function evaluateBinaryExpression(binaryExpression: BinaryExpression, scope: RuntimeScope) {
  const left = evaluate(binaryExpression.left, scope);
  const right = evaluate(binaryExpression.right, scope);
  switch (binaryExpression.operator) {
    case "+":
      return left + right;
    case "-":
      return left - right;
    case "*":
      return left * right;
    case "/":
      return left / right; // TODO: handle division by zero.
    case "%":
      return left % right;
    default:
      assert(false, `Interpretation of Binary operator ${binaryExpression.operator} is not implemented yet.`);
  }
}

function evaluateIdentifier(identifier: Identifier, scope: RuntimeScope) {
  return scope.lookupVariable(identifier.name).value;
}
