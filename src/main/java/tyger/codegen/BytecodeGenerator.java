package tyger.codegen;

import tyger.ast.FunctionDeclaration;
import tyger.ast.Module;
import tyger.ast.expressions.*;
import tyger.ast.expressions.binary.*;
import tyger.ast.expressions.literals.BooleanLiteral;
import tyger.ast.expressions.literals.DecimalLiteral;
import tyger.ast.visitor.AstVisitor;

import java.util.HashMap;
import java.util.Map;

public class BytecodeGenerator implements AstVisitor<ByteBuffer> {

    private final ConstantPool constant_pool = new ConstantPool();
    private final Map<FunctionDeclaration, CodeGen> functions = new HashMap<>();
    private final Bindings bindings = new Bindings();
    private final ClassFileByteBuffer class_file = new ClassFileByteBuffer(1024 * 1024); // 1 MB initial capacity.
    private String module_name = "";

    @Override
    public ByteBuffer visit_module(final Module module) {
        // set the module name in the visitor instance
        module_name = module.name();

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

        // constant pool object representing the generated class
        final int generated_class_reference = constant_pool.add_class(module_name);
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

    @Override
    public ByteBuffer visit_function_declaration(final FunctionDeclaration function_declaration) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_block(final Block block) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_if_expression(final IfExpression if_expression) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_decimal_literal(final DecimalLiteral decimal_literal) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_boolean_literal(final BooleanLiteral boolean_literal) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_function_call(final FunctionCall function_call) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_while_expression(final WhileExpression while_expression) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_assignment(final Assignment assignment) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_multiplication_expression(final Multiplication multiplication) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_identifier_access_expression(final NameExpression identifier_access_expression) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_division(final Division division) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_modulo(final Modulo modulo) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_addition(final Addition addition) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_subtraction(final Subtraction subtraction) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_left_shift(final LeftShift left_shift) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_right_shift(final RightShift right_shift) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_bit_and(final BitAnd bit_and) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_bit_or(final BitOr bit_or) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_bit_xor(final BitXor bit_xor) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_equals(final Equals equals) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_not_equals(final NotEquals not_equals) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_less_than(final LessThan less_than) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_less_than_or_equals(final LessThanOrEquals less_than_or_equals) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_greater_than(final GreaterThan greater_than) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_greater_than_or_equals(final GreaterThanOrEquals greater_than_or_equals) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_and(final And and) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_or(final Or or) {
        return class_file;
    }

    @Override
    public ByteBuffer visit_variable_declaration(final VariableDeclaration variable_declaration) {
        return class_file;
    }
}
