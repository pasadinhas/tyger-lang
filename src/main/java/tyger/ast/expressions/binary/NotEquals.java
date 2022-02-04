package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class NotEquals extends BinaryExpression {

    public NotEquals(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.NE, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_not_equals(this);
    }
}
