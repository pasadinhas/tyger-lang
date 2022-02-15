package tyger.ast.types;

public sealed abstract class Type permits Type.Integer, Type.Boolean, Type.Any, Type.Optional {

    public static final class Any extends Type {}
    public static final class Integer extends Type {}
    public static final class Boolean extends Type {}
    public static final class Optional extends Type {
        private final Type type;

        public Optional(final Type type) {
            this.type = type;
        }

        public Type type() {
            return type;
        }
    }

    public static final Type Any = new Any();
    public static final Type Integer = new Integer();
    public static final Type Boolean = new Boolean();
    public static final Type OptionalAny = Optional(Any);
    public static final Optional Optional(Type of) {
        return new Optional(of);
    }

    public static Type from_source_code(String type) {
        if (type.endsWith("?")) {
            return Optional(from_source_code(type.substring(0, type.length() - 1)));
        }

        return switch (type) {
            case "int" -> Integer;
            case "bool" -> Boolean;
            default -> throw new RuntimeException("Type conversion not implemented yet: " + type);
        };
    }

    public Type to_optional() {
        return this instanceof Optional ? this : Optional(this);
    }

    public Type to_non_optional() {
        Type type = this;
        while ((type instanceof Optional o)) {
            type = o.type();
        }
        return type;
    }

    public boolean is_optional() {
        return this instanceof Optional;
    }

    public boolean is_assignable_from(Type other) {
        return this.equals(other) || this.is_optional() && (other.equals(OptionalAny) || this.to_non_optional().equals(other));
    }

    /**
     * Returns the lowest common ancestor of both types, i.e. the most specific type that both types could be cast to, or null if there is
     * no common ancestor.
     */
    public static Type lca(final Type type_one, final Type type_two) {
        // if both types are the same, just return one of them.
        if (type_one.equals(type_two)) {
            return type_one;
        }

        // if one of them is optional of the other, return the optional.
        if (type_one.to_optional().equals(type_two) || type_two.to_optional().equals(type_one)) {
            return type_one.to_optional();
        }

        // if type one is any optional, return an optional of type two.
        if (OptionalAny.equals(type_one)) {
            return type_two.to_optional();
        }

        // if type two is any optional, return an optional of type one.
        if (OptionalAny.equals(type_two)) {
            return type_one.to_optional();
        }

        // no common ancestor found, return null.
        return null;
    }
}
