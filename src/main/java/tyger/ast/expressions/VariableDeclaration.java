package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.ast.types.Type;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

public class VariableDeclaration extends Expression {

    final Type declared_type;
    final String name;
    final Expression expression;

    public VariableDeclaration(final Loc location, final Type declared_type, final String name, final Expression expression) {
        super(location, expression);
        this.declared_type = declared_type;
        this.name = name;
        this.expression = expression;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_variable_declaration(this);
    }

    @Override
    public String toString() {
        return Json.object(
                Json.field_str("declare", name),
                Json.field_str("type", declared_type),
                Json.field("value", expression)
        );
    }
}
