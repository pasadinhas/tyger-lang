package tyger.ast.types;

public abstract class Type {

    public static final Type Integer = new Integer();
    public static final Type Boolean = new Boolean();
    public static final Type OptionalAny = Optional(new Type() {});
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
}
