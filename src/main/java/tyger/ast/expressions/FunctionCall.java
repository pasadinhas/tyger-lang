package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;

public class FunctionCall extends Expression {

    // List<Expression> : arguments
    // String function_name

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_function_call(this);
    }
}
