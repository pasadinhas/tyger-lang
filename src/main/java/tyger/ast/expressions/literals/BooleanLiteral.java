package tyger.ast.expressions.literals;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;

public class BooleanLiteral extends Expression {

    final boolean value;

    public BooleanLiteral(final Loc location, final boolean value) {
        super(location);
        this.value = value;
    }

    public boolean value() {
        return value;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_boolean_literal(this);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
