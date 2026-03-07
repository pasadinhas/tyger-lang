import { expect, test } from "vitest";
import { evaluate, RuntimeScope } from "../src/interpreter/interpreter";
import { parse } from "../src/frontend/parser";
import { lex } from "../src/frontend/lexer";

// Note: these are not unit tests. They depend on the correct behaviour of the lexer and the parser.

function numberValue(n: number) {
  return {
    type: "number",
    value: n,
    mutable: true,
  };
}
const arithmeticExpressions = {
"1 + 1": numberValue(2),
  "1 + 2 * 3": numberValue(7),

  "1 + 6 / 3": numberValue(3),
  "1 + 6 % 3": numberValue(1),
  "1 - 5": numberValue(-4),
  "1 - 2 * 3": numberValue(-5),
  "1 - 4 / 2": numberValue(-1),
  "1 - 4 % 2": numberValue(1),
};

for (const [expression, expectedResult] of Object.entries(arithmeticExpressions)) {
  test(`arithmetic expression: ${expression}`, () => {
    expect(evaluate(parse(lex(`${expression};`)), new RuntimeScope())).toStrictEqual(expectedResult);
  });
}

test(`variable declaration`, () => {
  expect(evaluate(parse(lex("let x = 45; x;")), new RuntimeScope())).toStrictEqual(numberValue(45));
});
