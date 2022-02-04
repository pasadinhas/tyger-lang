package tyger.ast.expressions.binary;

import tyger.ast.Expression;
import tyger.ast.expressions.BinaryExpression;
import tyger.ast.visitor.AstVisitor;

public class Division extends BinaryExpression {

    public Division(final Loc location, final Expression left, final Expression right) {
        super(location, Operator.DIV, left, right);
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_division(this);
    }
}
