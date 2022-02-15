package tyger.codegen;

import tyger.TygerParser;
import tyger.ast.Expression;
import tyger.ast.FunctionDeclaration;
import tyger.ast.Module;
import tyger.ast.expressions.*;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.BooleanLiteral;
import tyger.ast.expressions.literals.DecimalLiteral;
import tyger.ast.types.Type;
import tyger.ast.visitor.AstVisitor;

import java.util.*;

public class BytecodeGenerator implements AstVisitor<ByteBuffer> {

    private final ConstantPool constant_pool = new ConstantPool();
    private final Map<FunctionDeclaration, CodeGen> functions = new HashMap<>();
    private final Bindings bindings = new Bindings();
    private final ClassFileByteBuffer class_file = new ClassFileByteBuffer(1024 * 1024); // 1 MB initial capacity.
    private String module_name = "";
    private String generated_class_name;
    private CodeGen codegen;

    @Override
    public ByteBuffer visit_module(final Module module) {
        // set the module name and generated class name in the visitor instance
        module_name = module.name();
        generated_class_name = module_name;

        // global scope
        bindings.push_scope();

        // register all function declarations so they are available everywhere
        for (final FunctionDeclaration function_declaration : module.function_declarations()) {
            bindings.add_function(
                    function_declaration.name(),
                    function_signature(function_declaration),
                    function_declaration.return_type()
            );
        }

        // visit functions
        module.function_declarations().forEach(function_declaration -> function_declaration.accept(this));

        // constant pool object representing the generated class
        final int generated_class_reference = constant_pool.add_class(generated_class_name);
        final int object_class_reference = constant_pool.add_class("java/lang/Object");

        // write the class file bytes
        class_file.write_file_header();
        class_file.write_constant_pool(constant_pool);

        class_file.write_2(0x1011);                    // Modifiers: SYNTHETIC, FINAL, PUBLIC
        class_file.write_2(generated_class_reference); // This class, in the Constants Pool (module_name)
        class_file.write_2(object_class_reference);    // Super class, in the Constants Pool (java.lang.Object)
        class_file.write_2(0x0000);                    // Number of interfaces
        class_file.write_2(0x0000);                    // Number of fields

        class_file.write_2(functions.size());          // Number of methods
        for (final var entry : functions.entrySet()) {
            final FunctionDeclaration function_declaration = entry.getKey();
            final CodeGen function_code_gen = entry.getValue();
            final byte[] function_bytecode = function_code_gen.to_byte_array();

            System.out.println(function_declaration.name());

            final int function_name_reference = constant_pool.index_of_utf8(function_declaration.name());
            final int function_signature_reference = constant_pool.index_of_utf8(function_signature(function_declaration));
            final int code_reference = constant_pool.index_of_utf8("Code");

            class_file.write_2(0x0009); // access modifiers: public static
            class_file.write_2(function_name_reference); // method name
            class_file.write_2(function_signature_reference); // method signature
            class_file.write_2(0x0001); // Number of method attributes: 1 (Code attribute)
            class_file.write_2(code_reference); // "Code"
            class_file.write_2(                 // Size of "Code" attribute:
                    2 +                         // - 2 bytes for max stack size
                    2 +                         // - 2 bytes for max local variables
                    4 +                         // - 4 bytes for size of code
                    function_bytecode.length +  // - n bytes for the actual instructions
                    2 +                         // - 2 bytes for the exception table size (0)
                    2                           // - 2 bytes for the number of attributes (0)
            );
            class_file.write_2(function_code_gen.operand_stack.max);        // max stack size
            class_file.write_2(function_declaration.parameters().size());   // max local variables TODO: this is not correct, just temp!
            class_file.write_4(function_bytecode.length);                   // method code length
            class_file.write_all(function_bytecode);                        // method code
            class_file.write_2(0x0000);                                     // exception table size
            class_file.write_2(0x0000);                                     // number of attributes
        }

        class_file.write_2(0x0000); // number of attributes

        return class_file;
    }

    private String function_signature(final FunctionDeclaration function_declaration) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        function_declaration.parameters().stream()
                .map(FunctionDeclaration.Parameter::type)
                .map(Jvm.Type::from)
                .map(Jvm.Type::name)
                .forEach(builder::append);

