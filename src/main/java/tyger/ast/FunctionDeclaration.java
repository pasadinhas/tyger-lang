package tyger.ast;

import tyger.ast.types.Type;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

import java.util.List;
import java.util.stream.Collectors;

// TODO: convert to expression once functions are first class citizens
public class FunctionDeclaration extends AstNode {

    final String name;
    final Type return_type;
    final List<Argument> arguments;
    final Expression body;

    public record Argument(String name, Type type) {}

    public FunctionDeclaration(
            final Loc location,
            final String name,
            final Type return_type,
            final List<Argument> arguments,
            final Expression body
    ) {
        super(location, body);
        this.name = name;
        this.return_type = return_type;
        this.arguments = arguments;
        this.body = body;
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_function_declaration(this);
    }

    @Override
    public String toString() {
        return Json.object(
                Json.field_str("name", name),
                Json.field_str("type", return_type),
                Json.field("arguments", Json.object(
                        arguments.stream()
                                .map(argument -> Json.field_str(argument.name, argument.type))
                                .toArray(String[]::new)
                )),
                Json.field("body", body)
        );
    }
}
