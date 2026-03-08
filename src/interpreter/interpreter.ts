import { assert } from "../assert.ts";
import type {
  Program,
  Statement,
  NumericLiteral,
  BinaryExpression,
  Identifier,
  VariableDeclaration,
  AssignmentExpression,
  BooleanLiteral,
  FunctionDeclaration,
  BlockStatement,
  CallExpression,
  ReturnStatement,
  IfStatement,
  StringLiteral,
} from "../frontend/ast.ts";

type RuntimeValueType = "number" | "boolean" | "string" | "function" | "void";

interface RuntimeSymbol {
  type: RuntimeValueType;
  value: null | number | boolean | string | BlockStatement;
  mutable: boolean;
}

interface VoidRuntimeValue extends RuntimeSymbol {
  type: "void";
  value: null;
}

interface NumberRuntimeValue extends RuntimeSymbol {
  type: "number";
  value: number;
}

interface BooleanRuntimeValue extends RuntimeSymbol {
  type: "boolean";
  value: boolean;
}

interface StringRuntimeValue extends RuntimeSymbol {
  type: "string";
  value: string;
}

interface FunctionRuntimeValue extends RuntimeSymbol {
  type: "function";
  params: string[];
  value: BlockStatement;
}

function NumberRuntimeValue(value: number): NumberRuntimeValue {
  return {
    type: "number",
    value,
    mutable: true,
  };
}

function BooleanRuntimeValue(value: boolean): BooleanRuntimeValue {
  return {
    type: "boolean",
    value,
    mutable: true,
  };
}

function StringRuntimeValue(value: string): StringRuntimeValue {
  return {
    type: "string",
    value,
    mutable: false,
  };
}

const Void: VoidRuntimeValue = {
  type: "void",
  value: null,
  mutable: false,
};

class ReturnException {
  value: null;
  constructor(value: any) {
    this.value = value;
  }
}

export class RuntimeScope {
  private parent?: RuntimeScope;
  private symbols: Map<string, RuntimeSymbol>;

  constructor(parent?: RuntimeScope) {
    this.parent = parent;
    this.symbols = new Map();
  }

  declare(name: string, value: RuntimeSymbol): RuntimeSymbol {
    if (this.symbols.has(name)) {
      throw `Cannot re-declare symbol: ${name}`;
    }

    this.symbols.set(name, value);
    return value;
  }

  set(name: string, value: RuntimeSymbol): RuntimeSymbol {
    const declaration = this.lookup(name);
    if (!declaration.mutable) {
      throw `Cannot assign a value to a non-mutable symbol: ${name}`;
    }

    if (declaration.type !== value.type) {
      throw `Cannot assign value of type ${value.type} to symbol of type ${declaration.type}`;
    }

    declaration.value = value.value;
    return declaration;
  }

  lookup(name: string): RuntimeSymbol {
    return this.resolve(name).symbols.get(name) as RuntimeSymbol;
  }

  hasSymbolNoRecursion(name: string): boolean {
    return this.symbols.has(name);
  }

  resolve(name: string): RuntimeScope {
    if (this.symbols.has(name)) {
      return this;
    }

    if (this.parent == undefined) {
      throw `Cannot resolve symbol: ${name}`;
    }

    return this.parent.resolve(name);
  }
}

export function evaluate(statement: Statement, scope: RuntimeScope = new RuntimeScope()): any {
  switch (statement.kind) {
    case "Program":
      return evaluateProgram(statement as Program, scope);
    case "VariableDeclaration":
      return evaluateVariableDeclaration(statement as VariableDeclaration, scope);
    case "AssignmentExpression":
      return evaluateAssignmentExpression(statement as AssignmentExpression, scope);
    case "NumericLiteral":
      return evaluateNumericLiteral(statement as NumericLiteral, scope);
    case "BooleanLiteral":
      return evaluateBooleanLiteral(statement as BooleanLiteral, scope);
    case "StringLiteral":
      return evaluateStringLiteral(statement as StringLiteral, scope);
    case "BinaryExpression":
      return evaluateBinaryExpression(statement as BinaryExpression, scope);
    case "Identifier":
      return evaluateIdentifier(statement as Identifier, scope);
    case "CallExpression":
      return evaluateCallExpression(statement as CallExpression, scope);
    case "BlockStatement":
      return evaluateBlockStatement(statement as BlockStatement, scope);
    case "ReturnStatement":
      return evaluateReturnStatement(statement as ReturnStatement, scope);
    case "IfStatement":
      return evaluateIfStatement(statement as IfStatement, scope);
    default:
      assert(false, `Evaluation of statement ${statement.kind} has not been implemented yet.`);
  }
}

function evaluateProgram(program: Program, scope: RuntimeScope) {
  // First detect all available functions
  for (const statement of program.body) {
    if (statement.kind === "FunctionDeclaration") {
      evaluateFunctionDeclaration(statement as FunctionDeclaration, scope);
    }
  }

  // Execute all other statements
  let lastEvaluation: any = null;
  for (const statement of program.body) {
    if (statement.kind !== "FunctionDeclaration") {
      lastEvaluation = evaluate(statement, scope);
    }
  }
  return lastEvaluation;
}

