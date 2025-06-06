import { expect, test } from "vitest";
import { parse } from "../src/frontend/parser.ts";
import type { BinaryExpression, NumericLiteral, Program, VariableDeclaration } from "../src/frontend/ast.ts";
import { token } from "../src/frontend/lexer.ts";

test("empty program", () => {
  expect(parse([token("EOF")])).toStrictEqual({
    kind: "Program",
    body: [],
  } as Program);
});

test("variable declaration - mutable", () => {
  expect(
    parse([
      token("let"),
      token("mut"),
      token("Identifier", "x"),
      token("="),
      token("Number", "5"),
      token(";"),
      token("EOF"),
    ])
  ).toStrictEqual({
    kind: "Program",
    body: [
      {
        kind: "VariableDeclaration",
        mutable: true,
        identifier: "x",
        initializer: {
          kind: "NumericLiteral",
          raw: "5",
          value: 5,
        } as NumericLiteral,
      } as VariableDeclaration,
    ],
  } as Program);
});

test("variable declaration - constant", () => {
  expect(
    parse([
      token("let"),
      token("Identifier", "var2"),
      token("="),
      token("Number", "42"),
      token(";"),
      token("EOF"),
    ])
  ).toStrictEqual({
    kind: "Program",
    body: [
      {
        kind: "VariableDeclaration",
        mutable: false,
        identifier: "var2",
        initializer: {
          kind: "NumericLiteral",
          raw: "42",
          value: 42,
        } as NumericLiteral,
      } as VariableDeclaration,
    ],
  } as Program);
});

test("operator precedence: 1 / 2 + (3 - 4) * 5", () => {
  expect(parse([
    token("Number", "1"), 
    token("/"), 
    token("Number", "2"), 
    token("+"), 
    token("("), 
    token("Number", "3"), 
    token("-"), 
    token("Number", "4"), 
    token(")"), 
    token("*"), 
    token("Number", "5"), 
    token("EOF")
  ])).toStrictEqual({
    kind: "Program",
    body: [
      {
        kind: "BinaryExpression",
        operator: "+",
        left: {
          kind: "BinaryExpression",
          operator: "/",
          left: {
            kind: "NumericLiteral",
            raw: "1",
            value: 1,
          } as NumericLiteral,
          right: {
            kind: "NumericLiteral",
            raw: "2",
            value: 2,
          } as NumericLiteral
        } as BinaryExpression,
        right: {
          kind: "BinaryExpression",
          operator: "*",
          left: {
            kind: "BinaryExpression",
            operator: "-",
            left: {
              kind: "NumericLiteral",
              raw: "3",
              value: 3,
            } as NumericLiteral,
            right: {
              kind: "NumericLiteral",
              raw: "4",
              value: 4,
            } as NumericLiteral
          } as BinaryExpression,
          right: {
            kind: "NumericLiteral",
            raw: "5",
            value: 5,
          } as NumericLiteral
        } as BinaryExpression,
      } as BinaryExpression,
    ],
  } as Program);
});
