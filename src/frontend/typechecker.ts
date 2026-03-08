import { assert } from "../assert.ts";
import type {
  AssignmentExpression,
  BinaryExpression,
  BlockStatement,
  BooleanLiteral,
  FunctionDeclaration,
  Identifier,
  NodeKind,
  NumericLiteral,
  Program,
  ReturnStatement,
  Statement,
  VariableDeclaration,
  CallExpression,
  IfStatement,
  StringLiteral,
  ExternalFunctionDeclaration,
} from "./ast.ts";
import {
  coerceTypes,
  external_function_type,
  function_type,
  isAssignable,
  type Type,
  typeFromString,
  Types,
  typeToString,
} from "./types.ts";

class Scope {
  public symbols = new Map<string, Type>();

  declare(name: string, type: Type) {
    if (this.symbols.has(name)) {
      assert(false, `Identifier '${name}' has already been declared in this scope.`);
    }
    this.symbols.set(name, type);
  }

  lookup(name: string): Type | undefined {
    return this.symbols.get(name);
  }
}

export class Context {
  private scopeStack: Scope[] = [new Scope()];
  private functionBlock: BlockStatement[] = [];

  enterFunctionScope(blockStatement: BlockStatement) {
    this.enterScope();
    this.functionBlock.push(blockStatement);
  }

  exitFunctionScope() {
    this.exitScope();
    assert(this.functionBlock.length > 0, "Cannot exit a function scope if there is no function block stored.");
    return this.functionBlock.pop()!.returnTypes;
  }

  enterScope() {
    this.scopeStack.push(new Scope());
  }

  exitScope() {
    assert(this.scopeStack.length > 1, "Compiler Bug: Cannot exit the global scope.");
    this.scopeStack.pop();
  }

  addReturnType(type: Type) {
    assert(this.functionBlock.length > 0, "Cannot add a return type if there is no function scope.");
    this.functionBlock[this.functionBlock.length - 1].returnTypes.push(type);
  }

  declare(name: string, type: Type) {
    this.scopeStack[this.scopeStack.length - 1].declare(name, type);
  }

  lookup(name: string): Type | undefined {
    // We iterate backwards (from most local to most global)
    for (let i = this.scopeStack.length - 1; i >= 0; i--) {
      const type = this.scopeStack[i].lookup(name);
      if (type) return type;
    }

    return undefined;
  }
}

const typecheckers: Record<NodeKind, (statement: Statement, context: Context) => void> = {
  Program: (statement, context) => typecheckProgram(statement as Program, context),
  NumericLiteral: (statement, context) => typecheckNumericLiteral(statement as NumericLiteral, context),
  BooleanLiteral: (statement, context) => typecheckBooleanLiteral(statement as BooleanLiteral, context),
  StringLiteral: (statement, context) => typecheckStringLiteral(statement as StringLiteral, context),
  AssignmentExpression: (statement, context) =>
    typecheckAssignmentExpression(statement as AssignmentExpression, context),
  BinaryExpression: (statement, context) => typecheckBinaryExpression(statement as BinaryExpression, context),
  Identifier: (statement, context) => typecheckIdentifier(statement as Identifier, context),
  VariableDeclaration: (statement, context) => typecheckVariableDeclaration(statement as VariableDeclaration, context),
  FunctionDeclaration: (statement, context) => typecheckFunctionDeclaration(statement as FunctionDeclaration, context),
  ExternalFunctionDeclaration: (statement, context) =>
    typecheckExternalFunctionDeclaration(statement as ExternalFunctionDeclaration, context),
  BlockStatement: (statement, context) => typecheckBlockStatement(statement as BlockStatement, context),
  ReturnStatement: (statement, context) => typecheckReturnStatement(statement as ReturnStatement, context),
  CallExpression: (statement, context) => typecheckCallExpression(statement as CallExpression, context),
  IfStatement: (statement, context) => typecheckIfStatement(statement as IfStatement, context),
};

export function typecheck(statement: Statement, context: Context): Statement {
  typecheckers[statement.kind](statement, context);
  return statement;
}