function evaluateVariableDeclaration(variableDeclaration: VariableDeclaration, scope: RuntimeScope) {
  if (scope.hasSymbolNoRecursion(variableDeclaration.identifier)) {
    throw `Variable ${variableDeclaration.identifier} is already declared in this scope.`;
  }

  const value = evaluate(variableDeclaration.initializer, scope);
  scope.declare(variableDeclaration.identifier, value);
}

function evaluateAssignmentExpression(assignmentExpression: AssignmentExpression, scope: RuntimeScope) {
  if (assignmentExpression.left.kind !== "Identifier") {
    throw `Cannot assign to ${assignmentExpression.left}`;
  }

  const variableName = (assignmentExpression.left as Identifier).name;
  const variableValue = scope.lookup(variableName).value as number;
  const rightValue = evaluate(assignmentExpression.right, scope);

  let newValue;

  switch (assignmentExpression.operator) {
    case "=":
      newValue = rightValue;
      break;
    case "+=":
      newValue = variableValue + rightValue;
      break;
    case "-=":
      newValue = variableValue - rightValue;
      break;
    case "*=":
      newValue = variableValue * rightValue;
      break;
    case "/=":
      newValue = variableValue / rightValue;
      break;
    case "%=":
      newValue = variableValue % rightValue;
      break;
    default:
      assert(false, `invalid assignment operator: ${assignmentExpression.operator}`);
  }

  scope.set(variableName, NumberRuntimeValue(newValue));

  return scope.lookup(variableName);
}

function evaluateNumericLiteral(numericLiteral: NumericLiteral, scope: RuntimeScope) {
  return NumberRuntimeValue(numericLiteral.value);
}

function evaluateBooleanLiteral(booleanLiteral: BooleanLiteral, scope: RuntimeScope) {
  return BooleanRuntimeValue(booleanLiteral.value);
}

function evaluateStringLiteral(stringLiteral: StringLiteral, scope: RuntimeScope) {
  return StringRuntimeValue(stringLiteral.value);
}

function evaluateBinaryExpression(binaryExpression: BinaryExpression, scope: RuntimeScope) {
  const left = evaluate(binaryExpression.left, scope).value;
  const right = evaluate(binaryExpression.right, scope).value;

  switch (binaryExpression.operator) {
    case "+":
      return NumberRuntimeValue(left + right);
    case "-":
      return NumberRuntimeValue(left - right);
    case "*":
      return NumberRuntimeValue(left * right);
    case "/":
      return NumberRuntimeValue(left / right); // TODO: handle division by zero.
    case "%":
      return NumberRuntimeValue(left % right);
    case ">=":
      return BooleanRuntimeValue(left >= right);
    case ">":
      return BooleanRuntimeValue(left > right);
    case "<=":
      return BooleanRuntimeValue(left <= right);
    case "<":
      return BooleanRuntimeValue(left < right);
    case "==":
      return BooleanRuntimeValue(left === right);
    case "!=":
      return BooleanRuntimeValue(left !== right);
    default:
      assert(false, `Interpretation of Binary operator ${binaryExpression.operator} is not implemented yet.`);
  }
}

function evaluateIdentifier(identifier: Identifier, scope: RuntimeScope) {
  return scope.lookup(identifier.name);
}

function evaluateFunctionDeclaration(functionDeclaration: FunctionDeclaration, scope: RuntimeScope) {
  scope.declare(functionDeclaration.identifier, {
    type: "function",
    value: functionDeclaration.body,
    mutable: false,
    params: functionDeclaration.params.map((param) => param.name),
  } as FunctionRuntimeValue);
}

function evaluateCallExpression(callExpression: CallExpression, scope: RuntimeScope) {
  const fn = evaluate(callExpression.callee, scope) as FunctionRuntimeValue;
  const functionScope = new RuntimeScope(scope);
  for (let i = 0; i < fn.params.length; ++i) {
    functionScope.declare(fn.params[i], evaluate(callExpression.arguments[i], scope));
  }

  try {
    evaluate(fn.value, functionScope);
  } catch (throwable) {
    if (throwable instanceof ReturnException) {
      return throwable.value;
    }
    throw throwable;
  }

  return Void;
}

function evaluateBlockStatement(blockStatement: BlockStatement, scope: RuntimeScope) {
  const blockScope = new RuntimeScope(scope);
  let lastEvaluation = null;
  for (const statement of blockStatement.body) {
    lastEvaluation = evaluate(statement, blockScope);
  }
  return lastEvaluation;
}

function evaluateReturnStatement(returnStatement: ReturnStatement, scope: RuntimeScope) {
  // Using the host's language exception throwing mechanism to halt the execution and return the value
  // This should be caught in the evaluateCallExpression function
  throw new ReturnException(evaluate(returnStatement.expression, scope));
}

function evaluateIfStatement(ifStatement: IfStatement, scope: RuntimeScope) {
  const condition = evaluate(ifStatement.condition, scope);
  if (condition?.value) {
    return evaluate(ifStatement.then, scope);
  } else if (ifStatement.else) {
    return evaluate(ifStatement.else, scope);
  } else {
    return Void;
  }
}