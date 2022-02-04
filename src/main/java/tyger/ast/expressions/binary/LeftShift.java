package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class LeftShift extends BinaryExpression {

    public LeftShift(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.LSHIFT, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_left_shift(this);
    }
}
