import { expect, test } from "vitest";
import { evaluate } from "../src/interpreter/interpreter";
import { parse } from "../src/parser/parser";
import { lex } from "../src/lexer/lexer";

// Note: these are not unit tests. They depend on the correct behaviour of the lexer and the parser.

const arithmeticExpressions = {
  "1 + 1": 2,
  "1 + 2 * 3": 7,
  "1 + 6 / 3": 3,
  "1 + 6 % 3": 1,
  "1 - 5": -4,
  "1 - 2 * 3": -5,
  "1 - 4 / 2": -1,
  "1 - 4 % 2": 1,
};


for (const [expression, expectedResult] of Object.entries(arithmeticExpressions)) {
  test(`arithmetic expression: ${expression}`, () => {
    expect(evaluate(parse(lex(expression)))).toBe(expectedResult);
  });
}
