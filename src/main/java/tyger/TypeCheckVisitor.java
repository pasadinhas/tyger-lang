package tyger;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.BreakExpressionContext;
import tyger.TygerParser.GroupedExpressionContext;
import tyger.TygerParser.IdentifierExpressionContext;
import tyger.TygerParser.IfExpressionContext;
import tyger.TygerParser.LiteralExpressionContext;
import tyger.TygerParser.PostfixUnaryExpressionContext;
import tyger.TygerParser.PrefixUnaryExpressionContext;
import tyger.TygerParser.PrintExpressionContext;
import tyger.TygerParser.ProgContext;
import tyger.TygerParser.VariableDeclarationExpressionContext;
import tyger.TygerParser.WhileExpressionContext;

public class TypeCheckVisitor extends TygerBaseVisitor<TypeCheckVisitor.Type> {

    private static final String COLOR_BRIGHT_RED = "\u001b[31;1m";
    private static final String COLOR_RESET = "\u001b[0m";

    private static final int COMPILER_ERROR_LINES_AROUND = 7;

    private static final Logger logger = LoggerFactory.getLogger(TypeCheckVisitor.class);
    
    /**
     * Stack of last seen loops. Used to know to which loop a break expression belongs.
     */
    private final Stack<WhileExpressionContext> loopStack = new Stack<>();

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

    private String filename;
    private String source;
    private Map<String, Type> variables = new HashMap<>();

    public TypeCheckVisitor(String filename, String source) {
        this.filename = filename;
        this.source = source;
    }

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

    @Override
    public Type visitPrefixUnaryExpression(PrefixUnaryExpressionContext ctx) {
        PrefixUnaryOperation operation = new PrefixUnaryOperation(ctx.op.getText(), ctx.expression().accept(this));

        if (VALID_PREFIX_UNARY_OPERATIONS.containsKey(operation)) {
            return VALID_PREFIX_UNARY_OPERATIONS.get(operation);
        }

        return compiler_error(ctx, "Prefix unary operator '%s' is not applicable to type: %s", operation.operator, operation.type);
    }

    public Type visitPostfixUnaryExpression(PostfixUnaryExpressionContext ctx) {
        PostfixUnaryOperation operation = new PostfixUnaryOperation(ctx.op.getText(), ctx.expression().accept(this));

        if (!VALID_POSTFIX_UNARY_OPERATIONS.containsKey(operation)) {
            return compiler_error(ctx, "Postfix unary operator '%s' is not applicable to type: %s", operation.operator, operation.type);
        }
        
        return VALID_POSTFIX_UNARY_OPERATIONS.get(operation);
    };

    @Override
    public Type visitBinaryExpression(BinaryExpressionContext ctx) {
        BinaryOperation operation = new BinaryOperation(ctx.op.getText(), ctx.left.accept(this), ctx.right.accept(this));

        if (VALID_BINARY_OPERATIONS.containsKey(operation)) {
            return VALID_BINARY_OPERATIONS.get(operation);
        }

        return compiler_error(ctx, "Operator %s is not applicable to types: %s and %s", operation.operator, operation.left, operation.right);
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

        return compiler_error(ctx, "Undefined variable: %s", variableName);
    }

    @Override
    public Type visitAssignmentExpression(AssignmentExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        if (!variables.containsKey(variableName)) {
            compiler_error(ctx, "Cannot assign to undeclared variable '%s'", variableName);    
        }

        Type declaredType = variables.get(variableName);
        Type assignedType = ctx.expression().accept(this);

        if (!declaredType.isAssignableFrom(assignedType)) {
            return compiler_error(ctx, "Cannot assign value of type %s to variable '%s' of type %s", assignedType, variableName, declaredType);
        } 

        return declaredType;
    }

