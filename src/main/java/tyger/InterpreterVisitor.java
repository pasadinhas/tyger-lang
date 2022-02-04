package tyger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tyger.TygerParser.*;

import java.util.*;

public class InterpreterVisitor extends TygerBaseVisitor<Object> {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    private record Function(String name, List<String> arguments, FunctionDeclarationExpressionContext declaration) {}
    private record Scope(Map<String, Object> variables, Map<String, Function> functions) {
        Scope() {
            this(new HashMap<>(), new HashMap<>());
        }
    }
    private final LinkedList<Scope> scopes = new LinkedList<>();

    private Object getVariable(String name) {
        var iterator = scopes.iterator();
        while (iterator.hasNext()) {
            var variables = iterator.next().variables;
            if (variables.containsKey(name)) {
                return variables.get(name);
            }
        }
        throw new RuntimeException("Variable not found: " + name);
    }

    private void setVariable(String name, Object value) {
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

    private void setNewVariable(String name, Object value) {
        assert scopes.peek() != null;
        scopes.peek().variables.put(name, value);
    }

    private Function getFunction(String name) {
        var iterator = scopes.iterator();
        while (iterator.hasNext()) {
            var functions = iterator.next().functions;
            if (functions.containsKey(name)) {
                return functions.get(name);
            }
        }
        throw new RuntimeException("Function not found: " + name);
    }

    private void setFunction(String name, List<String> arguments, FunctionDeclarationExpressionContext declaration) {
        assert scopes.peek() != null;
        scopes.peek().functions.put(name, new Function(name, arguments, declaration));
    }

    private void scope_push() {
        this.scopes.push(new Scope());
    }
    private void scope_pop() {
        this.scopes.pop();
    }

    private static class None {
        @Override
        public String toString() {
            return "None";
        }
    }

    private static final None NoneLiteral = new None();

    private String pad(String value, int targetSize) {
        return value + " ".repeat(Math.max(0, targetSize - value.length()));
    }
    private void debug_printVariables() {
        final String HEADER_SCOPE_NUMBER = "Scope";
        final String HEADER_VARIABLE = "Variable";
        final String HEADER_VALUE = "Value";

        var variableNameSize = Math.max(HEADER_VARIABLE.length(), scopes.stream()
                .flatMap(scope -> scope.variables().keySet().stream())
                .map(String::length)
                .max(Integer::compareTo)
                .orElse(0));

        var variableValueSize= Math.max(HEADER_VALUE.length(), scopes.stream()
                .flatMap(scope -> scope.variables().values().stream())
                .map(Object::toString)
                .map(String::length)
                .max(Integer::compareTo)
                .orElse(0));

        var numberOfScopesSize = Math.max(HEADER_SCOPE_NUMBER.length(), (int) Math.floor(Math.log10(scopes.size())));

        StringBuilder table = new StringBuilder();

        // Header
        table.append("┌─");
        table.append("─".repeat(numberOfScopesSize));
        table.append("─┬─");
        table.append("─".repeat(variableNameSize));
        table.append("─┬─");
        table.append("─".repeat(variableValueSize));
        table.append("─┐");
        table.append('\n');

        table.append("│ ");
        table.append(pad(HEADER_SCOPE_NUMBER, numberOfScopesSize));
        table.append(" │ ");
        table.append(pad(HEADER_VARIABLE, variableNameSize));
        table.append(" │ ");
        table.append(pad(HEADER_VALUE, variableValueSize));
        table.append(" │");
        table.append('\n');

        int i = scopes.size() + 1;
        for (final Scope scope : scopes) {
            i--;
            if (scope.variables.isEmpty()) {
                continue;
            }

            table.append("├─");
            table.append("─".repeat(numberOfScopesSize));
            table.append("─┼─");
            table.append("─".repeat(variableNameSize));
            table.append("─┼─");
            table.append("─".repeat(variableValueSize));
            table.append("─┤");
            table.append('\n');

            final int currentScopeNumber = i;
            scope.variables.forEach((name, value) -> {
                table.append("│ ");
                table.append(pad(String.valueOf(currentScopeNumber), numberOfScopesSize));
                table.append(" │ ");
                table.append(pad(name, variableNameSize));
                table.append(" │ ");
                table.append(pad(value.toString(), variableValueSize));
                table.append(" │");
                table.append('\n');
            });
        }

        table.append("└─");
        table.append("─".repeat(numberOfScopesSize));
        table.append("─┴─");
        table.append("─".repeat(variableNameSize));
        table.append("─┴─");
        table.append("─".repeat(variableValueSize));
        table.append("─┘");
        table.append('\n');

        logger.debug("Scopes:\n{}", table);
    }

    @Override
    public Object visitModule(final ModuleContext ctx) {
        scope_push();

        ctx.functionDeclarationExpression().forEach(function -> {
            String functionName = function.identifier().getText();

            ArgsListContext argsListContext = function.argsList();
            final List<String> argumentNames = new ArrayList<>();
            while (argsListContext != null) {
                argumentNames.add(argsListContext.identifier().getText());
                argsListContext = argsListContext.argsList();
            }

            setFunction(functionName, argumentNames, function);
        });

        var mainFunction = getFunction("main");
        var result = mainFunction.declaration.accept(this);

        scope_pop();

        return result;
    }

    @Override
    public Object visitBlockExpression(BlockExpressionContext ctx) {
        scope_push();
        Object last = null;
        for (var expression : ctx.expression()) {
            last = expression.accept(this);
        }
        scope_pop();
        return last;
    }

    @Override
    public Object visitPrefixUnaryExpression(PrefixUnaryExpressionContext ctx) {
        Object expressionValue = ctx.expression().accept(this);
        if (expressionValue.equals(NoneLiteral)) {
            return expressionValue;
        } 

        return switch (ctx.op.getText()) {
            case "-" -> - (Long) expressionValue;
            case "not" -> !(Boolean) expressionValue;
            case "++", "--" -> {
                Long value = (Long) expressionValue;
                Long result = ctx.op.getText().equals("++") ? value + 1 : value - 1;

                if (ctx.expression() instanceof IdentifierExpressionContext identifierCtx) {
                    String variableName = identifierCtx.identifier().getText();
                    setVariable(variableName, result);
                }

                yield result;
            }
            default -> throw new RuntimeException("Prefix unary operator " + ctx.op.getText() + " is not implemented.");
        };
    }

    @Override
    public Object visitPostfixUnaryExpression(PostfixUnaryExpressionContext ctx) {
        Object expressionValue = ctx.expression().accept(this);
        if (expressionValue.equals(NoneLiteral)) {
            return expressionValue;
        } 

        return switch (ctx.op.getText()) {
            case "++", "--" -> {
                Long value = (Long) expressionValue;
                Long result = ctx.op.getText().equals("++") ? value + 1 : value - 1;

                if (ctx.expression() instanceof IdentifierExpressionContext identifierCtx) {
                    String variableName = identifierCtx.identifier().getText();
                    setVariable(variableName, result);
                }

                yield value;
            }
            default -> throw new RuntimeException("Postfix unary operator " + ctx.op.getText() + " is not implemented.");
        };
    }

    @Override
    public Object visitBinaryExpression(BinaryExpressionContext ctx) {
        Object left = ctx.left.accept(this);
        Object right = ctx.right.accept(this);

        if (left.equals(NoneLiteral) || right.equals(NoneLiteral)) {
            return NoneLiteral;
        }

        return switch (ctx.op.getText()) {
            case "+" -> (Long) left + (Long) right;
            case "-" -> (Long) left - (Long) right;
            case "/" -> (Long) left / (Long) right;
            case "*" -> (Long) left * (Long) right;
            case "%" -> (Long) left % (Long) right;
            case "|" -> (Long) left | (Long) right;
            case "&" -> (Long) left & (Long) right;
            case ">>" -> (Long) left >> (Long) right;
            case "<<" -> (Long) left << (Long) right;
            case "and" -> (Boolean) left && (Boolean) right;
            case "or" -> (Boolean) left || (Boolean) right;
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case ">" -> (Long) left > (Long) right;
            case ">=" -> (Long) left >= (Long) right;
            case "<=" -> (Long) left <= (Long) right;
            case "<" -> (Long) left < (Long) right;
            default -> throw new RuntimeException("Binary operator " + ctx.op.getText() + " is not implemented.");
        };
    }

    @Override
    public Object visitLiteralExpression(LiteralExpressionContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return Long.valueOf(ctx.getText());
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return Boolean.valueOf(ctx.getText());
        } else if (ctx.NONE_LITERAL() != null) {
            return NoneLiteral;
        }

        throw new IllegalStateException("Cannot get value for Literal: " + ctx.getText());
    }

