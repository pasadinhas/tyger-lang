package tyger.ast;

import tyger.ast.types.Type;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AstNode {

    private boolean bound = false;
    private Type type = null;

    public record Loc(int start_line, int start_line_char, int stop_line, int stop_line_char) {}

    public final Loc loc;
    public AstNode parent;
    protected final List<AstNode> children = new ArrayList<>();

    AstNode() {
        throw new RuntimeException("AST Nodes should not use the default constructor.");
    }

    AstNode(Loc location, AstNode... children) {
        this.loc = location;
        for (final AstNode child : children) {
            child.setParent(this);
        }
        Collections.addAll(this.children, children);
    }

    AstNode(Loc location, Collection<? extends AstNode> children) {
        this(location, children.toArray(AstNode[]::new));
    }

    public List<AstNode> children() {
        return Collections.unmodifiableList(children);
    }

    public abstract <T> T accept(AstVisitor<T> visitor);

    private void setParent(final AstNode parent) {
        this.parent = parent;
    }

    public AstNode bind(Type type) {
        assert !bound;
        this.bound = true;
        this.type = type;
        return this;
    }

    public Type type() {
        assert bound;
        return type;
    }

    @Override
    public String toString() {
        return Json.str(super.toString());
    }
}
