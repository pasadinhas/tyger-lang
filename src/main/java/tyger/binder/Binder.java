package tyger.binder;

import tyger.TypeCheckVisitor;
import tyger.ast.AstNode;
import tyger.ast.Expression;
import tyger.ast.FunctionDeclaration;
import tyger.ast.FunctionDeclaration.Parameter;
import tyger.ast.Module;
import tyger.ast.expressions.*;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.BooleanLiteral;
import tyger.ast.expressions.literals.DecimalLiteral;
import tyger.ast.types.Type;
import tyger.ast.visitor.AstVisitor;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Type-checks the AST and assigns types to each node.
 */
public class Binder implements AstVisitor<AstNode> {

    private final ErrorReporter reporter;
    private final Scope scope;

    public Binder(final String filename, final String source_code) {
        this.reporter = new ErrorReporter(filename, source_code);
        this.scope = new Scope();
    }

    public <T> T compiler_error(AstNode.Loc loc, String format, Object... args) {
        return reporter.compiler_error(loc, format, args);
    }

    private AstNode scoped(Supplier<AstNode> supplier) {
        scope.push();
        final AstNode result = supplier.get();
        scope.pop();
        return result;
    }

    @Override
    public AstNode visit_module(final Module module) {
        return scoped(() -> {
            // Add functions to global scope.
            module.function_declarations().forEach(function -> {
                if (scope.get_function(function.name()).isPresent()) {
                    compiler_error(function.loc, "Function with name %s has already been declared.", function.name());
                }

                scope.declare_function(function.name(), function);
            });

            // Actually bind and type-check everything
            for (final FunctionDeclaration function_declaration : module.function_declarations()) {
                function_declaration.accept(this);
            }

            return module;
        });
    }

    @Override
    public AstNode visit_function_declaration(final FunctionDeclaration function_declaration) {
        return scoped(() -> {
            for (final Parameter parameter : function_declaration.parameters()) {
                scope.declare_variable(parameter.name(), parameter.type());
            }

            final Type body_type = function_declaration.body().accept(this).type();

            if (!function_declaration.return_type().is_assignable_from(body_type)) {
                return compiler_error(
                        function_declaration.loc,
                        "Expected function to return %s but got: %s",
                        function_declaration.return_type(),
                        body_type
                );
            }

            return function_declaration.bind(function_declaration.return_type());
        });
    }

    @Override
    public AstNode visit_block(final Block block) {
        return scoped(() -> {
            final Collection<Expression> expressions = block.expressions();

            Type type_of_last_expression = Type.OptionalAny;
            for (final Expression expression : expressions) {
                type_of_last_expression = expression.accept(this).type();
            }

            return block.bind(type_of_last_expression);
        });
    }

    @Override
    public AstNode visit_if_expression(final IfExpression if_expression) {
        final Type condition_type = if_expression.condition().accept(this).type();
        if (condition_type != Type.Boolean) {
            return compiler_error(if_expression.condition().loc, "Condition of if expression must be a boolean. Got: %s", condition_type);
        }

        Type then_type = if_expression.then().accept(this).type();
        Type else_type = if_expression._else() != null
                ? if_expression._else().accept(this).type()
                : Type.OptionalAny;

        final Type if_expression_type = Type.lca(then_type, else_type);

        if (if_expression_type == null) {
            return compiler_error(if_expression.loc, "If expression must return compatible types in both branches. Got %s and %s", then_type, else_type);
        }

        return if_expression.bind(if_expression_type);
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
        final Optional<FunctionDeclaration> optional_function_declaration = scope.get_function(function_call.name());

        if (optional_function_declaration.isEmpty()) {
            return compiler_error(function_call.loc, "Unknown function: %s", function_call.name());
        }

        final FunctionDeclaration function_declaration = optional_function_declaration.get();

        final int received_parameters = function_call.parameters().size();
        final int expected_parameters = function_declaration.parameters().size();
        if (received_parameters != expected_parameters) {
            return compiler_error(
                    function_call.loc,
                    "Function %s expects %d arguments but got %d",
                    function_call.name(),
                    expected_parameters, received_parameters
            );
        }

        final var received_parameters_iterator = function_call.parameters().iterator();
        final var expected_parameters_iterator = function_declaration.parameters().iterator();

        while (received_parameters_iterator.hasNext()) {
            Expression received = received_parameters_iterator.next();
            Parameter expected = expected_parameters_iterator.next();

            Type received_type = received.accept(this).type();
            Type expected_type = expected.type();

            if (!expected_type.is_assignable_from(received_type)) {
                return compiler_error(
                        received.loc,
                        "Expected argument %s of function %s (%s argument) to be of type %s but got %s",
                        expected.name(),
                        function_call.name(),
                        ErrorReporter.ordinal(function_declaration.parameters().indexOf(expected) + 1),
                        expected_type,
                        received_type
                );
            }
        }

        return function_call.bind(function_declaration.return_type());
    }

