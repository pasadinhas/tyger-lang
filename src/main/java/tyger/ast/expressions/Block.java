package tyger.ast.expressions;

import tyger.ast.Expression;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

import java.util.Collection;
import java.util.Collections;

public class Block extends Expression {

    final Collection<Expression> expressions;

    public Block(Loc location, final Collection<Expression> expressions) {
        super(location, expressions);
        this.expressions = Collections.unmodifiableCollection(expressions);
    }

    public Collection<Expression> expressions() {
        return expressions;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_block(this);
    }

    @Override
    public String toString() {
        return Json.array(expressions);
    }
}
