package tyger.ast;

import java.util.Collection;

public abstract class Expression extends AstNode {

    public Expression() {
    }

    public Expression(final Loc location, final AstNode... children) {
        super(location, children);
    }

    public Expression(final Loc location, final Collection<? extends AstNode> children) {
        super(location, children);
    }
}
