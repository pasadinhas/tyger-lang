package tyger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tyger.TygerParser.AssignmentExpressionContext;
import tyger.TygerParser.BinaryExpressionContext;
import tyger.TygerParser.BlockExpressionContext;
import tyger.TygerParser.GroupedExpressionContext;
import tyger.TygerParser.IdentifierExpressionContext;
import tyger.TygerParser.IfExpressionContext;
import tyger.TygerParser.LiteralExpressionContext;
import tyger.TygerParser.PrefixUnaryExpressionContext;
import tyger.TygerParser.ProgContext;

public class InterpreterVisitor extends TygerBaseVisitor<Object> {

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
        return switch (ctx.op.getText()) {
            case "+" -> (Long) ctx.left.accept(this) + (Long) ctx.right.accept(this);
            case "-" -> (Long) ctx.left.accept(this) - (Long) ctx.right.accept(this);
            case "/" -> (Long) ctx.left.accept(this) / (Long) ctx.right.accept(this);
            case "*" -> (Long) ctx.left.accept(this) * (Long) ctx.right.accept(this);
            case "%" -> (Long) ctx.left.accept(this) % (Long) ctx.right.accept(this);
            case "|" -> (Long) ctx.left.accept(this) | (Long) ctx.right.accept(this);
            case "&" -> (Long) ctx.left.accept(this) & (Long) ctx.right.accept(this);
            case ">>" -> (Long) ctx.left.accept(this) >> (Long) ctx.right.accept(this);
            case "<<" -> (Long) ctx.left.accept(this) << (Long) ctx.right.accept(this);
            case "and" -> (Boolean) ctx.left.accept(this) && (Boolean) ctx.right.accept(this);
            case "or" -> (Boolean) ctx.left.accept(this) || (Boolean) ctx.right.accept(this);
            case "==" -> ctx.left.accept(this).equals(ctx.right.accept(this));
            case "!=" -> !ctx.left.accept(this).equals(ctx.right.accept(this));
            case ">" -> (Long) ctx.left.accept(this) > (Long) ctx.right.accept(this);
            case ">=" -> (Long) ctx.left.accept(this) >= (Long) ctx.right.accept(this);
            case "<=" -> (Long) ctx.left.accept(this) <= (Long) ctx.right.accept(this);
            case "<" -> (Long) ctx.left.accept(this) < (Long) ctx.right.accept(this);
            default -> throw new RuntimeException("Binary operator " + ctx.op.getText() + " is not implemented.");
        };
    }

    @Override
    public Object visitLiteralExpression(LiteralExpressionContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return Long.valueOf(ctx.getText());
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return Boolean.valueOf(ctx.getText());
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
}