import { assert } from "../assert.ts";
import type {
  Program,
  Statement,
  Expression,
  Identifier,
  NumericLiteral,
  BooleanLiteral,
  BinaryExpression,
  VariableDeclaration,
  AssignmentExpression,
} from "./ast.ts";
import type { Token, TokenType } from "./lexer.ts";

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
  switch (peek(parser).type) {
    case "let":
      return parseVariableDeclaration(parser);
    default:
      return parseExpression(parser);
  }
}

// let [mut] <identifier> = <expression>
function parseVariableDeclaration(parser: Parser): VariableDeclaration {
  eat(parser); // let

  let mutable = false;
  if (peek(parser).type === "mut") {
    eat(parser); // mut
    mutable = true;
  }

  const identifierToken = expect(
    parser,
    "Identifier",
    `Unexpected token: expected an identifier in a variable declaration but got: ${peek(parser).type}`
  );

  let typeHint: string | undefined;
  if (peek(parser).type === ":") {
    eat(parser); // :
    typeHint = expect(
      parser,
      "Identifier",
      `Unexpected token: expected a type identifier in a variable declaration but got: ${peek(parser).type}`
    ).value;
  }

  expect(
    parser,
    "=",
    `Unexpected token: Expected an equal sign in a variable declaration but got: ${peek(parser).type}`
  );

  const initializer = parseExpression(parser);
  expect(
    parser,
    ";",
    `Unexpected token: Expected a semicolon at the end of a variable declaration but got: ${peek(parser).type}`
  );

  return {
    kind: "VariableDeclaration",
    mutable: mutable,
    identifier: identifierToken.value,
    initializer: initializer,
    typeHint: typeHint,
  };
}

function parseExpression(parser: Parser): Expression {
  return parseAssignmentExpression(parser);
}

function parseAssignmentExpression(parser: Parser): Expression {
  const left = parseEqualityExpression(parser);

  if (["=", "+=", "-=", "*=", "/=", "%="].includes(peek(parser).type)) {
    const operator = eat(parser).type;
    const right = parseExpression(parser);
    return {
      kind: "AssignmentExpression",
      left,
      right,
      operator,
    } as AssignmentExpression;
  }

  return left;
}

function parseEqualityExpression(parser: Parser): Expression {
  let left = parseRelativeExpression(parser);

  while (["==", "!="].includes(peek(parser).type)) {
    const operator = eat(parser).type;
    const right = parseRelativeExpression(parser);
    left = {
      kind: "BinaryExpression",
      left,
      right,
      operator,
    } as BinaryExpression;
  }

  return left;
}

function parseRelativeExpression(parser: Parser): Expression {
  let left = parseAdditiveExpression(parser);

  while ([">=", ">", "<", "<="].includes(peek(parser).type)) {
    const operator = eat(parser).type;
    const right = parseAdditiveExpression(parser);
    left = {
      kind: "BinaryExpression",
      left,
      right,
      operator,
    } as BinaryExpression;
  }

  return left;
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
  let left = parseUnaryOperatorExpression(parser);

  while (["/", "*", "%"].includes(peek(parser).type)) {
    const operator = eat(parser).type;
    const right = parseUnaryOperatorExpression(parser);
    left = {
      kind: "BinaryExpression",
      left,
      right,
      operator,
    } as BinaryExpression;
  }

  return left;
}

function parseUnaryOperatorExpression(parser: Parser) {
  return parsePostfixOperatorExpression(parser);
}

function parsePostfixOperatorExpression(parser: Parser): Expression {
  return parsePrimaryExpression(parser);
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
    case "true":
    case "false":
      return {
        kind: "BooleanLiteral",
        raw: peek(parser).value, // peeking here, and...
        value: eat(parser).value === "true", // ... eating here.
      } as BooleanLiteral;
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
