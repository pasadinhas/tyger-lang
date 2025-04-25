import { assert } from "../assert.ts";
import type { Program, Statement, NumericLiteral, BinaryExpression } from "../ast/ast.ts";

export function evaluate(statement: Statement) {
  switch (statement.kind) {
    case "Program":
      return evaluateProgram(statement as Program);
    case "NumericLiteral":
      return evaluateNumericLiteral(statement as NumericLiteral);
    case "BinaryExpression":
      return evaluateBinaryExpression(statement as BinaryExpression);
    case "Identifier":
      assert(false, `Interpretation for AST Node ${statement.kind} is not implemented yet.`);
  }
}

function evaluateProgram(program: Program) {
  let lastEvaluation: any = null;
  for (const statement of program.body) {
    lastEvaluation = evaluate(statement);
  }
  return lastEvaluation;
}

function evaluateNumericLiteral(numericLiteral: NumericLiteral) {
  return numericLiteral.value;
}

function evaluateBinaryExpression(binaryExpression: BinaryExpression) {
  const left = evaluate(binaryExpression.left);
  const right = evaluate(binaryExpression.right);
  switch (binaryExpression.operator) {
    case "+":
      return left + right;
    case "-":
      return left - right;
    case "*":
      return left * right;
    case "/":
      return left / right; // TODO: handle division by zero.
    case "%":
      return left % right;
    default:
      assert(false, `Interpretation of Binary operator ${binaryExpression.operator} is not implemented yet.`);
  }
}
