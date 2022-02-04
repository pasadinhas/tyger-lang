package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class BitXor extends BinaryExpression {

    public BitXor(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.CARET, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_bit_xor(this);
    }
}