    @Override
    public Type visitIfExpression(IfExpressionContext ctx) {
        Type conditionType = ctx.condition.accept(this);
        if (!conditionType.equals(Type.BOOLEAN)) {
            compiler_error(ctx.condition, "Condition of if expression must be a boolean. Got: %s", conditionType);
        }

        Type blockType = ctx.block.accept(this);
        Type elseType = ctx.elseif != null
            ? ctx.elseif.accept(this)
            : ctx.elseBlock.accept(this);

        if (!elseType.equals(blockType)) {
            return compiler_error(ctx, "If expression must return the same type in all branches. Got %s and %s", blockType, elseType);
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
            compiler_error(ctx, "Variable '%s' has already been declared.", variableName);
        }

        String declaredTypeStr = ctx.typeIdentifier().getText();
        if (!TYPE_MAP.containsKey(declaredTypeStr)) {
            compiler_error(ctx, "Variable '%s' is being declared with an undefined type: %s", variableName, declaredTypeStr);
        }

        Type declaredType = TYPE_MAP.get(declaredTypeStr);
        Type expressionType = ctx.expression().accept(this);

        if (!declaredType.isAssignableFrom(expressionType)) {
            compiler_error(ctx, "Cannot assign value of type %s to variable '%s' of type %s.", expressionType, variableName, declaredType);
        }

        variables.put(variableName, declaredType);
        return declaredType;
    }

    private <T> T compiler_error(ParserRuleContext ctx, String format, Object... args) {
        int errorStartLine = ctx.start.getLine();
        int errorStartLineStartChar = ctx.start.getCharPositionInLine();
        int errorStopLine = ctx.stop.getLine();
        int errorStopLineStopChar = ctx.stop.getCharPositionInLine() + ctx.stop.getText().length();
        
        String[] sourceCodeLines = source.split("\n");

        int outputStartLine = Math.max(1, errorStartLine - COMPILER_ERROR_LINES_AROUND);
        int outputStopLine = Math.min(sourceCodeLines.length, errorStopLine + COMPILER_ERROR_LINES_AROUND);
        
        int lineNumberDigits = outputStopLine / 10;

        StringBuilder errorMessage = new StringBuilder("\n")
                .append(COLOR_BRIGHT_RED)
                .append("Compiler error [")
                .append(filename)
                .append(":")
                .append(errorStartLine)
                .append("]: ")
                .append(COLOR_RESET)
                .append(String.format(format, args))
                .append("\n\n");



        for (int lineNumber = outputStartLine; lineNumber <= outputStopLine; lineNumber++) {
            int lineNumberWhitespacePadding = lineNumberDigits - lineNumber / 10;
            for (int i = 0; i < lineNumberWhitespacePadding; i++) {
                errorMessage.append(' ');
            }

            errorMessage.append(lineNumber)
                .append(": ");

            String line = sourceCodeLines[lineNumber - 1];

            if (lineNumber >= errorStartLine && lineNumber <= errorStopLine) {
                if (lineNumber > errorStartLine && lineNumber <= errorStopLine) {
                    errorMessage.append(COLOR_BRIGHT_RED);
                }

                for (int charIndex = 0; charIndex < line.length(); charIndex++) {
                    if (lineNumber == errorStartLine && charIndex == errorStartLineStartChar) {
                        errorMessage.append(COLOR_BRIGHT_RED);
                    }

                    errorMessage.append(line.charAt(charIndex));

                    if (lineNumber == errorStopLine && charIndex == errorStopLineStopChar - 1) {
                        // Reset color
                        errorMessage.append(COLOR_RESET);
                    } 
                }
                errorMessage.append(COLOR_RESET);
                errorMessage.append('\n');
            } else {
                errorMessage.append(line).append('\n');
            }
        }

        logger.error("{}", errorMessage);
        
        throw new RuntimeException(String.format(format, args));
    }

    public Type visitWhileExpression(WhileExpressionContext ctx) {
        loopStack.push(ctx);
        
        Type conditionType = ctx.condition.accept(this);

        if (!conditionType.equals(Type.BOOLEAN)) {
            compiler_error(ctx.condition, "Condition of while expression must be a boolean. Got: %s", conditionType);
        }

        // while loop can return optionally if the condition never triggers.
        Type result = ctx.blockExpression().accept(this).toOptional(); 
        
        loopStack.pop();

        return result;
    };

    public Type visitBreakExpression(BreakExpressionContext ctx) {
        if (loopStack.isEmpty()) {
            return compiler_error(ctx, "Break expression must occur inside a loop.");
        }

        return ctx.expression() == null ? Type.OPTIONAL_ANY : ctx.expression().accept(this);
    };

    public Type visitPrintExpression(PrintExpressionContext ctx) {
        return ctx.expression().accept(this);
    };
}