        builder.append(")");
        builder.append(Jvm.Type.from(function_declaration.type()).name());
        return builder.toString();
    }

    private ByteBuffer scoped(Runnable runnable) {
        bindings.push_scope();
        runnable.run();
        bindings.pop_scope();
        return class_file;
    }

    @Override
    public ByteBuffer visit_function_declaration(final FunctionDeclaration function_declaration) {
        return scoped(() -> {
            // Register a new active code gen
            this.codegen = new CodeGen();
            functions.put(function_declaration, codegen);

            // Add references to the constant pool
            constant_pool.add_method_ref(generated_class_name, function_declaration.name(), function_signature(function_declaration));
            constant_pool.add_utf_8("Code");

            // Bind parameters to local variables
            function_declaration.parameters().forEach(parameter -> bindings.add_local_variable(parameter.name(), parameter.type()));

            function_declaration.body().accept(this);

            switch (function_declaration.type()) {
                case Type.Integer i -> codegen.ireturn();
                case Type.Boolean b -> codegen.ireturn();
                default -> throw new RuntimeException("Return of " + function_declaration.type() + " is not implemented yet.");
            }
        });
    }

    @Override
    public ByteBuffer visit_block(final Block block) {
        final Iterator<Expression> iterator = block.expressions().iterator();
        while (iterator.hasNext()) {
            final Expression expression = iterator.next();

            expression.accept(this);

            // drop any unused values. only the last expression can leave a value for a return.
            while (iterator.hasNext() && codegen.operand_stack.size > 0) {
                codegen.pop();
            }
        }
        return class_file;
    }

    @Override
    public ByteBuffer visit_if_expression(final IfExpression if_expression) {
        not_implemented_yet();
        return class_file;
    }

    private void not_implemented_yet() {
        throw new RuntimeException("ERROR: " + get_caller() + " is not implemented yet.");
    }

    private String get_caller() {
        StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        StackWalker.StackFrame frame = stackWalker.walk(stream1 -> stream1.skip(2)
                .findFirst()
                .orElse(null));

        if (frame == null) {
            return "???";
        }

        return String.format("%s#%s", frame.getDeclaringClass().getSimpleName(), frame.getMethodName());
    }

    @Override
    public ByteBuffer visit_decimal_literal(final DecimalLiteral decimal_literal) {
        final long value = decimal_literal.value();
        if (value == 0) {
            codegen.iconst_0();
        } else if (value == 1) {
            codegen.iconst_1();
        } else if (value == 2) {
            codegen.iconst_2();
        } else if (value == 3) {
            codegen.iconst_3();
        } else if (value == 4) {
            codegen.iconst_4();
        } else if (value == 5) {
            codegen.iconst_5();
        } else if (value <= 0xFF) {
            codegen.bipush((int) value);
        } else if (value <= 0xFFFF) {
            codegen.sipush((int) value);
        } else {
            throw new RuntimeException("TODO: generation of integers larger than two bytes is not implemented yet.");
        }
        return class_file;
    }

    @Override
    public ByteBuffer visit_boolean_literal(final BooleanLiteral boolean_literal) {
        if (boolean_literal.value()) {
            codegen.iconst_1();
        } else {
            codegen.iconst_0();
        }
        return class_file;
    }

    @Override
    public ByteBuffer visit_function_call(final FunctionCall function_call) {
        final Bindings.Function function = bindings.find_function(function_call.name());
        final int function_reference = constant_pool.add_method_ref(generated_class_name, function.name(), function.signature());

        function_call.parameters().forEach(expression -> expression.accept(this));
        codegen.invokestatic(function_reference, function_call.parameters().size());

        return class_file;
    }

    @Override
    public ByteBuffer visit_while_expression(final WhileExpression while_expression) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_assignment(final Assignment assignment) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_identifier_access_expression(final NameExpression identifier_access_expression) {
        final Bindings.LocalVariable local_variable = bindings.find_local_variable(identifier_access_expression.name());

        final Type type = identifier_access_expression.type();

        var unused = switch (type) {
            case Type.Integer i -> switch (local_variable.stack_position()) {
                case 0 -> codegen.iload_0();
                case 1 -> codegen.iload_1();
                case 2 -> codegen.iload_2();
                case 3 -> codegen.iload_3();
                default -> codegen.iload(local_variable.stack_position());
            };
            default -> throw new RuntimeException("visit_identifier_access_expression with type " + type + " is not implemented yet");
        };

        return class_file;
    }

    @Override
    public ByteBuffer visit_multiplication(final Multiplication multiplication) {
        multiplication.left().accept(this);
        multiplication.right().accept(this);

        switch (multiplication.type()) {
            case Type.Integer i -> codegen.imul();
            default -> throw new RuntimeException("ERROR: visit_multiplication with type " + multiplication.type() + " is not implemented yet.");
        }

        return class_file;
    }

    @Override
    public ByteBuffer visit_division(final Division division) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_modulo(final Modulo modulo) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_addition(final Addition addition) {
        addition.left().accept(this);
        addition.right().accept(this);

        switch (addition.type()) {
            case Type.Integer i -> codegen.iadd();
            default -> throw new RuntimeException("ERROR: visit_addition with type " + addition.type() + " is not implemented yet.");
        }

        return class_file;
    }

    @Override
    public ByteBuffer visit_subtraction(final Subtraction subtraction) {
        subtraction.left().accept(this);
        subtraction.right().accept(this);

        switch (subtraction.type()) {
            case Type.Integer i -> codegen.isub();
            default -> throw new RuntimeException("ERROR: visit_subtraction with type " + subtraction.type() + " is not implemented yet.");
        }

        return class_file;
    }

    @Override
    public ByteBuffer visit_left_shift(final LeftShift left_shift) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_right_shift(final RightShift right_shift) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_bit_and(final BitAnd bit_and) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_bit_or(final BitOr bit_or) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_bit_xor(final BitXor bit_xor) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_equals(final Equals equals) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_not_equals(final NotEquals not_equals) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_less_than(final LessThan less_than) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_less_than_or_equals(final LessThanOrEquals less_than_or_equals) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_greater_than(final GreaterThan greater_than) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_greater_than_or_equals(final GreaterThanOrEquals greater_than_or_equals) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_and(final And and) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_or(final Or or) {
        not_implemented_yet();
        return class_file;
    }

    @Override
    public ByteBuffer visit_variable_declaration(final VariableDeclaration variable_declaration) {
        not_implemented_yet();
        return class_file;
    }
}
