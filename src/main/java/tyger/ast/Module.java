package tyger.ast;

import tyger.ast.visitor.AstVisitor;
import tyger.util.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class Module extends AstNode {

    final String name;
    final Collection<FunctionDeclaration> function_declarations;

    public Module(Loc location, String name, Collection<FunctionDeclaration> function_declarations) {
        super(location, function_declarations.toArray(FunctionDeclaration[]::new));
        this.name = name;
        this.function_declarations = Collections.unmodifiableCollection(new ArrayList<>(function_declarations));
    }

    @Override
    public <T> T accept(final AstVisitor<T> visitor) {
        return visitor.visit_module(this);
    }

    public Collection<FunctionDeclaration> function_declarations() {
        return function_declarations;
    }

    @Override
    public String toString() {
        return Json.object(
                Json.field_str("module", name),
                Json.field_array("functions", function_declarations)
        );
    }
}
