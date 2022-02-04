package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class BitOr extends BinaryExpression {

    public BitOr(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.BITOR, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_bit_or(this);
    }
}
