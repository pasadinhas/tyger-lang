package tyger.ast.visitor;

import tyger.ast.AstNode;
import tyger.ast.Expression;
import tyger.ast.FunctionDeclaration;
import tyger.ast.Module;
import tyger.ast.expressions.*;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.BooleanLiteral;
import tyger.ast.expressions.literals.DecimalLiteral;
import tyger.ast.types.Type;

import java.util.Collection;

/**
 * Type-checks the AST and assigns types to each node.
 */
public class Binder implements AstVisitor<AstNode> {

    private ErrorReporter reporter;

    public Binder(final String filename, final String source_code) {
        this.reporter = new ErrorReporter(filename, source_code);
    }

    public <T> T compiler_error(AstNode.Loc loc, String format, Object... args) {
        return reporter.compiler_error(loc, format, args);
    }

    @Override
    public AstNode visit_module(final Module module) {
        return null;
    }

    @Override
    public AstNode visit_function_declaration(final FunctionDeclaration function_declaration) {
        return null;
    }

    @Override
    public AstNode visit_block(final Block block) {
        // scope_push
        final Collection<Expression> expressions = block.expressions();

        Type type_of_last_expression = Type.OptionalAny;
        for (final Expression expression : expressions) {
            type_of_last_expression = expression.accept(this).type();
        }
        // scope pop
        return block.bind(type_of_last_expression);
    }

    @Override
    public AstNode visit_if_expression(final IfExpression if_expression) {
        if_expression.condition().accept(this);
        if (if_expression.type() != Type.Boolean) {
            return compiler_error(
                    if_expression.condition().loc,
                    "Condition of if expression must be a boolean. Got: %s",
                    if_expression.condition().type()
            );
        }

        Type thenType = if_expression.then().accept(this).type();
        Type elseType = if_expression._else() != null
                ? if_expression._else().accept(this).type()
                : Type.OptionalAny;

        // TODO: see if the types are compatible

        return null;
    }

    @Override
    public AstNode visit_decimal_literal(final DecimalLiteral decimal_literal) {
        return decimal_literal.bind(Type.Integer);
    }

    @Override
    public AstNode visit_boolean_literal(final BooleanLiteral boolean_literal) {
        return boolean_literal.bind(Type.Boolean);
    }

    @Override
    public AstNode visit_function_call(final FunctionCall function_call) {
        return null;
    }

    @Override
    public AstNode visit_while_expression(final WhileExpression while_expression) {
        return null;
    }

    @Override
    public AstNode visit_assignment(final Assignment assignment) {
        return null;
    }

    @Override
    public AstNode visit_multiplication_expression(final Multiplication multiplication) {
        return null;
    }

    @Override
    public AstNode visit_identifier_access_expression(final NameExpression identifier_access_expression) {
        return null;
    }

    @Override
    public AstNode visit_division(final Division division) {
        return null;
    }

    @Override
    public AstNode visit_modulo(final Modulo modulo) {
        return null;
    }

    @Override
    public AstNode visit_addition(final Addition addition) {
        return null;
    }

    @Override
    public AstNode visit_subtraction(final Subtraction subtraction) {
        return null;
    }

    @Override
    public AstNode visit_left_shift(final LeftShift left_shift) {
        return null;
    }

    @Override
    public AstNode visit_right_shift(final RightShift right_shift) {
        return null;
    }

    @Override
    public AstNode visit_bit_and(final BitAnd bit_and) {
        return null;
    }

    @Override
    public AstNode visit_bit_or(final BitOr bit_or) {
        return null;
    }

    @Override
    public AstNode visit_bit_xor(final BitXor bit_xor) {
        return null;
    }

    @Override
    public AstNode visit_equals(final Equals equals) {
        return null;
    }

    @Override
    public AstNode visit_not_equals(final NotEquals not_equals) {
        return null;
    }

    @Override
    public AstNode visit_less_than(final LessThan less_than) {
        return null;
    }

    @Override
    public AstNode visit_less_than_or_equals(final LessThanOrEquals less_than_or_equals) {
        return null;
    }

    @Override
    public AstNode visit_greater_than(final GreaterThan greater_than) {
        return null;
    }

    @Override
    public AstNode visit_greater_than_or_equals(final GreaterThanOrEquals greater_than_or_equals) {
        return null;
    }

    @Override
    public AstNode visit_and(final And and) {
        return null;
    }

    @Override
    public AstNode visit_or(final Or or) {
        return null;
    }

    @Override
    public AstNode visit_variable_declaration(final VariableDeclaration variable_declaration) {
        return null;
    }
}
