package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.util.Json;

public abstract class BinaryExpression extends Expression {

    public enum Operator {
        ASSIGN("="),
        MUL("*"),
        DIV("/"),
        MOD("%"),
        ADD("+"),
        SUB("-"),
        LSHIFT("<<"),
        RSHIFT(">>"),
        BITAND("&"),
        CARET("^"),
        BITOR("|"),
        EQ("=="),
        NE("!="),
        LT("<"),
        LE("<="),
        GT(">"),
        GE(">="),
        AND("and"),
        OR("or"),
        ;

        Operator(final String op) {
            this.op = op;
        }

        final String op;
    }

    final Expression left;
    final Expression right;
    final Operator operator;

    public BinaryExpression(final Loc location, final Operator operator, final Expression left, final Expression right) {
        super(location, left, right);
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public String toString() {
        return Json.object(
                Json.field_str("operator", operator.op),
                Json.field("left", left),
                Json.field("right", right)
        );
    }
}
