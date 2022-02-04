package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class Equals extends BinaryExpression {

    public Equals(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.EQ, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_equals(this);
    }
}
