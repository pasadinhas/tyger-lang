package tyger.ast.visitor;

import tyger.ast.*;
import tyger.ast.Module;
import tyger.ast.expressions.*;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.*;

public interface AstVisitor<T> {
    T visit_module(Module module);
    T visit_function_declaration(FunctionDeclaration function_declaration);
    T visit_block(Block block);
    T visit_if_expression(IfExpression if_expression);
    T visit_decimal_literal(DecimalLiteral decimal_literal);
    T visit_boolean_literal(BooleanLiteral boolean_literal);
    T visit_function_call(FunctionCall function_call);
    T visit_while_expression(WhileExpression while_expression);
    T visit_assignment(Assignment assignment);
    T visit_identifier_access_expression(NameExpression identifier_access_expression);
    T visit_multiplication(Multiplication multiplication);
    T visit_division(Division division);
    T visit_modulo(Modulo modulo);
    T visit_addition(Addition addition);
    T visit_subtraction(Subtraction subtraction);
    T visit_left_shift(LeftShift left_shift);
    T visit_right_shift(RightShift right_shift);
    T visit_bit_and(BitAnd bit_and);
    T visit_bit_or(BitOr bit_or);
    T visit_bit_xor(BitXor bit_xor);
    T visit_equals(Equals equals);
    T visit_not_equals(NotEquals not_equals);
    T visit_less_than(LessThan less_than);
    T visit_less_than_or_equals(LessThanOrEquals less_than_or_equals);
    T visit_greater_than(GreaterThan greater_than);
    T visit_greater_than_or_equals(GreaterThanOrEquals greater_than_or_equals);
    T visit_and(And and);
    T visit_or(Or or);
    T visit_variable_declaration(VariableDeclaration variable_declaration);
}
