package tyger;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.GroupedExpressionContext;
import tyger.TygerParser.IdentifierExpressionContext;
import tyger.TygerParser.IfExpressionContext;
import tyger.TygerParser.LiteralExpressionContext;
import tyger.TygerParser.PostfixUnaryExpressionContext;
import tyger.TygerParser.PrefixUnaryExpressionContext;
import tyger.TygerParser.ProgContext;
import tyger.TygerParser.VariableDeclarationExpressionContext;
import tyger.TygerParser.WhileExpressionContext;

public class TypeCheckVisitor extends TygerBaseVisitor<TypeCheckVisitor.Type> {

    private static final Logger logger = LoggerFactory.getLogger(TypeCheckVisitor.class);

    public static enum Type {
        INTEGER, 
        OPTIONAL_INTEGER,
        BOOLEAN,
        OPTIONAL_BOOLEAN,
        OPTIONAL_ANY; // used internally as the type of 'None'. Must be coerced to other optional types.

        Type toOptional() {
            return switch(this) {
                case OPTIONAL_ANY, OPTIONAL_BOOLEAN, OPTIONAL_INTEGER -> this;
                case INTEGER -> OPTIONAL_INTEGER;
                case BOOLEAN -> OPTIONAL_BOOLEAN;
            };
        }

        Type toNonOptional() {
            return switch(this) {
                case OPTIONAL_ANY -> throw new RuntimeException(String.format("Cannot convert %s to non-optional.", OPTIONAL_ANY));
                case INTEGER, BOOLEAN -> this;
                case OPTIONAL_INTEGER -> INTEGER;
                case OPTIONAL_BOOLEAN -> BOOLEAN;
            };
        }

        boolean isOptional() {
            return this.name().toLowerCase().startsWith("optional");
        }

        boolean isAssignableFrom(Type other) {
            return this.equals(other) || this.isOptional() && (other.equals(OPTIONAL_ANY) || this.toNonOptional().equals(other));
        }
    }

    private static final record PrefixUnaryOperation(String operator, Type type) {}
    private static final Map<PrefixUnaryOperation, Type> VALID_PREFIX_UNARY_OPERATIONS = Map.ofEntries(
        Map.entry(new PrefixUnaryOperation("-", Type.INTEGER), Type.INTEGER),
        Map.entry(new PrefixUnaryOperation("-", Type.OPTIONAL_INTEGER), Type.OPTIONAL_INTEGER),
        Map.entry(new PrefixUnaryOperation("--", Type.INTEGER), Type.INTEGER),
        Map.entry(new PrefixUnaryOperation("--", Type.OPTIONAL_INTEGER), Type.OPTIONAL_INTEGER),
        Map.entry(new PrefixUnaryOperation("++", Type.INTEGER), Type.INTEGER),
        Map.entry(new PrefixUnaryOperation("++", Type.OPTIONAL_INTEGER), Type.OPTIONAL_INTEGER),
        Map.entry(new PrefixUnaryOperation("not", Type.BOOLEAN), Type.BOOLEAN),
        Map.entry(new PrefixUnaryOperation("not", Type.OPTIONAL_BOOLEAN), Type.OPTIONAL_BOOLEAN)
    );

    private static final record PostfixUnaryOperation(String operator, Type type) {}
    private static final Map<PostfixUnaryOperation, Type> VALID_POSTFIX_UNARY_OPERATIONS = Map.ofEntries(
        Map.entry(new PostfixUnaryOperation("--", Type.INTEGER), Type.INTEGER),
        Map.entry(new PostfixUnaryOperation("--", Type.OPTIONAL_INTEGER), Type.OPTIONAL_INTEGER),
        Map.entry(new PostfixUnaryOperation("++", Type.INTEGER), Type.INTEGER),
        Map.entry(new PostfixUnaryOperation("++", Type.OPTIONAL_INTEGER), Type.OPTIONAL_INTEGER)
    );

    private static final record BinaryOperation(String operator, Type left, Type right) {}
    
    private static final Map<BinaryOperation, Type> VALID_BINARY_OPERATIONS = new HashMap<>();

