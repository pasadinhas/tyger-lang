import { assert } from "../assert.ts";
import type {
  AssignmentExpression,
  BinaryExpression,
  BooleanLiteral,
  Identifier,
  NodeKind,
  NumericLiteral,
  Program,
  Statement,
  VariableDeclaration,
} from "./ast.ts";
import { coerceTypes, isAssignable, type Type, Types, typeToString } from "./types.ts";

type TypeEnv = Map<string, Type>;

const typecheckers: Record<NodeKind, (Statement, TypeEnv) => void> = {
  Program: (statement, env) => typecheckProgram(statement as Program, env),
  NumericLiteral: (statement, env) => typecheckNumericLiteral(statement as NumericLiteral, env),
  BooleanLiteral: (statement, env) => typecheckBooleanLiteral(statement as BooleanLiteral, env),
  AssignmentExpression: typecheckAssignmentExpression,
  BinaryExpression: typecheckBinaryExpression,
  Identifier: typecheckIdentifier,
  VariableDeclaration: typecheckVariableDeclaration,
};

export function typecheck(statement: Statement, env: TypeEnv): Statement {
  typecheckers[statement.kind](statement, env);
  return statement;
}

function typecheckProgram(program: Program, env: TypeEnv) {
  for (const statement of program.body) {
    typecheck(statement, env);
  }
}

function typecheckNumericLiteral(numericLiteral: NumericLiteral, env: TypeEnv) {
  numericLiteral.type = Types.i64;
}

function typecheckBooleanLiteral(booleanLiteral: BooleanLiteral, env: TypeEnv) {
  booleanLiteral.type = Types.boolean;
}

function typecheckAssignmentExpression(assignmentExpression: AssignmentExpression, env: TypeEnv) {
  typecheck(assignmentExpression.left, env);
  typecheck(assignmentExpression.right, env);

  const leftType = assignmentExpression.left.type!;
  const rightType = assignmentExpression.right.type!;

  typecheckerAssert(
    isAssignable(leftType, rightType),
    `Cannot assign expression of type ${typeToString(rightType)} to variable of type ${typeToString(leftType)}`
  );

  assignmentExpression.type = assignmentExpression.left.type;
}

function typecheckBinaryExpression(binaryExpression: BinaryExpression, env: TypeEnv) {
  typecheck(binaryExpression.left, env);
  typecheck(binaryExpression.right, env);

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
        `Cannot apply operator ${binaryExpression.operator} to types ${typeToString(leftType)} and ${typeToString(rightType)}`
      );
      binaryExpression.type = resultType;
      return;
    default:
      assert(false, `Unhandled operator: ${binaryExpression.operator}`);
  }
}

function typecheckIdentifier(identifier: Identifier, env: TypeEnv) {
  const type = env.get(identifier.name);
  typecheckerAssert(type !== undefined, `Unbound variable ${identifier.name}`);
  identifier.type = type;
}

function typecheckVariableDeclaration(variableDeclaration: VariableDeclaration, env: TypeEnv) {
  typecheck(variableDeclaration.initializer, env);

  const type = env.get(variableDeclaration.identifier);
  typecheckerAssert(type === undefined, `Cannot redeclare variable ${variableDeclaration.identifier}`);

  env.set(variableDeclaration.identifier, variableDeclaration.initializer.type as Type);
}

export function typecheckerAssert(condition: unknown, message = "<no message>"): asserts condition {
  if (!condition) {
    throw new Error(`TypeChecker :: ${message}`);
  }
}
