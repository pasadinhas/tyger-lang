package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class LessThan extends BinaryExpression {

    public LessThan(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.LT, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_less_than(this);
    }
}
