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
  BlockStatement,
  FunctionDeclaration,
  ReturnStatement,
  Param,
  CallExpression,
  IfStatement,
  StringLiteral,
  ExternalFunctionDeclaration,
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

function dump(parser: Parser) {
  console.log(parser.tokens.slice(parser.current));
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
    case "extern":
      return parseExternalFunctionDeclaration(parser);
    case "let":
      return parseVariableDeclaration(parser);
    case "return":
      return parseReturnStatement(parser);
    case "fn":
      return parseFunctionDeclaration(parser);
    case "if":
      return parseIfStatement(parser);
    case "{":
      return parseBlockStatement(parser);
    default:
      return parseExpressionStatement(parser);
  }
}

function parseExpressionStatement(parser: Parser): Expression {
  const expression = parseExpression(parser);
  expect(
    parser,
    ";",
    `Unexpected token: Expected a semicolon at the end of an expression statement but got: ${peek(parser).type}`,
  );
  return expression;
}


function parseIfStatement(parser: Parser): IfStatement {
  expect(parser, "if", `Unexpected token: expected 'if' at the start of a if statement but got: ${peek(parser).type}`);
  const condition = parseExpression(parser);
  const thenStatement = parseStatement(parser);
  let elseStatement = undefined;

  if (match(parser, "else")) {
    eat(parser); // else
    elseStatement = parseStatement(parser);
  } 

  return {
    kind: "IfStatement",
    condition,
    then: thenStatement,
    else: elseStatement,
  };
}

function parseBlockStatement(parser: Parser): BlockStatement {
  const body: Statement[] = [];

  expect(parser, "{", `Unexpected token: expected a '{' at the start of a block but got: ${peek(parser).type}`);
  while (!match(parser, "}")) {
    body.push(parseStatement(parser));
  }
  expect(parser, "}", `Unexpected token: expected a '}' at the end of a block but got: ${peek(parser).type}`);

  return {
    kind: "BlockStatement",
    body: body,
    returnTypes: [],
  };
}

function parseReturnStatement(parser: Parser): ReturnStatement {
  expect(
    parser,
    "return",
    `Unexpected token: Expected 'return' at the start of a return statement but got: ${peek(parser).type}`,
  );
  const expression = parseExpression(parser);
  expect(
    parser,
    ";",
    `Unexpected token: Expected a semicolon at the end of a return statement but got: ${peek(parser).type}`,
  );
  return {
    kind: "ReturnStatement",
    expression: expression,
  };
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
    `Unexpected token: expected an identifier in a variable declaration but got: ${peek(parser).type}`,
  );

  let typeHint: string | undefined;
  if (peek(parser).type === ":") {
    eat(parser); // :
    typeHint = expect(
      parser,
      "Identifier",
      `Unexpected token: expected a type identifier in a variable declaration but got: ${peek(parser).type}`,
    ).value;
  }

  expect(
    parser,
    "=",
    `Unexpected token: Expected an equal sign in a variable declaration but got: ${peek(parser).type}`,
  );

  const initializer = parseExpression(parser);
  expect(
    parser,
    ";",
    `Unexpected token: Expected a semicolon at the end of a variable declaration but got: ${peek(parser).type}`,
  );

  return {
    kind: "VariableDeclaration",
    mutable: mutable,
    identifier: identifierToken.value,
    initializer: initializer,
    typeHint: typeHint,
  };
}

function parseExternalFunctionDeclaration(parser: Parser): ExternalFunctionDeclaration {
  expect(
    parser,
    "extern",
    `Unexpected token: Expected 'extern' at the start of an external function declaration but got: ${peek(parser).type}`,
  );

  expect(
    parser,
    "fn",
    `Unexpected token: Expected 'fn' after 'extern' but got: ${peek(parser).type}`,
  );

  const identifierToken = expect(
    parser,
    "Identifier",
    `Unexpected token: expected an identifier in an external function declaration but got: ${peek(parser).type}`,
  );

  const params: Param[] = [];
  expect(parser, "(", `Unexpected token: Expected '(' at the start of the params list but got: ${peek(parser).type}`);

  let isVariadic = false;
  let i = 0;
  let trailingComma = false;
  while (!match(parser, ")")) {
    trailingComma = false;
    if (match(parser, "...")) {
      eat(parser); // ...
      isVariadic = true;
      if (!match(parser, ")")) {
        assert(false, `Unexpected token: Expected ')' after '...' as the variadic argument of an external function must be the last argument, but got: ${peek(parser).type}`);
      }
      break;
    }
    const identifierToken = expect(
      parser,
      "Identifier",
      `Unexpected token: expected an identifier in a params list but got: ${peek(parser).type}`,
    );

    expect(parser, ":", `Unexpected token: Expected ':' after the function parameter name but got: ${peek(parser).type}`);

    const typeIdentifierToken = expect(
      parser,
      "Identifier",
      `Unexpected token: expected a type identifier in a params list but got: ${peek(parser).type}`,
    );

    params.push({
      name: identifierToken.value,
      typeHint: typeIdentifierToken.value,
      index: i++,
    });

    if (match(parser, ",")) {
      trailingComma = true;
      eat(parser); // ,
    }
  }

  if (trailingComma) {
    assert(false, `Unexpected token: found a trailing comma in a params list`);
  }

  expect(parser, ")", `Unexpected token: Expected ')' after the end of the params list but got: ${peek(parser).type}`);

  expect(parser, "->", `Unexpected token: Expected '->' after the function parameters but got: ${peek(parser).type}`);

  let typeHint = expect(
    parser,
    "Identifier",
    `Unexpected token: expected a type identifier in the function declaration return type but got: ${peek(parser).type}`,
  ).value;

  expect(
    parser,
    ";",
    `Unexpected token: Expected a semicolon at the end of an external function declaration but got: ${peek(parser).type}`,
  );

  return {
    kind: "ExternalFunctionDeclaration",
    identifier: identifierToken.value,
    typeHint: typeHint,
    params: params,
    isVariadic,
  };
}

