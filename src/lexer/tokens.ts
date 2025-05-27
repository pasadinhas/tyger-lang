export type TokenType =
  | "let"
  | "mut"
  | "="
  | "+"
  | "-"
  | "*"
  | "/"
  | "%"
  | "("
  | ")"
  | ";"
  | "Identifier"
  | "Number"
  | "EOF";

export interface Token {
  type: TokenType;
  value: string;
}

export function token(
  type: TokenType,
  value: string = type
): Token {
  return { type, value };
}
