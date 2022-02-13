package tyger.binder;

import tyger.ast.FunctionDeclaration;
import tyger.ast.types.Type;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

public class Scope {

    record Bindings(Map<String, Type> variables, Map<String, FunctionDeclaration> functions) {
        Bindings() {
            this(new HashMap<>(), new HashMap<>());
        }
    }

    private LinkedList<Bindings> bindings = new LinkedList<>();

    public void reset() {
        bindings.clear();
    }

    public void push() {
        bindings.push(new Bindings());
    }

    public void pop() {
        bindings.pop();
    }

    public void declare_function(String name, FunctionDeclaration function) {
        assert bindings.peek() != null;
        assert bindings.peek().functions.get(name) == null;
        bindings.peek().functions.put(name, function);
    }

    public Optional<FunctionDeclaration> get_function(String name) {
        assert bindings.peek() != null;
        for (final Bindings binding : bindings) {
            if (binding.functions.containsKey(name)) {
                return Optional.of(binding.functions.get(name));
            }
        }
        return Optional.empty();
    }

    public void declare_variable(String name, Type type) {
        assert bindings.peek() != null;
        assert bindings.peek().variables.get(name) == null;
        bindings.peek().variables.put(name, type);
    }

    public Optional<Type> get_variable(String name) {
        assert bindings.peek() != null;
        for (final Bindings binding : bindings) {
            if (binding.variables.containsKey(name)) {
                return Optional.of(binding.variables.get(name));
            }
        }
        return Optional.empty();
    }

    public Optional<Type> get_variable_in_current_scope(String name) {
        assert bindings.peek() != null;
        return Optional.ofNullable(bindings.peek().variables.get(name));
    }

    public void set_variable(String name, Type type) {
        assert bindings.peek() != null;
        for (final Bindings binding : bindings) {
            if (binding.variables.containsKey(name)) {
                binding.variables.put(name, type);
                return;
            }
        }
        assert false; // variable must exist.
    }
}
