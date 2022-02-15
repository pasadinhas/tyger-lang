package tyger.ast.visitor;

import tyger.ast.AstNode;
import tyger.ast.FunctionDeclaration;
import tyger.ast.Module;
import tyger.ast.expressions.*;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.BooleanLiteral;
import tyger.ast.expressions.literals.DecimalLiteral;

public class DefaultAstVisitor<T> implements AstVisitor<T> {

    public boolean allow_default_handling() {
        return true;
    }

    public T default_handling(AstNode node) {
        assert allow_default_handling();
        node.children().forEach(child -> child.accept(this));
        return null;
    }

    public T visit_module(Module module) {
        return default_handling(module);
    }

    public T visit_function_declaration(FunctionDeclaration function_declaration) {
        return default_handling(function_declaration);
    }

    public T visit_block(Block block) {
        return default_handling(block);
    }

    public T visit_if_expression(IfExpression if_expression) {
        return default_handling(if_expression);
    }

    public T visit_decimal_literal(DecimalLiteral decimal_literal) {
        return default_handling(decimal_literal);
    }

    public T visit_boolean_literal(BooleanLiteral boolean_literal) {
        return default_handling(boolean_literal);
    }

    public T visit_function_call(FunctionCall function_call) {
        return default_handling(function_call);
    }

    public T visit_while_expression(WhileExpression while_expression) {
        return default_handling(while_expression);
    }

    public T visit_assignment(Assignment assignment) {
        return default_handling(assignment);
    }

    public T visit_multiplication(Multiplication multiplication) {
        return default_handling(multiplication);
    }

    public T visit_identifier_access_expression(NameExpression identifier_access_expression) {
        return default_handling(identifier_access_expression);
    }

    public T visit_division(Division division) {
        return default_handling(division);
    }

    public T visit_modulo(Modulo modulo) {
        return default_handling(modulo);
    }

    public T visit_addition(Addition addition) {
        return default_handling(addition);
    }

    public T visit_subtraction(Subtraction subtraction) {
        return default_handling(subtraction);
    }

    public T visit_left_shift(LeftShift left_shift) {
        return default_handling(left_shift);
    }

    public T visit_right_shift(RightShift right_shift) {
        return default_handling(right_shift);
    }

    public T visit_bit_and(BitAnd bit_and) {
        return default_handling(bit_and);
    }

    public T visit_bit_or(BitOr bit_or) {
        return default_handling(bit_or);
    }

    public T visit_bit_xor(BitXor bit_xor) {
        return default_handling(bit_xor);
    }

    public T visit_equals(Equals equals) {
        return default_handling(equals);
    }

    public T visit_not_equals(NotEquals not_equals) {
        return default_handling(not_equals);
    }

    public T visit_less_than(LessThan less_than) {
        return default_handling(less_than);
    }

    public T visit_less_than_or_equals(LessThanOrEquals less_than_or_equals) {
        return default_handling(less_than_or_equals);
    }

    public T visit_greater_than(GreaterThan greater_than) {
        return default_handling(greater_than);
    }

    public T visit_greater_than_or_equals(GreaterThanOrEquals greater_than_or_equals) {
        return default_handling(greater_than_or_equals);
    }

    public T visit_and(And and) {
        return default_handling(and);
    }

    public T visit_or(Or or) {
        return default_handling(or);
    }

    public T visit_variable_declaration(VariableDeclaration variable_declaration) {
        return default_handling(variable_declaration);
    }
    
}
