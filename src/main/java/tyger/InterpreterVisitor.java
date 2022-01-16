package tyger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.GroupedExpressionContext;
import tyger.TygerParser.IdentifierExpressionContext;
import tyger.TygerParser.IfExpressionContext;
import tyger.TygerParser.LiteralExpressionContext;
import tyger.TygerParser.PrefixUnaryExpressionContext;
import tyger.TygerParser.ProgContext;
import tyger.TygerParser.VariableDeclarationExpressionContext;
import tyger.TygerParser.WhileExpressionContext;

public class InterpreterVisitor extends TygerBaseVisitor<Object> {

    private static class None {
        @Override
        public String toString() {
            return "None";
        }
    }

    private static final None NoneLiteral = new None();
    private final Map<String, Object> variables = new HashMap<>();

    @Override
    public Object visitProg(final ProgContext ctx) {
        return ctx.blockExpression().accept(this);
    }

    @Override
    public Object visitBlockExpression(BlockExpressionContext ctx) {
        Object last = null;
        for (var expression : ctx.expression()) {
            last = expression.accept(this);
        }
        return last;
    }

    @Override
    public Object visitPrefixUnaryExpression(PrefixUnaryExpressionContext ctx) {
        return switch (ctx.op.getText()) {
            case "-" -> -(Long) ctx.expression().accept(this);
            case "not" -> !(Boolean) ctx.expression().accept(this);
            default -> throw new RuntimeException("Prefix unary operator " + ctx.op.getText() + " is not implemented.");
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

        variables.put(variableName, value);
        
        return value;
    }

    @Override
    public Object visitIdentifierExpression(IdentifierExpressionContext ctx) {
        return variables.get(ctx.identifier().getText());
    }

    @Override
    public Object visitIfExpression(IfExpressionContext ctx) {
        Boolean test = (Boolean) ctx.condition.accept(this);
        if (test) {
            return ctx.block.accept(this);
        } else {
            return ctx.elseif != null ? ctx.elseif.accept(this) : ctx.elseBlock.accept(this);
        }
    }

    @Override
    public Object visitVariableDeclarationExpression(VariableDeclarationExpressionContext ctx) {
        String variableName = ctx.identifier().getText();
        Object value = ctx.expression().accept(this);

        variables.put(variableName, value);
        
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
}