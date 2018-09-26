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

import java.util.List;
import java.util.function.Function;

public class FunctionCall extends Expression {
    public String name;
    public List<Expression> arguments;

    public FunctionCall(final String name, final List<Expression> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public Object eval(final ExecutionContext executionContext) {
        final Object value = executionContext.get(name);

        if (! (value instanceof FunctionDeclaration)) {
            throw new RuntimeException(String.format("Expected '%s' to be a functionDeclaration but got: %s", name, value));
        }

        executionContext.pushStackFrame();

        final FunctionDeclaration functionDeclaration = (FunctionDeclaration) value;

        if (functionDeclaration.params.size() != arguments.size()) {
            throw new RuntimeException(
                    String.format("Expect %s parameters calling functionDeclaration '%s' but got %d.", functionDeclaration.params.size(), name, arguments.size()));
        }

        for (int i = 0; i < arguments.size(); i++) {
            executionContext.addBinding(functionDeclaration.params.get(i).name, arguments.get(i).eval(executionContext));
        }

        final Object result = functionDeclaration.body.eval(executionContext);

        executionContext.popStackFrame();

        return result;
    }

    @Override
    public boolean accept(final TypeChecker typeChecker, final Scope scope) {
        return typeChecker.visit(this, scope);
    }
}
