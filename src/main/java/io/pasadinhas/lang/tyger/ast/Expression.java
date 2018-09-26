/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.ast;

import io.pasadinhas.lang.tyger.ExecutionContext;
import io.pasadinhas.lang.tyger.TygerParser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

abstract public class Expression extends Typed {
    abstract public Object eval(final ExecutionContext executionContext);

    public static Expression from(final TygerParser.ExprContext ctx) {
        if (ctx.literal() != null) {
            return Literal.from(ctx.literal());
        } else if (ctx.Identifier() != null) {
            return new ValueIdentifier(ctx.Identifier().getText());
        } else if (ctx.block() != null) {
            return Expression.from(ctx.block());
        } else if (ctx.ifExpr() != null) {
            final List<TygerParser.ExprContext> exprs = ctx.ifExpr().expr();
            final Expression condition = Expression.from(exprs.get(0));
            final Expression thenExpr = Expression.from(exprs.get(1));
            final Expression elseExpr = exprs.size() > 2 ? Expression.from(exprs.get(2)) : null;
            return new IfExpression(condition, thenExpr, elseExpr);
        } else if (ctx.binaryOperator() != null) {
            final Expression left = Expression.from(ctx.expr(0));
            final Expression right = Expression.from(ctx.expr(1));
            final String operator = ctx.binaryOperator().getText();
            return new BinaryOperator(left, right, operator);
        } else if (ctx.funcCall() != null) {
            List<Expression> arguments = new ArrayList<>();
            if (ctx.funcCall().funcCallArgs() != null) {
                arguments = ctx.funcCall().funcCallArgs().expr().stream().map(Expression::from).collect(Collectors.toList());
            }
            return new FunctionCall(ctx.funcCall().Identifier().getText(), arguments);
        } else {
            System.out.println(String.format("Unsupported expression: %s", ctx.getText()));
            return null;
            //throw new RuntimeException(String.format("Unsupported expression: %s", ctx.getText()));
        }
    }

    public static Expression from(final TygerParser.BlockContext ctx) {
        return new Block(ctx);
    }
}
