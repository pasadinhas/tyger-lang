package tyger.ast.types;

public class Optional extends Type {

    private final Type type;

    public Optional(final Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }
}
