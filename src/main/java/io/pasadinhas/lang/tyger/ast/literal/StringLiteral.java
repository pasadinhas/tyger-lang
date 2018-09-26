/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.ast.literal;

import io.pasadinhas.lang.tyger.ExecutionContext;
import io.pasadinhas.lang.tyger.ast.Literal;
import io.pasadinhas.lang.tyger.typechecker.Scope;
import io.pasadinhas.lang.tyger.typechecker.Type;
import io.pasadinhas.lang.tyger.typechecker.TypeChecker;

public class StringLiteral extends Literal {
    private String value;

    public StringLiteral(final String value) {
        this.value = value;
        this.setType(Type.STRING);
    }

    @Override
    public Object eval(final ExecutionContext executionContext) {
        return value;
    }

    @Override
    public boolean accept(final TypeChecker typeChecker, final Scope scope) {
        return typeChecker.visit(this, scope);
    }
}
