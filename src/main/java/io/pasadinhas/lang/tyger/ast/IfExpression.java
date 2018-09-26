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
import io.pasadinhas.lang.tyger.typechecker.Scope;
import io.pasadinhas.lang.tyger.typechecker.TypeChecker;

public class IfExpression extends Expression {
    public Expression condition;
    public Expression thenExpr;
    public Expression elseExpr;

    public IfExpression(final Expression condition, final Expression thenExpr, final Expression elseExpr) {
        this.condition = condition;
        this.thenExpr = thenExpr;
        this.elseExpr = elseExpr;
    }

    @Override
    public Object eval(final ExecutionContext executionContext) {
        if (condition.eval(executionContext).equals(Boolean.TRUE)) {
            return thenExpr.eval(executionContext);
        } else if (elseExpr != null) {
            return elseExpr.eval(executionContext);
        }
        return null;
    }

    @Override
    public int typeCheck(final TypeChecker typeChecker, final Scope scope) {
        if (this.type != null) return 0;
        if (elseExpr == null) {
            throw typeChecker.error("If expression must contain an else expression (for now..).");
        }

        int total = 0;
        total += condition.typeCheck(typeChecker, scope);
        if (condition.type != "Boolean") {
            throw typeChecker.error("If condition '%s' has type '%s' instead of Boolean", condition, condition.type);
        }

        total += thenExpr.typeCheck(typeChecker, scope);
        total += elseExpr.typeCheck(typeChecker, scope);

        if (thenExpr.type.equals(elseExpr.type)) {
            this.type = thenExpr.type;
            return total + 1;
        }

        throw typeChecker.error("If then expression type does not match else type: '%s' and '%s', respectively.", thenExpr.type, elseExpr.type);
    }

    @Override
    public int inferType(final TypeChecker typeChecker, final Scope scope, final String type) {
        return 0;
    }
}
