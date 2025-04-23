export const TokenKinds = {
  // keywords
  Let: "Let",
  
  // Operators
  Equal: "Equal",

  // Arithmetic
  Plus: "Plus",
  Minus: "Minus",
  Star: "Star",
  Slash: "Slash",
  // Percent: "Percent",

  // Delimiters
  LParen: "LParen",
  RParen: "RParen",
  Semicolon: "Semicolon",

  // Literals
  Identifier: "Identifier",
  Number: "Number",
} as const;

export type TokenKind = (typeof TokenKinds)[keyof typeof TokenKinds];

export interface Token {
  kind: TokenKind;
  value: string;
}

export function token(kind: TokenKind, value: string): Token {
  return { kind, value };
}
