import { expect, test } from "vitest";
import { parse } from "../src/parser/parser.ts";
import type { BinaryExpression, NumericLiteral, Program } from "../src/ast/ast.ts";
import { token } from "../src/lexer/tokens.ts";

test("empty program", () => {
  expect(parse([token("EOF")])).toStrictEqual({
    kind: "Program",
    body: [],
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
