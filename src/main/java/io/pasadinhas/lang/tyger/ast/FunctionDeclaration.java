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
import io.pasadinhas.lang.tyger.typechecker.Type;
import io.pasadinhas.lang.tyger.typechecker.TypeChecker;

import java.util.ArrayList;
import java.util.List;

public class FunctionDeclaration extends Expression {
    public final String name;
    public Expression body;
    public List<FunctionFormalParameter> params;
    public Type returnType;

    public FunctionDeclaration(final TygerParser.FunctionDeclarationContext ctx) {
        // func.name.getText(), func.functionBody(), func.functionFormalParameters()))
        this.name = ctx.name.getText();

        final TygerParser.FunctionBodyContext bodyCtx = ctx.functionBody();
        this.body = bodyCtx.block() != null ? Expression.from(bodyCtx.block()) : Expression.from(bodyCtx.expr());

        this.params = new ArrayList<>();

        if (ctx.functionFormalParameters() != null) {
            ctx.functionFormalParameters().functionFormalParameter().forEach(paramCtx -> this.params.add(new FunctionFormalParameter(paramCtx)));
        }

        if (ctx.expressionTypeHint() != null) {
            this.returnType = new Type(ctx.expressionTypeHint().primitiveType().getText());
        }

        type = Type.FUNCTION;
    }

    public Object eval(final ExecutionContext executionContext) {
        return this;
    }

    @Override
    public boolean accept(final TypeChecker typeChecker, final Scope scope) {
        return typeChecker.visit(this, scope);
    }
}