    static {
        // Add operations without OPTIONAL types.
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("+", Type.INTEGER, Type.INTEGER), Type.INTEGER);        
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("-", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("/", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("*", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("%", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("^", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation(">>", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("<<", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("|", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("&", Type.INTEGER, Type.INTEGER), Type.INTEGER);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("and", Type.BOOLEAN, Type.BOOLEAN), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("or", Type.BOOLEAN, Type.BOOLEAN), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("==", Type.BOOLEAN, Type.BOOLEAN), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("==", Type.INTEGER, Type.INTEGER), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("!=", Type.BOOLEAN, Type.BOOLEAN), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("!=", Type.INTEGER, Type.INTEGER), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation(">=", Type.INTEGER, Type.INTEGER), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation(">", Type.INTEGER, Type.INTEGER), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("<=", Type.INTEGER, Type.INTEGER), Type.BOOLEAN);
        VALID_BINARY_OPERATIONS.put(new BinaryOperation("<", Type.INTEGER, Type.INTEGER), Type.BOOLEAN);

        // Add operations with OPTIONAL types.
        Map<BinaryOperation, Type> optionalBinaryOperations = new HashMap<>();
        VALID_BINARY_OPERATIONS.entrySet().forEach(entry -> {
            BinaryOperation operation = entry.getKey();
            String op = operation.operator();
            Type left = operation.left();
            Type right = operation.right();
            Type result = entry.getValue();

            optionalBinaryOperations.put(new BinaryOperation(op, left.toOptional(), right), result.toOptional());
            optionalBinaryOperations.put(new BinaryOperation(op, left, right.toOptional()), result.toOptional());
            optionalBinaryOperations.put(new BinaryOperation(op, left.toOptional(), right.toOptional()), result.toOptional());
        });

        VALID_BINARY_OPERATIONS.putAll(optionalBinaryOperations);
    }

    private Map<String, Type> variables = new HashMap<>();

    @Override
    public Type visitProg(ProgContext ctx) {
        return ctx.blockExpression().accept(this);
    }

    @Override
    public Type visitBlockExpression(BlockExpressionContext ctx) {
        Type last = null;
        for (var expression : ctx.expression()) {
            last = expression.accept(this);
        }
        return last;
    }

    private Type error_binaryOperatorNotApplicable(BinaryOperation operation) {
        throw new RuntimeException("Operator '" + operation.operator + "' is not applicable to types: " + operation.left + " and " + operation.right);
    }

    private Type error_prefixUnaryOperatorNotApplicable(PrefixUnaryOperation operation) {
        throw new RuntimeException("Prefix unary operator '" + operation.operator + "' is not applicable to type: " + operation.type);
    }

    @Override
    public Type visitPrefixUnaryExpression(PrefixUnaryExpressionContext ctx) {
        PrefixUnaryOperation operation = new PrefixUnaryOperation(ctx.op.getText(), ctx.expression().accept(this));

        if (VALID_PREFIX_UNARY_OPERATIONS.containsKey(operation)) {
            return VALID_PREFIX_UNARY_OPERATIONS.get(operation);
        }

        return error_prefixUnaryOperatorNotApplicable(operation);
    }

    public Type visitPostfixUnaryExpression(PostfixUnaryExpressionContext ctx) {
        PostfixUnaryOperation operation = new PostfixUnaryOperation(ctx.op.getText(), ctx.expression().accept(this));

        if (!VALID_POSTFIX_UNARY_OPERATIONS.containsKey(operation)) {
            return compiler_error("Postfix unary operator '%s' is not applicable to type: %s", operation.operator, operation.type);
        }
        
        return VALID_POSTFIX_UNARY_OPERATIONS.get(operation);
    };

    @Override
    public Type visitBinaryExpression(BinaryExpressionContext ctx) {
        BinaryOperation operation = new BinaryOperation(ctx.op.getText(), ctx.left.accept(this), ctx.right.accept(this));

        if (VALID_BINARY_OPERATIONS.containsKey(operation)) {
            return VALID_BINARY_OPERATIONS.get(operation);
        }

        return error_binaryOperatorNotApplicable(operation);
    }

    @Override
    public Type visitGroupedExpression(GroupedExpressionContext ctx) {
        return ctx.expression().accept(this);
    }

    @Override
    public Type visitLiteralExpression(LiteralExpressionContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return Type.INTEGER;
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return Type.BOOLEAN;
        } else if (ctx.NONE_LITERAL() != null) {
            return Type.OPTIONAL_ANY;
        }
        throw new RuntimeException("Could not detect type of token: " + ctx.getText());
    }

    @Override
    public Type visitIdentifierExpression(IdentifierExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        if (variables.containsKey(variableName)) {
            return variables.get(variableName);
        }

        throw new RuntimeException("Undefined variable: " + variableName);
    }

    @Override
    public Type visitAssignmentExpression(AssignmentExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        if (!variables.containsKey(variableName)) {
            compiler_error("Cannot assign to undeclared variable '%s'", variableName);    
        }

        Type declaredType = variables.get(variableName);
        Type assignedType = ctx.expression().accept(this);

        if (!declaredType.isAssignableFrom(assignedType)) {
            return compiler_error("Cannot assign value of type %s to variable '%s' of type %s", assignedType, variableName, declaredType);
        } 

        return declaredType;
    }

    @Override
    public Type visitIfExpression(IfExpressionContext ctx) {
        Type conditionType = ctx.condition.accept(this);
        if (!conditionType.equals(Type.BOOLEAN)) {
            throw new RuntimeException("Condition of if expression must be a boolean. Got: " + conditionType);
        }

        Type blockType = ctx.block.accept(this);
        Type elseType = ctx.elseif != null
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

    private static final Map<String, Type> TYPE_MAP = Map.of(
        "int", Type.INTEGER,
        "int?", Type.OPTIONAL_INTEGER,
        "bool", Type.BOOLEAN,
        "bool?", Type.OPTIONAL_BOOLEAN
    );

    @Override
    public Type visitVariableDeclarationExpression(VariableDeclarationExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        if (variables.containsKey(variableName)) {
            compiler_error("Variable '%s' has already been declared.", variableName);
        }

        String declaredTypeStr = ctx.typeIdentifier().getText();
        if (!TYPE_MAP.containsKey(declaredTypeStr)) {
            compiler_error("Variable '%s' is being declared with an undefined type: %s", variableName, declaredTypeStr);
        }

        Type declaredType = TYPE_MAP.get(declaredTypeStr);
        Type expressionType = ctx.expression().accept(this);

        if (!declaredType.isAssignableFrom(expressionType)) {
            compiler_error("Cannot assign value of type %s to variable '%s' of type %s.", expressionType, variableName, declaredType);
        }

        variables.put(variableName, declaredType);
        return declaredType;
    }

    private <T> T compiler_error(String format, Object... args) {
        throw new RuntimeException(String.format(format, args));
    }

    public Type visitWhileExpression(WhileExpressionContext ctx) {
        Type conditionType = ctx.condition.accept(this);
        if (!conditionType.equals(Type.BOOLEAN)) {
            throw new RuntimeException("Condition of while expression must be a boolean. Got: " + conditionType);
        }

        return ctx.blockExpression().accept(this).toOptional(); // while loop can return optionally if the condition never triggers.
    };

}