package tyger.ast.expressions.literals;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;

public class DecimalLiteral extends Expression {

    final long value;

    public DecimalLiteral(final Loc location, final long value) {
        super(location);
        this.value = value;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_decimal_literal(this);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
