package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class LessThanOrEquals extends BinaryExpression {

    public LessThanOrEquals(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.LE, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_less_than_or_equals(this);
    }
}
