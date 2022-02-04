package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class BitAnd extends BinaryExpression {

    public BitAnd(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.BITAND, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_bit_and(this);
    }
}
