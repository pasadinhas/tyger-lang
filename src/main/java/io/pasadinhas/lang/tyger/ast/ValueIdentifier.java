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

public class ValueIdentifier extends Expression {
    final public String name;

    public ValueIdentifier(final String name) {
        this.name = name;
    }

    @Override
    public Object eval(final ExecutionContext executionContext) {
        return executionContext.get(name);
    }

    @Override
    public int typeCheck(final TypeChecker typeChecker, final Scope scope) {
        if (type != null) return 0;

        final Typed value = scope.lookup(name);

        this.type = value.type;
        return 1;
    }

    @Override
    public int inferType(final TypeChecker typeChecker, final Scope scope, final String type) {
        if (this.type == null || this.type.equals("infer")) {
            this.type = type;
            return 1;
        }

        if (this.type.equals(type)) {
            return 0;
        }

        throw typeChecker.error("Cannot infer identifier type to '%s' because it already has type '%s'", type, this.type);
    }
}