    @Override
    public AstNode visit_while_expression(final WhileExpression while_expression) {
        final Type condition_type = while_expression.condition().accept(this).type();

        if (condition_type != Type.Boolean) {
            compiler_error(while_expression.condition().loc, "Condition of while expression must be a boolean. Got: %s", condition_type);
        }

        final Type code_type = while_expression.code().accept(this).type();

        // while loop can return optionally if the condition never triggers.
        return while_expression.bind(code_type.to_optional());
    }

    @Override
    public AstNode visit_identifier_access_expression(final NameExpression identifier_access_expression) {
        final String name = identifier_access_expression.name();

        final Optional<Type> optional_variable_type = scope.get_variable(name);
        if (optional_variable_type.isEmpty()) {
            return compiler_error(identifier_access_expression.loc, "Undefined variable: %s", name);
        }

        return identifier_access_expression.bind(optional_variable_type.get());
    }

    @Override
    public AstNode visit_assignment(final Assignment assignment) {
        if (!(assignment.left() instanceof NameExpression)) {
            compiler_error(assignment.left().loc, "Left-side of an assignment must be a variable");
        }

        final NameExpression variable = (NameExpression) assignment.left();
        final String variable_name = variable.name();

        final Optional<Type> optional_variable = scope.get_variable(variable_name);
        if (optional_variable.isEmpty()) {
            compiler_error(assignment.loc, "Cannot assign to undeclared variable '%s'", variable_name);
        }

        final Type declared_type = optional_variable.get();
        final Type expression_type = assignment.right().accept(this).type();

        if (!declared_type.is_assignable_from(expression_type)) {
            return compiler_error(assignment.loc, "Cannot assign value of type %s to variable '%s' of type %s", expression_type, variable_name, declared_type);
        }

        return assignment.bind(declared_type);
    }

    record BinaryOperation(Type left, Type right) {}
    private AstNode visit_binary_expression(final BinaryExpression binary_expression, Map<BinaryOperation, Type> valid_operations) {
        final Type left_type = binary_expression.left().accept(this).type();
        final Type right_type = binary_expression.right().accept(this).type();

        final BinaryOperation binary_operation = new BinaryOperation(left_type, right_type);

        if (!valid_operations.containsKey(binary_operation)) {
            return compiler_error(
                    binary_expression.loc,
                    "Operator %s is not applicable to types: %s and %s",
                    binary_expression.op(),
                    binary_operation.left,
                    binary_operation.right
            );
        }

        return binary_expression.bind(valid_operations.get(binary_operation));
    }

    private AstNode visit_arithmetic_binary_expression(final BinaryExpression binary_expression) {
        return visit_binary_expression(binary_expression, Map.of(
                new BinaryOperation(Type.Integer, Type.Integer), Type.Integer
        ));
    }

    private AstNode visit_equality_binary_expression(final BinaryExpression binary_expression) {
        return visit_binary_expression(binary_expression, Map.of(
                new BinaryOperation(Type.Integer, Type.Integer), Type.Boolean,
                new BinaryOperation(Type.Optional(Type.Integer), Type.Integer),Type.Boolean,
                new BinaryOperation(Type.Integer, Type.Optional(Type.Integer)), Type.Boolean,
                new BinaryOperation(Type.Optional(Type.Integer), Type.Optional(Type.Integer)), Type.Boolean,
                new BinaryOperation(Type.Boolean, Type.Boolean), Type.Boolean,
                new BinaryOperation(Type.Optional(Type.Boolean), Type.Boolean), Type.Boolean,
                new BinaryOperation(Type.Boolean, Type.Optional(Type.Boolean)), Type.Boolean,
                new BinaryOperation(Type.Optional(Type.Boolean), Type.Optional(Type.Boolean)), Type.Boolean
        ));
    }

