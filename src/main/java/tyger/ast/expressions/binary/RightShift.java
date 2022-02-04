package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class RightShift extends BinaryExpression {

    public RightShift(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.RSHIFT, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_right_shift(this);
    }
}
