import { type Type } from "./types.ts";

export type NodeKind =
  | "Program"
  | "VariableDeclaration"
  | "FunctionDeclaration"
  | "BlockStatement"
  | "ReturnStatement"
  | "AssignmentExpression"
  | "CallExpression"
  | "NumericLiteral"
  | "BooleanLiteral"
  | "Identifier"
  | "BinaryExpression";

export interface Statement {
  kind: NodeKind;
}

export interface Program extends Statement {
  kind: "Program";
  body: Statement[];
}

export interface VariableDeclaration extends Statement {
  kind: "VariableDeclaration";
  mutable: boolean;
  identifier: string;
  initializer: Expression;
  typeHint?: string;
}

export interface Param {
  name: string;
  index: number;
  typeHint: string;
}

export interface FunctionDeclaration extends Statement {
  kind: "FunctionDeclaration";
  identifier: string;
  typeHint: string;
  body: BlockStatement;
  params: Param[];
}

export interface ReturnStatement extends Statement {
  kind: "ReturnStatement";
  expression: Expression;
}

export interface BlockStatement extends Statement {
  kind: "BlockStatement";
  body: Statement[];
  returnTypes: Type[];
}

export interface Expression extends Statement {
  type?: Type;
}

export interface AssignmentExpression extends Expression {
  kind: "AssignmentExpression";
  left: Expression;
  right: Expression;
  operator: string;
}

export interface CallExpression extends Expression {
  kind: "CallExpression";
  callee: Expression;
  arguments: Expression[];
}

export interface BinaryExpression extends Expression {
  kind: "BinaryExpression";
  left: Expression;
  right: Expression;
  operator: string;
}

export interface Identifier extends Expression {
  kind: "Identifier";
  name: string;
}

export interface NumericLiteral extends Expression {
  kind: "NumericLiteral";
  raw: string;
  value: number;
}

export interface BooleanLiteral extends Expression {
  kind: "BooleanLiteral";
  raw: string;
  value: boolean;
}