function typecheckProgram(program: Program, context: Context) {
  // First pass -- find all function declarations to declare them in the Global scope
  for (const statement of program.body) {
    if (statement.kind === "FunctionDeclaration") {
      const functionDeclaration = statement as FunctionDeclaration;
      context.declare(
        functionDeclaration.identifier,
        function_type(
          functionDeclaration.params.map((param) => typeFromString(param.typeHint)),
          typeFromString(functionDeclaration.typeHint),
        ),
      );
    }
    if (statement.kind === "ExternalFunctionDeclaration") {
      const externalFunctionDeclaration = statement as ExternalFunctionDeclaration;
      context.declare(
        externalFunctionDeclaration.identifier,
        external_function_type(
          externalFunctionDeclaration.params.map((param) => typeFromString(param.typeHint)),
          typeFromString(externalFunctionDeclaration.typeHint),
          externalFunctionDeclaration.isVariadic,
        ),
      );
    }
  }

  // Second pass -- actually type check the program
  for (const statement of program.body) {
    typecheck(statement, context);
  }
}

function typecheckNumericLiteral(numericLiteral: NumericLiteral, context: Context) {
  numericLiteral.type = Types.i64;
}

function typecheckBooleanLiteral(booleanLiteral: BooleanLiteral, context: Context) {
  booleanLiteral.type = Types.boolean;
}

function typecheckStringLiteral(stringLiteral: StringLiteral, context: Context) {
  stringLiteral.type = Types.string;
}

function typecheckAssignmentExpression(assignmentExpression: AssignmentExpression, context: Context) {
  typecheck(assignmentExpression.left, context);
  typecheck(assignmentExpression.right, context);

  const leftType = assignmentExpression.left.type!;
  const rightType = assignmentExpression.right.type!;

  typecheckerAssert(
    isAssignable(leftType, rightType),
    `Cannot assign expression of type ${typeToString(rightType)} to variable of type ${typeToString(leftType)}`,
  );

  assignmentExpression.type = assignmentExpression.left.type;
}

function typecheckBinaryExpression(binaryExpression: BinaryExpression, context: Context) {
  typecheck(binaryExpression.left, context);
  typecheck(binaryExpression.right, context);

  const leftType = binaryExpression.left.type!;
  const rightType = binaryExpression.right.type!;

  switch (binaryExpression.operator) {
    case "+":
    case "-":
    case "*":
    case "/":
      const resultType = coerceTypes(leftType, rightType);
      typecheckerAssert(
        resultType !== undefined,
        `Cannot apply operator ${binaryExpression.operator} to types ${typeToString(leftType)} and ${typeToString(
          rightType,
        )}`,
      );
      binaryExpression.type = resultType;
      return;
    case ">":
    case "<":
    case ">=":
    case "<=":
      // TODO: at least for now lets allow any number to be compared
      typecheckerAssert(
        ["int", "float"].includes(leftType.kind) && ["int", "float"].includes(rightType.kind),
        `Cannot apply operator ${binaryExpression.operator} to types ${typeToString(leftType)} and ${typeToString(
          rightType,
        )}`,
      );
      binaryExpression.type = Types.boolean;
      return;
    case "==":
    case "!=":
      binaryExpression.type = Types.boolean;
      return;
    default:
      assert(false, `Unhandled operator: ${binaryExpression.operator}`);
  }
}

function typecheckIdentifier(identifier: Identifier, context: Context) {
  const type = context.lookup(identifier.name);
  typecheckerAssert(type !== undefined, `Unbound symbol: ${identifier.name}`);
  identifier.type = type;
}

function typecheckVariableDeclaration(variableDeclaration: VariableDeclaration, context: Context) {
  const type = context.lookup(variableDeclaration.identifier);
  typecheckerAssert(type === undefined, `Cannot redeclare variable ${variableDeclaration.identifier}`);

  typecheck(variableDeclaration.initializer, context);
  const initializerType = variableDeclaration.initializer.type!;
  if (variableDeclaration.typeHint) {
    const typeHint = typeFromString(variableDeclaration.typeHint!);
    typecheckerAssert(
      isAssignable(typeHint, initializerType),
      `Cannot assign value of type ${typeToString(initializerType)} to variable hinted as ${typeToString(typeHint)}`,
    );

    // if there is a type hint, the variable is declared with that type
    context.declare(variableDeclaration.identifier, typeHint);
  } else {
    // if there is no type hint, we simply assign it the type of the initializer.
    context.declare(variableDeclaration.identifier, initializerType);
  }
}

