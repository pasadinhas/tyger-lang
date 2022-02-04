package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

public class IfExpression extends Expression {

    final Expression condition;
    final Expression then;
    final Expression _else;

    public IfExpression(final Loc location, final Expression condition, final Expression then, final Expression _else) {
        super(location, condition, then, _else);
        this.condition = condition;
        this.then = then;
        this._else = _else;
    }

    public Expression condition() {
        return condition;
    }

    public Expression then() {
        return then;
    }

    public Expression _else() {
        return _else;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_if_expression(this);
    }

    @Override
    public String toString() {
        return Json.object(
                Json.field("if", condition),
                Json.field("then", then),
                Json.field("else", _else)
        );
    }
}
