package tyger;

import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tyger.TygerParser.ArgsListContext;
import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.FunctionDeclarationExpressionContext;
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

    private record Function(String name, Type type, Map<String, Type> arguments) {}
    // TODO: separate variables from function arguments. Cannot overshadow function arguments.
    private record Scope(Map<String, Type> variables, Map<String, Function> functions) {
        Scope() {
            this(new HashMap<>(), new HashMap<>());
        }
    }

    private static final String COLOR_BRIGHT_RED = "\u001b[31;1m";
    private static final String COLOR_RESET = "\u001b[0m";

    private static final int COMPILER_ERROR_LINES_AROUND = 7;

    private static final Logger logger = LoggerFactory.getLogger(TypeCheckVisitor.class);
    
    /**
     * Stack of last seen loops. Used to know to which loop a break expression belongs.
     */
    private final Stack<WhileExpressionContext> loopStack = new Stack<>();

    public enum Type {
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

        boolean isNotAssignableFrom(Type other) {
            return !isAssignableFrom(other);
        }

        /**
         * Returns the lowest common ancestor of both types, i.e. the most specific type that both types could be cast to.
         *
         * @return the lowest common ancestor of both types.
         */
        Optional<Type> lca(Type other) {
            if (this.equals(other)) {
                return Optional.of(this);
            }

            if (this.toOptional().equals(other) || other.toOptional().equals(this)) {
                return Optional.of(this.toOptional());
            }

            if (OPTIONAL_ANY.equals(this)) {
                return Optional.of(other.toOptional());
            }

            if (OPTIONAL_ANY.equals(other)) {
                return Optional.of(this.toOptional());
            }

            return Optional.empty();
        }
    }

    private record PrefixUnaryOperation(String operator, Type type) {}
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

    private record PostfixUnaryOperation(String operator, Type type) {}
    private static final Map<PostfixUnaryOperation, Type> VALID_POSTFIX_UNARY_OPERATIONS = Map.ofEntries(
        Map.entry(new PostfixUnaryOperation("--", Type.INTEGER), Type.INTEGER),
        Map.entry(new PostfixUnaryOperation("--", Type.OPTIONAL_INTEGER), Type.OPTIONAL_INTEGER),
        Map.entry(new PostfixUnaryOperation("++", Type.INTEGER), Type.INTEGER),
        Map.entry(new PostfixUnaryOperation("++", Type.OPTIONAL_INTEGER), Type.OPTIONAL_INTEGER)
    );

    private record BinaryOperation(String operator, Type left, Type right) {}
    
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
        VALID_BINARY_OPERATIONS.forEach((operation, result) -> {
            String op = operation.operator();
            Type left = operation.left();
            Type right = operation.right();

            optionalBinaryOperations.put(new BinaryOperation(op, left.toOptional(), right), result.toOptional());
            optionalBinaryOperations.put(new BinaryOperation(op, left, right.toOptional()), result.toOptional());
            optionalBinaryOperations.put(new BinaryOperation(op, left.toOptional(), right.toOptional()), result.toOptional());
        });

        VALID_BINARY_OPERATIONS.putAll(optionalBinaryOperations);
    }

    private final String filename;
    private final String source;
    private final LinkedList<Scope> scopes = new LinkedList<>();

    public TypeCheckVisitor(String filename, String source) {
        this.filename = filename;
        this.source = source;
    }

    private Optional<Type> findVariable(String name) {
        var iterator = scopes.iterator();
        while (iterator.hasNext()) {
            var variables = iterator.next().variables;
            if (variables.containsKey(name)) {
                return Optional.of(variables.get(name));
            }
        }
        return Optional.empty();
    }

    private Optional<Type> findVariableInCurrentScope(String name) {
        assert scopes.peek() != null;
        return Optional.ofNullable(scopes.peek().variables.get(name));
    }

    private void setVariable(String name, Type value) {
        var iterator = scopes.iterator();
        while (iterator.hasNext()) {
            var variables = iterator.next().variables;
            if (variables.containsKey(name)) {
                variables.put(name, value);
                return;
            }
        }

        assert scopes.peek() != null;
        scopes.peek().variables.put(name, value);
    }

    private void setVariableInCurrentScope(String name, Type value) {
        assert scopes.peek() != null;
        scopes.peek().variables.put(name, value);
    }

    private Optional<Function> findFunction(String name) {
        var iterator = scopes.iterator();
        while (iterator.hasNext()) {
            var functions = iterator.next().functions;
            if (functions.containsKey(name)) {
                return Optional.of(functions.get(name));
            }
        }
        return Optional.empty();
    }

    private void setFunction(String name, Type type, Map<String, Type> arguments) {
        assert scopes.peek() != null;
        scopes.peek().functions.put(name, new Function(name, type, arguments));
    }

    private void scope_push() {
        this.scopes.push(new Scope());
    }
    private void scope_pop() {
        this.scopes.pop();
    }

    @Override
    public Type visitProg(ProgContext ctx) {
        scope_push();

        ctx.functionDeclarationExpression().forEach(function -> {
            String functionName = function.identifier().getText();
            
            if (findFunction(functionName).isPresent()) {
                compiler_error(function.identifier(), "Function with name %s had already been defined.", functionName);
            }

            setFunction(functionName, function.typeIdentifier().accept(this), functionArguments(function.argsList()));
        });

        ctx.functionDeclarationExpression().forEach(function -> function.accept(this));

        var mainFunction = findFunction("main");
        if (mainFunction.isEmpty()) {
            return compiler_error(ctx, "No function main detected.");
        }

        scope_pop();
        return mainFunction.get().type;
    }

    private Map<String, Type> functionArguments(ArgsListContext ctx) {
        Map<String, Type> arguments = new HashMap<>();

        for (var argsList = ctx; argsList != null; argsList = argsList.argsList()) {
            arguments.put(
                    argsList.identifier().getText(),
                    argsList.typeIdentifier().accept(this)
            );
        }

        return arguments;
    }

    public Type visitFunctionDeclarationExpression(FunctionDeclarationExpressionContext ctx) {
        Type declaredType = ctx.typeIdentifier().accept(this);
        
        scope_push();
        functionArguments(ctx.argsList()).forEach(this::setVariable);
        
        Type bodyReturnType = ctx.blockExpression().accept(this);
        
        scope_pop();

        if (declaredType.isNotAssignableFrom(bodyReturnType)) {
            compiler_error(ctx, "Expected function to return %s but it returns %s instead.", declaredType, bodyReturnType);
        }

        return declaredType;
    }

    public Type visitTypeIdentifier(tyger.TygerParser.TypeIdentifierContext ctx) {
        String typeName = ctx.getText();

        if (!TYPE_MAP.containsKey(typeName)) {
            compiler_error(ctx, "Unknown type: %s", typeName);
        }

        return TYPE_MAP.get(typeName);
    }

    @Override
    public Type visitBlockExpression(BlockExpressionContext ctx) {
        scope_push();
        Type last = null;
        for (var expression : ctx.expression()) {
            last = expression.accept(this);
        }
        scope_pop();
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
    }

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
        var variable = findVariable(variableName);
        if (variable.isPresent()) {
            return variable.get();
        }

        return compiler_error(ctx, "Undefined variable: %s", variableName);
    }

    @Override
    public Type visitAssignmentExpression(AssignmentExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        var variable = findVariable(variableName);
        if (variable.isEmpty()) {
            compiler_error(ctx, "Cannot assign to undeclared variable '%s'", variableName);    
        }

        Type declaredType = variable.get();
        Type assignedType = ctx.expression().accept(this);

        if (declaredType.isNotAssignableFrom(assignedType)) {
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
        Type elseType = ctx.elseBlock != null
                ? ctx.elseBlock.accept(this)
                : Type.OPTIONAL_ANY;

        final Optional<Type> optionalExpressionType = blockType.lca(elseType);

        if (optionalExpressionType.isEmpty()) {
            return compiler_error(ctx, "If expression must return compatible types in both branches. Got %s and %s", blockType, elseType);
        }

        return optionalExpressionType.get();
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
        var variable = findVariableInCurrentScope(variableName);
        if (variable.isPresent()) {
            compiler_error(ctx, "Variable '%s' has already been declared.", variableName);
        }

        String declaredTypeStr = ctx.typeIdentifier().getText();
        if (!TYPE_MAP.containsKey(declaredTypeStr)) {
            compiler_error(ctx, "Variable '%s' is being declared with an undefined type: %s", variableName, declaredTypeStr);
        }

        Type declaredType = TYPE_MAP.get(declaredTypeStr);
        Type expressionType = ctx.expression().accept(this);

        if (declaredType.isNotAssignableFrom(expressionType)) {
            compiler_error(ctx, "Cannot assign value of type %s to variable '%s' of type %s.", expressionType, variableName, declaredType);
        }

        setVariableInCurrentScope(variableName, declaredType);
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
                .append("Compilation error [")
                .append(filename)
                .append(":")
                .append(errorStartLine)
                .append("]: ")
                .append(COLOR_RESET)
                .append(String.format(format, args))
                .append("\n\n");



        for (int lineNumber = outputStartLine; lineNumber <= outputStopLine; lineNumber++) {
            int lineNumberWhitespacePadding = lineNumberDigits - lineNumber / 10;
            errorMessage.append(" ".repeat(Math.max(0, lineNumberWhitespacePadding)));

            errorMessage.append(lineNumber)
                .append(": ");

            String line = sourceCodeLines[lineNumber - 1];

            if (lineNumber >= errorStartLine && lineNumber <= errorStopLine) {
                if (lineNumber > errorStartLine) {
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
    }

    public Type visitPrintExpression(PrintExpressionContext ctx) {
        return ctx.expression().accept(this);
    }


    private List<Type> expressionListTypes(final TygerParser.ExpressionListContext ctx) {
        List<Type> expressionsTypes = new ArrayList<>();

        for (var expressionList = ctx; expressionList != null; expressionList = expressionList.expressionList()) {
            expressionsTypes.add(expressionList.expression().accept(this));
        }

        return expressionsTypes;
    }

    @Override
    public Type visitFunctionCallExpression(final TygerParser.FunctionCallExpressionContext ctx) {
        final String functionName = ctx.identifier().getText();
        final Optional<Function> optionalFunction = findFunction(functionName);

        if (optionalFunction.isEmpty()) {
            compiler_error(ctx, "Unknown function: %s", functionName);
        }

        Function function = optionalFunction.get();

        final Collection<Type> receivedTypes = expressionListTypes(ctx.expressionList());

        final int receivedArgumentsNumber = receivedTypes.size();
        final int expectedArgumentsNumber = function.arguments.size();
        if (receivedArgumentsNumber != expectedArgumentsNumber) {
            compiler_error(ctx, "Function %s expects %d arguments but got %d", functionName, expectedArgumentsNumber, receivedArgumentsNumber);
        }

        final var receivedTypesIterator = receivedTypes.iterator();
        final var expectedTypesIterator = function.arguments.entrySet().iterator();

        for (int i = 0; i < expectedArgumentsNumber; i++) {
            var receivedType = receivedTypesIterator.next();
            var expectedType = expectedTypesIterator.next();

            if (expectedType.getValue().isNotAssignableFrom(receivedType)) {
                compiler_error(
                        ctx,
                        "Expected argument %s of function %s (%s argument) to be of type %s but got %s",
                        expectedType.getKey(),
                        functionName,
                        ordinal(i + 1),
                        expectedType.getValue(),
                        receivedType
                );
            }
        }

        return function.type;
    }

    private static String ordinal(int i) {
        String[] suffixes = new String[] { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
        switch (i % 100) {
            case 11:
            case 12:
            case 13:
                return i + "th";
            default:
                return i + suffixes[i % 10];

        }
    }
}
