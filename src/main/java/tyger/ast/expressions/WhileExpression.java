package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

public class WhileExpression extends Expression {

    final Expression condition;
    final Expression code;

    public WhileExpression(final Loc location, final Expression condition, final Expression code) {
        super(location, condition, code);
        this.condition = condition;
        this.code = code;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_while_expression(this);
    }

    @Override
    public String toString() {
        return Json.object(
                Json.field("while", condition),
                Json.field("do", code)
        );
    }
}
