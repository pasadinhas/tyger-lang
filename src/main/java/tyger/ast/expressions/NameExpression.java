package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

public class NameExpression extends Expression {

    final String name;

    public NameExpression(final Loc location, final String name) {
        super(location);
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_identifier_access_expression(this);
    }

    @Override
    public String toString() {
        return Json.object(Json.field_str("name", name));
    }
}
