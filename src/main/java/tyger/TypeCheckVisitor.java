package tyger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.ExpressionContext;
import tyger.TygerParser.GroupedExpressionContext;
import tyger.TygerParser.IdentifierExpressionContext;
import tyger.TygerParser.IfExpressionContext;
import tyger.TygerParser.LiteralExpressionContext;
import tyger.TygerParser.PrefixUnaryExpressionContext;
import tyger.TygerParser.ProgContext;

public class TypeCheckVisitor extends TygerBaseVisitor<Class<?>> {

    private static final Logger logger = LoggerFactory.getLogger(TypeCheckVisitor.class);

    private static final record PrefixUnaryOperation(String operator, Class<?> expression) {}
    private static final Map<PrefixUnaryOperation, Class<?>> VALID_PREFIX_UNARY_OPERATIONS = Map.ofEntries(
        Map.entry(new PrefixUnaryOperation("-", Long.class), Long.class),
        Map.entry(new PrefixUnaryOperation("not", Boolean.class), Boolean.class)
    );

    private static final record BinaryOperation(String operator, Class<?> left, Class<?> right) {}
    
    private static final Map<BinaryOperation, Class<?>> VALID_BINARY_OPERATIONS = Map.ofEntries(
        Map.entry(new BinaryOperation("+", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("-", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("/", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("*", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("%", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("^", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation(">>", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("<<", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("|", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("&", Long.class, Long.class), Long.class),
        Map.entry(new BinaryOperation("and", Boolean.class, Boolean.class), Boolean.class),
        Map.entry(new BinaryOperation("or", Boolean.class, Boolean.class), Boolean.class),
        Map.entry(new BinaryOperation("==", Boolean.class, Boolean.class), Boolean.class),
        Map.entry(new BinaryOperation("==", Long.class, Long.class), Boolean.class),
        Map.entry(new BinaryOperation("!=", Boolean.class, Boolean.class), Boolean.class),
        Map.entry(new BinaryOperation("!=", Long.class, Long.class), Boolean.class),
        Map.entry(new BinaryOperation(">=", Long.class, Long.class), Boolean.class),
        Map.entry(new BinaryOperation(">", Long.class, Long.class), Boolean.class),
        Map.entry(new BinaryOperation("<=", Long.class, Long.class), Boolean.class),
        Map.entry(new BinaryOperation("<", Long.class, Long.class), Boolean.class)
    );

    private Map<String, Class<?>> variables = new HashMap<>();

    @Override
    public Class<?> visitProg(ProgContext ctx) {
        return ctx.blockExpression().accept(this);
    }

    @Override
    public Class<?> visitBlockExpression(BlockExpressionContext ctx) {
        Class<?> last = null;
        for (var expression : ctx.expression()) {
            last = expression.accept(this);
        }
        return last;
    }

    private Class<?> error_binaryOperatorNotApplicable(BinaryOperation operation) {
        throw new RuntimeException("Operator '" + operation.operator + "' is not applicable to types: " + operation.left + " and " + operation.right);
    }

    private Class<?> error_prefixUnaryOperatorNotApplicable(PrefixUnaryOperation operation) {
        throw new RuntimeException("Prefix unary operator '" + operation.operator + "' is not applicable to type: " + operation.expression);
    }

    @Override
    public Class<?> visitPrefixUnaryExpression(PrefixUnaryExpressionContext ctx) {
        PrefixUnaryOperation operation = new PrefixUnaryOperation(ctx.op.getText(), ctx.expression().accept(this));

        if (VALID_PREFIX_UNARY_OPERATIONS.containsKey(operation)) {
            return VALID_PREFIX_UNARY_OPERATIONS.get(operation);
        }

        return error_prefixUnaryOperatorNotApplicable(operation);
    }

    @Override
    public Class<?> visitBinaryExpression(BinaryExpressionContext ctx) {
        logger.debug("Type Checker | Binary Expression | {} {} {}", ctx.left.getText(), ctx.op.getText(), ctx.right.getText());
        BinaryOperation operation = new BinaryOperation(ctx.op.getText(), ctx.left.accept(this), ctx.right.accept(this));

        if (VALID_BINARY_OPERATIONS.containsKey(operation)) {
            return VALID_BINARY_OPERATIONS.get(operation);
        }

        return error_binaryOperatorNotApplicable(operation);
    }

    @Override
    public Class<?> visitGroupedExpression(GroupedExpressionContext ctx) {
        return ctx.expression().accept(this);
    }

    @Override
    public Class<?> visitLiteralExpression(LiteralExpressionContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return Long.class;
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return Boolean.class;
        }
        throw new RuntimeException("Could not detect type of token: " + ctx.getText());
    }

    @Override
    public Class<?> visitIdentifierExpression(IdentifierExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        if (variables.containsKey(variableName)) {
            return variables.get(variableName);
        }

        throw new RuntimeException("Undefined variable: " + variableName);
    }

    @Override
    public Class<?> visitAssignmentExpression(AssignmentExpressionContext ctx) {
        logger.debug("Type Checker | Assignment Expression | {} {} {}", ctx.identifier().getText(), ctx.expression().getText());

        String variableName = ctx.identifier().getText();
        Class<?> type = ctx.expression().accept(this);

        if (variables.containsKey(variableName)) {
            Class<?> previousType = variables.get(variableName);
            if (type.equals(previousType)) {
                return type;
            } else {
                throw new RuntimeException(String.format(
                    "Cannot assign value of type %s to variable '%s' of type %s",
                    type, variableName, previousType
                ));
            }
        } else {
            variables.put(variableName, type);
            return type;
        }
    }

    @Override
    public Class<?> visitIfExpression(IfExpressionContext ctx) {
        Class<?> conditionType = ctx.condition.accept(this);
        if (!conditionType.equals(Boolean.class)) {
            throw new RuntimeException("Condition of if expression must be a boolean. Got: " + conditionType);
        }

        Class<?> blockType = ctx.block.accept(this);
        Class<?> elseType = ctx.elseif != null
            ? ctx.elseif.accept(this)
            : ctx.elseBlock.accept(this);

        if (!elseType.equals(blockType)) {
            throw new RuntimeException(String.format(
                "If expression must return the same type in all branches. Got %s and %s",
                blockType, elseType
            ));
        }

        return blockType;
    }

}