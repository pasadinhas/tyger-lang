/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.typechecker;

import io.pasadinhas.lang.tyger.ast.Block;
import io.pasadinhas.lang.tyger.ast.Expression;
import io.pasadinhas.lang.tyger.ast.FunctionCall;
import io.pasadinhas.lang.tyger.ast.FunctionDeclaration;
import io.pasadinhas.lang.tyger.ast.FunctionFormalParameter;
import io.pasadinhas.lang.tyger.ast.Program;
import io.pasadinhas.lang.tyger.ast.Typed;
import io.pasadinhas.lang.tyger.ast.literal.BooleanLiteral;
import io.pasadinhas.lang.tyger.ast.literal.CharacterLiteral;
import io.pasadinhas.lang.tyger.ast.literal.FloatingPointLiteral;
import io.pasadinhas.lang.tyger.ast.literal.IntegerLiteral;
import io.pasadinhas.lang.tyger.ast.literal.StringLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeChecker {
    private static final Logger logger = LoggerFactory.getLogger(TypeChecker.class);

    public boolean visit(final Program program, final Scope scope) {
        scope.push();

        program.functions.forEach(scope::add);

        for (final FunctionDeclaration functionDeclaration : program.functions.values()) {
            functionDeclaration.accept(this, scope);
        }

        scope.pop();
        return true;
    }

    public boolean visit(final FunctionDeclaration functionDeclaration, final Scope scope) {
        for (final FunctionFormalParameter param : functionDeclaration.params) {
            param.accept(this, scope);
        }

        scope.push();

        functionDeclaration.params.forEach(param -> scope.add(param.name, param));

        functionDeclaration.body.accept(this, scope);

        if (functionDeclaration.returnType != null && !functionDeclaration.returnType.equals(functionDeclaration.body.type)) {
            throw error("Function '%s' return type '%s' does not match body's return type: '%s'.", name, returnType, body.type);
        }

        if (returnType == null) {
            returnType = body.type;
            total += 1;
        }

        scope.pop();
        return true;
    }

    public boolean visit(final BooleanLiteral booleanLiteral, final Scope scope) {
        if (!booleanLiteral.hasType(Type.BOOLEAN)) {
            logger.error("Boolean literal with type {}", booleanLiteral.getType());
            return false;
        }
        return true;
    }

    public boolean visit(final CharacterLiteral characterLiteral, final Scope scope) {
        if (!characterLiteral.hasType(Type.CHARACTER)) {
            logger.error("Character literal with type {}", characterLiteral.getType());
            return false;
        }
        return true;
    }

    public boolean visit(final FloatingPointLiteral floatingPointLiteral, final Scope scope) {
        if (!floatingPointLiteral.hasType(Type.FLOAT)) {
            logger.error("Float literal with type {}", floatingPointLiteral.getType());
            return false;
        }
        return true;
    }

    public boolean visit(final IntegerLiteral integerLiteral, final Scope scope) {
        if (!integerLiteral.hasType(Type.INTEGER)) {
            logger.error("Integer literal with type {}", integerLiteral.getType());
            return false;
        }
        return true;
    }

    public boolean visit(final StringLiteral stringLiteral, final Scope scope) {
        if (!stringLiteral.hasType(Type.STRING)) {
            logger.error("String literal with type {}", stringLiteral.getType());
            return false;
        }
        return true;
    }

    public boolean visit(final Block block, final Scope scope) {
        scope.push();

        if (block.expressions.isEmpty()) {
            logger.error("Block cannot be empty.");
            return false;
        }

        for (final Expression expression : block.expressions) {
            expression.accept(this, scope);
        }

        final Expression lastExpression = block.expressions.get(block.expressions.size() - 1);

        block.setType(lastExpression.getType());

        scope.pop();
        return true;
    }

    public boolean visit(final FunctionCall functionCall, final Scope scope) {
        final Typed value = scope.lookup(functionCall.name);

        if (!value.hasType(Type.FUNCTION)) {
            logger.error("Expected '{}' to be a Function but got '{}'.", functionCall.name, value.getType());
            return false;
        }

        final FunctionDeclaration function = (FunctionDeclaration) value;
        functionCall.setType(function.returnType);
        return true;
    }

    public boolean visit(final FunctionFormalParameter parameter, final Scope scope) {
        if (type != null) return 0;
        type = "infer";
        return 1;
    }

    public static TypeCheckerException error(final String message, Object... args) {
        return new TypeCheckerException("TypeChecker Error: " + String.format(message, args));
    }
}
