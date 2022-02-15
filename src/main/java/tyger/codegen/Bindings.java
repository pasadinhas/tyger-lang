package tyger.codegen;

import tyger.ast.types.Type;

import java.util.*;

public class Bindings {

    record Function(String name, String signature, Type type) {}
    record LocalVariable(String name, Integer stack_position, Type type) {}

    private record Scope(Map<String, Function> functions, Map<String, LocalVariable> variables) {
        Scope() {
            this(new HashMap<>(), new HashMap<>());
        }
    }

    private final LinkedList<Scope> scopes = new LinkedList<>();

    private int local_variables_size = 0;

    public void push_scope() {
        scopes.push(new Scope());
    }

    public void pop_scope() {
        assert scopes.peek() != null;

        local_variables_size -= scopes.peek().variables.size();
        assert local_variables_size >= 0;

        scopes.pop();
    }

    public void add_function(String name, String signature, Type type) {
        assert !scopes.isEmpty();
        scopes.peek().functions.put(name, new Function(name, signature, type));
    }

    public Function find_function(String name) {
        assert scopes.peek() != null;
        for (final Scope scope : scopes) {
            if (scope.functions.containsKey(name)) {
                return scope.functions.get(name);
            }
        }
        throw new RuntimeException("Function not found: " + name);
    }

    public void add_local_variable(String name, Type type) {
        assert !scopes.isEmpty();
        scopes.peek().variables.put(name, new LocalVariable(name, local_variables_size++, type));
    }

    public LocalVariable find_local_variable(String name) {
        assert scopes.peek() != null;
        for (final Scope scope : scopes) {
            if (scope.variables.containsKey(name)) {
                return scope.variables.get(name);
            }
        }
        throw new RuntimeException("Local variable not found: " + name);
    }
}
