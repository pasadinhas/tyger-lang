package tyger.ast;

import tyger.ast.types.Type;
import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

import java.util.List;

// TODO: convert to expression once functions are first class citizens
public class FunctionDeclaration extends AstNode {

    final String name;
    final Type return_type;
    final List<Parameter> parameters;
    final Expression body;

    public record Parameter(String name, Type type) {}

    public FunctionDeclaration(
            final Loc location,
            final String name,
            final Type return_type,
            final List<Parameter> parameters,
            final Expression body
    ) {
        super(location, body);
        this.name = name;
        this.return_type = return_type;
        this.parameters = parameters;
        this.body = body;
    }

    public String name() {
        return name;
    }

    public Type return_type() {
        return return_type;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    public Expression body() {
        return body;
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
                        parameters.stream()
                                .map(parameter -> Json.field_str(parameter.name, parameter.type))
                                .toArray(String[]::new)
                )),
                Json.field("body", body)
        );
    }
}