function parseFunctionDeclaration(parser: Parser): FunctionDeclaration {
  expect(
    parser,
    "fn",
    `Unexpected token: Expected 'fn' at the start of a function declaration but got: ${peek(parser).type}`,
  );

  const identifierToken = expect(
    parser,
    "Identifier",
    `Unexpected token: expected an identifier in a function declaration but got: ${peek(parser).type}`,
  );

  const params = parseParamsList(parser);

  expect(parser, "->", `Unexpected token: Expected '->' after the function parameters but got: ${peek(parser).type}`);

  let typeHint = expect(
    parser,
    "Identifier",
    `Unexpected token: expected a type identifier in the function declaration return type but got: ${peek(parser).type}`,
  ).value;

  const body = parseBlockStatement(parser);

  return {
    kind: "FunctionDeclaration",
    identifier: identifierToken.value,
    body: body,
    typeHint: typeHint,
    params: params,
  };
}

// (arg1: string, arg2: i64)
function parseParamsList(parser: Parser): Param[] {
  const params: Param[] = [];
  expect(parser, "(", `Unexpected token: Expected '(' at the start of the params list but got: ${peek(parser).type}`);

  let i = 0;
  let trailingComma = false;
  while (!match(parser, ")")) {
    trailingComma = false;
    const identifierToken = expect(
      parser,
      "Identifier",
      `Unexpected token: expected an identifier in a params list but got: ${peek(parser).type}`,
    );

    expect(parser, ":", `Unexpected token: Expected ':' after the function parameter name but got: ${peek(parser).type}`);

    const typeIdentifierToken = expect(
      parser,
      "Identifier",
      `Unexpected token: expected a type identifier in a params list but got: ${peek(parser).type}`,
    );

    params.push({
      name: identifierToken.value,
      typeHint: typeIdentifierToken.value,
      index: i++,
    });

    if (match(parser, ",")) {
      trailingComma = true;
      eat(parser); // ,
    }
  }

  if (trailingComma) {
    assert(false, `Unexpected token: found a trailing comma in a params list`);
  }

  expect(parser, ")", `Unexpected token: Expected ')' after the end of the params list but got: ${peek(parser).type}`);
  return params;
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
  let left = parsePrimaryExpression(parser);

  // Use a loop to handle chained postfix operators like: my_function(1)(2)
  while (match(parser, "(")) {
    left = parseCallExpression(parser, left);
  }

  return left;
}

function parseCallExpression(parser: Parser, callee: Expression): CallExpression {
  expect(parser, "(", `Unexpected token: expected '(' at the start of a function call arguments list but got: ${peek(parser).type}`);
  
  const args: Expression[] = [];
  let trailingComma = false;

  while (!match(parser, ")")) {
    trailingComma = false;
    args.push(parseExpression(parser));

    if (match(parser, ",")) {
      eat(parser);
      trailingComma = true;
    } else {
      break;
    }
  }

  if (trailingComma) {
    assert(false, "Unexpected token: found a trailing comma in an argument list");
  }

  expect(parser, ")", `Unexpected token: expected ')' at the end of a function call arguments list but got: ${peek(parser).type}`);

  return {
    kind: "CallExpression",
    callee,
    arguments: args,
  } as CallExpression;
}

function parsePrimaryExpression(parser: Parser): Expression {
  switch (peek(parser).type) {
    case "Identifier":
      return {
        kind: "Identifier",
        name: eat(parser).value,
      } as Identifier;
    case "String":
      return {
        kind: "StringLiteral",
        value: eat(parser).value,
      } as StringLiteral
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
        `Unexpected token at the end of parenthesised expression. Expected  ')', found: ${peek(parser).type}`,
      );
      return expression;
    default:
      assert(false, `parser :: unexpected token: ${JSON.stringify(peek(parser))}`);
  }
}
