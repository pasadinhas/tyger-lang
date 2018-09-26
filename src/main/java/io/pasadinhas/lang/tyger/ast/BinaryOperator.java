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

import java.util.Arrays;

public class BinaryOperator extends Expression {
    public Expression left;
    public Expression right;
    public String operator;

    public BinaryOperator(final Expression left, final Expression right, final String operator) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public Object eval(final ExecutionContext executionContext) {
        final Object left = this.left.eval(executionContext);
        final Object right = this.right.eval(executionContext);

        if (operator.equals("==")) {
            return left.equals(right);
        }

        if (!(left instanceof Long || left instanceof Double) || !(right instanceof Long || right instanceof Double)) {
            throw new RuntimeException(String.format("Binary expressions only support Numeric values but got: %s %s %s", left, operator, right));
        }

        if (left instanceof Long && right instanceof Long) {
            return evalLong((Long) left, (Long) right);
        } else {
            final Double dLeft = (left instanceof Double) ? (Double) left : ((Long) left).doubleValue();
            final Double dRight = (right instanceof Double) ? (Double) right : ((Long) right).doubleValue();
            return evalDouble(dLeft, dRight);
        }
    }

    public Object evalDouble(final Double left, final Double right) {
        switch (operator) {
            case "*": return left * right;
            case "-": return left - right;
            case "/": return left / right;
            case "+": return left + right;
            case "%": return left % right;
            case "<": return left < right;
            case "<=": return left <= right;
            case ">": return left > right;
            case ">=": return left >= right;
            default: throw new RuntimeException(String.format("Operator '%s' not recognized.", operator));
        }
    }

    public Object evalLong(final Long left, final Long right) {
        switch (operator) {
            case "*": return left * right;
            case "-": return left - right;
            case "/": return left / right;
            case "+": return left + right;
            case "%": return left % right;
            case "<": return left < right;
            case "<=": return left <= right;
            case ">": return left > right;
            case ">=": return left >= right;
            default: throw new RuntimeException(String.format("Operator '%s' not recognized.", operator));
        }
    }

    @Override
    public int typeCheck(final TypeChecker typeChecker, final Scope scope) {
        if (this.type != null) {
            return 0;
        }
        int total = 0;
        total += left.typeCheck(typeChecker, scope);
        total += right.typeCheck(typeChecker, scope);

        if (left.type.equals("infer") && right.type.equals("infer")) {
            throw typeChecker.error("Both expressions of binary expression have infer type. Cannot proceed.");
        }

        if (!left.type.equals(right.type) && left.type.equals("infer")) {
            this.left.inferType(typeChecker, scope, right.type);
        } else if (!left.type.equals(right.type) && right.type.equals("infer")) {
            this.right.inferType(typeChecker, scope, left.type);
        }

        if (left.type.equals(right.type)) {
            if (Arrays.asList("==", ">=", ">", "<", "<=").contains(operator)) {
                this.type = "Boolean";
            } else {
                this.type = left.type;
            }
            return total + 1;
        }

        throw typeChecker.error("Binary expression '%s %s %s' failed to type check. " +
                                "Expected '%s' to have the same type as '%s' but '%s' is not '%s'.",
                                left, operator, right, left, right, left.type, right.type);
    }

    @Override
    public int inferType(final TypeChecker typeChecker, final Scope scope, final String type) {
        if (this.type != null && !this.type.equals(type) && !this.type.equals("infer")) {
            throw typeChecker.error("Forcing type '%s' to binary operator with type '%s'.", type, this.type);
        }

        int total = 0;
        total += left.inferType(typeChecker, scope, type);
        total += right.inferType(typeChecker, scope, type);

        if (this.type.equals(type)) {
            return total;
        }

        this.type = type;
        return total + 1;
    }
}
