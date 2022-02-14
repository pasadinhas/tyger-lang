package tyger.codegen;

public final class Jvm {

    public static class Type {



        public static final Type I = new Type("I");

        private final String name;

        public static final Type from(final tyger.ast.types.Type type) {
            if (type == tyger.ast.types.Type.Integer) return I;
            throw new RuntimeException("TODO: type not yet supported during code generation: " + type);
        }

        public Type(final String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