    private AstNode visit_comparison_binary_expression(final BinaryExpression binary_expression) {
        return visit_binary_expression(binary_expression, Map.of(
                new BinaryOperation(Type.Integer, Type.Integer), Type.Boolean
        ));
    }

    private AstNode visit_logical_binary_expression(final BinaryExpression binary_expression) {
        return visit_binary_expression(binary_expression, Map.of(
                new BinaryOperation(Type.Boolean, Type.Boolean), Type.Boolean
        ));
    }

    @Override
    public AstNode visit_multiplication(final Multiplication multiplication) {
        return visit_arithmetic_binary_expression(multiplication);
    }

    @Override
    public AstNode visit_division(final Division division) {
        return visit_arithmetic_binary_expression(division);
    }

    @Override
    public AstNode visit_modulo(final Modulo modulo) {
        return visit_arithmetic_binary_expression(modulo);
    }

    @Override
    public AstNode visit_addition(final Addition addition) {
        return visit_arithmetic_binary_expression(addition);
    }

    @Override
    public AstNode visit_subtraction(final Subtraction subtraction) {
        return visit_arithmetic_binary_expression(subtraction);
    }

    @Override
    public AstNode visit_left_shift(final LeftShift left_shift) {
        return visit_arithmetic_binary_expression(left_shift);
    }

    @Override
    public AstNode visit_right_shift(final RightShift right_shift) {
        return visit_arithmetic_binary_expression(right_shift);
    }

    @Override
    public AstNode visit_bit_and(final BitAnd bit_and) {
        return visit_arithmetic_binary_expression(bit_and);
    }

    @Override
    public AstNode visit_bit_or(final BitOr bit_or) {
        return visit_arithmetic_binary_expression(bit_or);
    }

    @Override
    public AstNode visit_bit_xor(final BitXor bit_xor) {
        return visit_arithmetic_binary_expression(bit_xor);
    }

    @Override
    public AstNode visit_equals(final Equals equals) {
        return visit_equality_binary_expression(equals);
    }

    @Override
    public AstNode visit_not_equals(final NotEquals not_equals) {
        return visit_equality_binary_expression(not_equals);
    }

    @Override
    public AstNode visit_less_than(final LessThan less_than) {
        return visit_comparison_binary_expression(less_than);
    }

    @Override
    public AstNode visit_less_than_or_equals(final LessThanOrEquals less_than_or_equals) {
        return visit_comparison_binary_expression(less_than_or_equals);
    }

    @Override
    public AstNode visit_greater_than(final GreaterThan greater_than) {
        return visit_comparison_binary_expression(greater_than);
    }

    @Override
    public AstNode visit_greater_than_or_equals(final GreaterThanOrEquals greater_than_or_equals) {
        return visit_comparison_binary_expression(greater_than_or_equals);
    }

    @Override
    public AstNode visit_and(final And and) {
        return visit_logical_binary_expression(and);
    }

    @Override
    public AstNode visit_or(final Or or) {
        return visit_logical_binary_expression(or);
    }

    @Override
    public AstNode visit_variable_declaration(final VariableDeclaration variable_declaration) {
        final String name = variable_declaration.name();
        final Optional<Type> optional_variable = scope.get_variable_in_current_scope(name);
        if (optional_variable.isPresent()) {
            compiler_error(variable_declaration.loc, "Variable '%s' has already been declared in this scope.", name);
        }

        final Type declared_type = variable_declaration.declared_type();
        final Type expression_type = variable_declaration.expression().accept(this).type();

        if (!declared_type.is_assignable_from(expression_type)) {
            compiler_error(variable_declaration.loc, "Cannot assign value of type %s to variable '%s' of type %s.", expression_type, name, declared_type);
        }

        scope.declare_variable(name, declared_type);

        return variable_declaration.bind(declared_type);
    }
}
