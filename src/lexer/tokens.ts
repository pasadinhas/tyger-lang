export type TokenType =
  | "let"
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
  value?: string;
}

export function token(
  type: TokenType,
  value: string | undefined = undefined
): Token {
  return { type, value };
}