function typecheckFunctionDeclaration(functionDeclaration: FunctionDeclaration, context: Context) {
  context.enterFunctionScope(functionDeclaration.body);
  functionDeclaration.params.forEach((param) => context.declare(param.name, typeFromString(param.typeHint)));
  typecheck(functionDeclaration.body, context);
  const returnTypes = context.exitFunctionScope();
  const expectedReturnType = typeFromString(functionDeclaration.typeHint);
  // TODO: this doesn't ensure that the function returns that type -- there can be branches where it doesn't return at all
  for (const returnType of returnTypes) {
    typecheckerAssert(
      isAssignable(expectedReturnType, returnType),
      `Cannot return value of type ${typeToString(returnType)} from function that returns ${typeToString(expectedReturnType)}`,
    );
  }
}

function typecheckExternalFunctionDeclaration(
  externalFunctionDeclaration: ExternalFunctionDeclaration,
  context: Context,
) {
  // TODO: lets assume for now that external functions are always correctly defined.
}

function typecheckBlockStatement(blockStatement: BlockStatement, context: Context) {
  // TODO: this means function parameters live in a different context than the block, meaning they could be shadowed.
  context.enterScope();
  for (const statement of blockStatement.body) {
    typecheck(statement, context);
  }
  context.exitScope();
}

function typecheckReturnStatement(returnStatement: ReturnStatement, context: Context) {
  typecheck(returnStatement.expression, context);
  context.addReturnType(returnStatement.expression.type!);
}

function typecheckCallExpression(callExpression: CallExpression, context: Context) {
  typecheck(callExpression.callee, context);

  const functionType = callExpression.callee.type!;

  switch (functionType.kind) {
    case "function": {
      typecheckerAssert(
        functionType.params.length === callExpression.arguments.length,
        `Expected ${functionType.params.length} arguments but got ${callExpression.arguments.length}`,
      );

      for (let i = 0; i < functionType.params.length; ++i) {
        typecheck(callExpression.arguments[i], context);

        const param = functionType.params[i];
        const argument = callExpression.arguments[i].type;

        typecheckerAssert(
          isAssignable(param, argument!),
          `Cannot assign value of type ${typeToString(argument!)} to function parameter of type ${typeToString(param)}`,
        );
      }
      break;
    }
    case "external_function": {
      if (functionType.isVariadic) {
        typecheckerAssert(
          callExpression.arguments.length >= functionType.params.length,
          `Expected at least ${functionType.params.length} arguments for a variadic function but got ${callExpression.arguments.length}`,
        );
      } else {
        typecheckerAssert(
          functionType.params.length === callExpression.arguments.length,
          `Expected ${functionType.params.length} arguments but got ${callExpression.arguments.length}`,
        );

      }
      for (let i = 0; i < callExpression.arguments.length; ++i) {
        typecheck(callExpression.arguments[i], context);

        if (i >= functionType.params.length) {
          continue; // We don't validate variadic arguments.
        }
        const param = functionType.params[i];
        const argument = callExpression.arguments[i].type;

        if (param.kind === "ptr") {
          continue; // TODO: for now we assume we can always pass anything to ptr
        }

        typecheckerAssert(
          isAssignable(param, argument!),
          `Cannot assign value of type ${typeToString(argument!)} to function parameter of type ${typeToString(param)}`,
        );
      }

      break;
    }
    default:
      typecheckerAssert(false, `${functionType.kind} is not a function.`);
  }
  callExpression.type = functionType.return;
}

function typecheckIfStatement(ifStatement: IfStatement, context: Context) {
  typecheck(ifStatement.condition, context);
  typecheckerAssert(
    ifStatement.condition.type!.kind === "boolean",
    "The condition of an if statement must be a boolean",
  );
  typecheck(ifStatement.then, context);
  ifStatement.else && typecheck(ifStatement.else, context);
}

export function typecheckerAssert(condition: unknown, message = "<no message>"): asserts condition {
  if (!condition) {
    throw new Error(`TypeChecker :: ${message}`);
  }
}
