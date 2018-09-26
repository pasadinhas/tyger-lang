/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.ast;

import io.pasadinhas.lang.tyger.TygerParser;
import io.pasadinhas.lang.tyger.ast.literal.BooleanLiteral;
import io.pasadinhas.lang.tyger.ast.literal.CharacterLiteral;
import io.pasadinhas.lang.tyger.ast.literal.FloatingPointLiteral;
import io.pasadinhas.lang.tyger.ast.literal.IntegerLiteral;
import io.pasadinhas.lang.tyger.ast.literal.StringLiteral;

public abstract class Literal extends Expression {
    public static Literal from(final TygerParser.LiteralContext ctx) {
        if (ctx.BooleanLiteral() != null) {
            return new BooleanLiteral(Boolean.valueOf(ctx.BooleanLiteral().getText()));
        } else if (ctx.StringLiteral() != null) {
            return new StringLiteral(ctx.StringLiteral().getText());
        } else if (ctx.IntegerLiteral() != null) {
            return new IntegerLiteral(Long.valueOf(ctx.IntegerLiteral().getText()));
        } else if (ctx.FloatingPointLiteral() != null) {
            return new FloatingPointLiteral(Double.valueOf(ctx.FloatingPointLiteral().getText()));
        } else if (ctx.CharacterLiteral() != null) {
            return new CharacterLiteral(ctx.CharacterLiteral().getText().charAt(0));
        } else {
            throw new RuntimeException(String.format("Error creating Literal from: %s", ctx.getText()));
        }
    }
}
