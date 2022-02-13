package tyger.codegen;

import tyger.ast.FunctionDeclaration;
import tyger.ast.Module;
import tyger.ast.expressions.*;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.BooleanLiteral;
import tyger.ast.expressions.literals.DecimalLiteral;
import tyger.ast.visitor.AstVisitor;

public class BytecodeGenerator implements AstVisitor<Byte[]> {


    @Override
    public Byte[] visit_module(final Module module) {
        return null;
    }

    @Override
    public Byte[] visit_function_declaration(final FunctionDeclaration function_declaration) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_block(final Block block) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_if_expression(final IfExpression if_expression) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_decimal_literal(final DecimalLiteral decimal_literal) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_boolean_literal(final BooleanLiteral boolean_literal) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_function_call(final FunctionCall function_call) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_while_expression(final WhileExpression while_expression) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_assignment(final Assignment assignment) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_multiplication_expression(final Multiplication multiplication) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_identifier_access_expression(final NameExpression identifier_access_expression) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_division(final Division division) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_modulo(final Modulo modulo) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_addition(final Addition addition) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_subtraction(final Subtraction subtraction) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_left_shift(final LeftShift left_shift) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_right_shift(final RightShift right_shift) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_bit_and(final BitAnd bit_and) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_bit_or(final BitOr bit_or) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_bit_xor(final BitXor bit_xor) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_equals(final Equals equals) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_not_equals(final NotEquals not_equals) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_less_than(final LessThan less_than) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_less_than_or_equals(final LessThanOrEquals less_than_or_equals) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_greater_than(final GreaterThan greater_than) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_greater_than_or_equals(final GreaterThanOrEquals greater_than_or_equals) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_and(final And and) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_or(final Or or) {
        return new Byte[0];
    }

    @Override
    public Byte[] visit_variable_declaration(final VariableDeclaration variable_declaration) {
        return new Byte[0];
    }
}
