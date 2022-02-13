package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;

import java.util.List;

public class FunctionCall extends Expression {

    final List<Expression> parameters;
    final String name;

    public FunctionCall(final Loc location, final String name, final List<Expression> parameters) {
        super(location, parameters);
        this.parameters = parameters;
        this.name = name;
    }

    public List<Expression> parameters() {
        return parameters;
    }

    public String name() {
        return name;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_function_call(this);
    }
}
