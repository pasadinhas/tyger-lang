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
import io.pasadinhas.lang.tyger.typechecker.Scope;
import io.pasadinhas.lang.tyger.typechecker.TypeChecker;

import java.util.List;
import java.util.stream.Collectors;

public class Block extends Expression {
    public List<Expression> expressions;

    public Block(final TygerParser.BlockContext ctx) {
        this.expressions = ctx.expr().stream().map(exprCtx -> Expression.from(exprCtx)).collect(Collectors.toList());
    }

    @Override
    public Object eval(final ExecutionContext executionContext) {
        Object res = null;
        for (Expression expression : expressions) {
            res = expression.eval(executionContext);
        }
        return res;
    }


    @Override
    public boolean accept(final TypeChecker typeChecker, final Scope scope) {
        return typeChecker.visit(this, scope);
    }
}