    @Override
    public Object visitGroupedExpression(GroupedExpressionContext ctx) {
        return ctx.expression().accept(this);
    }

    @Override
    public Object visitAssignmentExpression(AssignmentExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        Object value = ctx.expression().accept(this);

        setVariable(variableName, value);

        return value;
    }

    @Override
    public Object visitIdentifierExpression(IdentifierExpressionContext ctx) {
        debug_printVariables();
        return getVariable(ctx.identifier().getText());
    }

    @Override
    public Object visitIfExpression(IfExpressionContext ctx) {
        Boolean test = (Boolean) ctx.condition.accept(this);
        if (test) {
            return ctx.block.accept(this);
        } else if (ctx.elseBlock != null) {
            return ctx.elseBlock.accept(this);
        } else {
            return NoneLiteral;
        }
    }

    @Override
    public Object visitVariableDeclarationExpression(VariableDeclarationExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        Object value = ctx.expression().accept(this);

        setNewVariable(variableName, value);
        
        return value;
    }

    @Override
    public Object visitWhileExpression(WhileExpressionContext ctx) {
        Object result = NoneLiteral;
        while ((Boolean) ctx.condition.accept(this)) {
            result = ctx.blockExpression().accept(this);
        }
        return result;
    }


    @Override
    public Object visitPrintExpression(PrintExpressionContext ctx) {
        Object result = ctx.expression().accept(this);
        logger.info("{}", result);
        return result;
    }

    @Override
    public Object visitFunctionCallExpression(final FunctionCallExpressionContext ctx) {
        final Function function = getFunction(ctx.identifier().getText());

        final StringBuilder debug_functionCall = new StringBuilder();
        debug_functionCall.append(function.name);
        debug_functionCall.append('(');


        scope_push();
        ExpressionListContext argumentsList = ctx.expressionList();
        function.arguments().forEach(argumentName -> {
            final Object argumentValue = argumentsList.expression().accept(this);
            setNewVariable(argumentName, argumentValue);

            debug_functionCall.append(argumentName);
            debug_functionCall.append(" = ");
            debug_functionCall.append(argumentValue);
            debug_functionCall.append(", ");
        });

        debug_functionCall.append(')');

        logger.debug("Function call: {}", debug_functionCall);
        debug_printVariables();
        var result = function.declaration.accept(this);
        scope_pop();
        return result;
    }
}
