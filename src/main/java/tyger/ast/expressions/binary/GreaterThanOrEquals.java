package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class GreaterThanOrEquals extends BinaryExpression {

    public GreaterThanOrEquals(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.GE, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_greater_than_or_equals(this);
    }
}
