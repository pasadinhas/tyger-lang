import { assert } from "../assert.ts";
import type { Program, Statement, Expression, Identifier, NumericLiteral, BinaryExpression } from "../ast/ast.ts";
import { type Token, type TokenType } from "../lexer/tokens.ts";

interface Parser {
  tokens: Token[];
  current: number;
}

function peek(parser: Parser, offset: number = 0): Token {
  return parser.tokens[parser.current + offset];
}

function eat(parser: Parser): Token {
  return parser.tokens[parser.current++];
}

function match(parser: Parser, expected: TokenType): boolean {
  return peek(parser).type === expected;
}

function expect(parser: Parser, expected: TokenType, errorMessage: string): Token {
  if (!match(parser, expected)) {
    assert(false, errorMessage);
  }
  return eat(parser);
}

export function parse(tokens: Token[]): Program {
  const parser: Parser = { tokens, current: 0 };
  return parseProgram(parser);
}

function parseProgram(parser: Parser): Program {
  const body: Statement[] = [];

  while (!match(parser, "EOF")) {
    body.push(parseStatement(parser));
  }

  return {
    kind: "Program",
    body: body,
  };
}

function parseStatement(parser: Parser): Statement {
  return parseExpression(parser);
}

function parseExpression(parser: Parser): Expression {
  return parseAdditiveExpression(parser);
}

function parseAdditiveExpression(parser: Parser): Expression {
  let left = parseMultiplicativeExpression(parser);

  while (["+", "-"].includes(peek(parser).type)) {
    const operator = eat(parser).type;
    const right = parseMultiplicativeExpression(parser);
    left = {
      kind: "BinaryExpression",
      left,
      right,
      operator,
    } as BinaryExpression;
  }

  return left;
}

function parseMultiplicativeExpression(parser: Parser): Expression {
  let left = parsePrimaryExpression(parser);

  while (["/", "*", "%"].includes(peek(parser).type)) {
    const operator = eat(parser).type;
    const right = parsePrimaryExpression(parser);
    left = {
      kind: "BinaryExpression",
      left,
      right,
      operator,
    } as BinaryExpression;
  }

  return left;
}

function parsePrimaryExpression(parser: Parser): Expression {
  switch (peek(parser).type) {
    case "Identifier":
      return {
        kind: "Identifier",
        name: eat(parser).value,
      } as Identifier;
    case "Number":
      return {
        kind: "NumericLiteral",
        raw: peek(parser).value, // peeking here, and...
        value: Number(eat(parser).value), // ... eating here.
      } as NumericLiteral;
    case "(":
      eat(parser);
      const expression = parseExpression(parser);
      expect(
        parser,
        ")",
        `Unexpected token at the end of parenthesised expression. Expected  ')', found: ${peek(parser).type}`
      );
      return expression;
    default:
      assert(false, `parser :: unexpected token: ${JSON.stringify(peek(parser))}`);
  }
}
