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

import java.util.ArrayList;
import java.util.HashMap;

public class Program extends Expression {
    public final HashMap<String, FunctionDeclaration> functions = new HashMap<>();

    public FunctionDeclaration registerFunction(final FunctionDeclaration f) {
        if (functions.containsKey(f.name)) {
            throw new RuntimeException(String.format("FunctionDeclaration %s is already defined.", f.name));
        }

        return functions.put(f.name, f);
    }

    public Object eval(final ExecutionContext executionContext) {
        executionContext.pushStackFrame();

        functions.forEach((name, f) -> executionContext.addBinding(name, f));

        return new FunctionCall("main", new ArrayList<>()).eval(executionContext);
    }

    @Override
    public boolean accept(final TypeChecker typeChecker, final Scope scope) {
        return typeChecker.visit(this, scope);
    }
}
