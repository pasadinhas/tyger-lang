export type NodeKind =
  | "Program"
  | "NumericLiteral"
  | "Identifier"
  | "BinaryExpression";

export interface Statement {
  kind: NodeKind;
}

export interface Program extends Statement {
  kind: "Program";
  body: Statement[];
}

export interface Expression extends Statement {}

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
