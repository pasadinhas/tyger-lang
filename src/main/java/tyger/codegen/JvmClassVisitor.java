package tyger.codegen;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tyger.TygerBaseVisitor;
import tyger.TygerParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;

public class JvmClassVisitor extends TygerBaseVisitor<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(JvmClassVisitor.class);
    private final String generatedClassName = "tyger";
    private final File file;
    private final ConstantPool constant_pool = new ConstantPool();
    private final CodeGen code_gen = new CodeGen();
    private final Map<ParserRuleContext, CodeGen.Type> types = new HashMap<>();

    record Method(String name, String signature, CodeGen.Type type) {}
    private final Map<String, Method> methods = new HashMap<>();

    public JvmClassVisitor(final File file) {
        this.file = file;
    }

    static class CodeGen {
        private static final Logger logger = LoggerFactory.getLogger(CodeGen.class);

        final OperandStack operand_stack = new OperandStack();

        static class OperandStack {
            int size = 0;
            int max = 0;

            void reset() {
                size = 0;
                max = 0;
            }

            int grow() {
                max = Math.max(++size, max);
                return size;
            }

            int shrink() {
                assert size > 0;
                return --size;
            }

            int shrink(int n) {
                assert size > n - 1;
                size -= n;
                return size;
            }
        }

        // jvm instructions
        private static final byte aconst_null = 0x01;
        private static final byte iconst_0 = 0x03;
        private static final byte iconst_1 = 0x04;
        private static final byte iconst_2 = 0x05;
        private static final byte iconst_3 = 0x06;
        private static final byte iconst_4 = 0x07;
        private static final byte iconst_5 = 0x08;
        private static final byte bipush = 0x10;
        private static final byte sipush = 0x11;
        private static final byte iload = 0x15;
        private static final byte iload_0 = 0x1a;
        private static final byte iload_1 = 0x1b;
        private static final byte iload_2 = 0x1c;
        private static final byte iload_3 = 0x1d;
        private static final byte pop = 0x57;
        private static final byte iadd = 0x60;
        private static final byte isub = 0x64;
        private static final byte idiv = 0x6c;
        private static final byte imul = 0x68;
        private static final byte irem = 0x70;
        private static final byte if_icmpne = (byte) 0xa0;
        private static final byte _goto = (byte) 0xa7;
        private static final byte invokestatic = (byte) 0xb8;

        record R0(int position) {}
        record R1(int position, int op_1) {}
        record R2(int position, int op_1, int op_2) {}

        private enum Type { I, Z }
        private final ArrayList<Byte> function_code = new ArrayList<>(1024 * 1024); // 1MB initial capacity.

        private void log(String format, Object... args) {
            log(position(), format, args);
        }

        private void log(int position, String format, Object... args) {
            logger.debug(String.format("%3d: ", position) + format, args);
        }

        R0 aconst_null() {
            log("aconst_null");
            operand_stack.grow();
            return instruction_0(aconst_null);
        }

        R0 iconst_0() {
            log("iconst_0");
            operand_stack.grow();
            return instruction_0(iconst_0);
        }

        R0 iconst_1() {
            log("iconst_1");
            operand_stack.grow();
            return instruction_0(iconst_1);
        }

        R0 iconst_2() {
            log("iconst_2");
            operand_stack.grow();
            return instruction_0(iconst_2);
        }

        R0 iconst_3() {
            log("iconst_3");
            operand_stack.grow();
            return instruction_0(iconst_3);
        }

        R0 iconst_4() {
            log("iconst_4");
            operand_stack.grow();
            return instruction_0(iconst_4);
        }

        R0 iconst_5() {
            log("iconst_5");
            operand_stack.grow();
            return instruction_0(iconst_5);
        }

        R1 bipush(int value) {
            log("bipush {}", value);
            operand_stack.grow();
            return instruction_1(bipush, value);
        }

        R1 sipush(int value) {
            log("sipush {}", value);
            operand_stack.grow();
            return instruction_2(sipush, value);
        }

        R1 iload(int index) {
            assert index > 3;
            log("iload {}", index);
            operand_stack.grow();
            return instruction_1(iload, index);
        }

        R0 iload_0() {
            log("iload_0");
            operand_stack.grow();
            return instruction_0(iload_0);
        }

        R0 iload_1() {
            log("iload_1");
            operand_stack.grow();
            return instruction_0(iload_1);
        }

        R0 iload_2() {
            log("iload_2");
            operand_stack.grow();
            return instruction_0(iload_2);
        }

        R0 iload_3() {
            log("iload_3");
            operand_stack.grow();
            return instruction_0(iload_3);
        }

        R1 if_icmpne() {
            return if_icmpne(0x0000);
        }

        R1 if_icmpne(int jumpTo) {
            log("if_icmpne {}", jumpTo);
            operand_stack.shrink();
            operand_stack.shrink();
            return instruction_2(if_icmpne, jumpTo);
        }

        R1 _goto() {
            return _goto(0x0000);
        }

        R1 _goto(int jumpTo) {
            log("goto {}", jumpTo);
            return instruction_2(_goto, jumpTo);
        }

        R0 pop() {
            log("pop");
            operand_stack.shrink();
            return instruction_0(pop);
        }

        R0 iadd() {
            log("iadd");
            operand_stack.shrink(); // 2 pops and 1 push
            return instruction_0(iadd);
        }

        R0 isub() {
            log("isub");
            operand_stack.shrink(); // 2 pops and 1 push
            return instruction_0(isub);
        }

        R0 idiv() {
            log("idiv");
            operand_stack.shrink(); // 2 pops and 1 push
            return instruction_0(idiv);
        }

        R0 imul() {
            log("imul");
            operand_stack.shrink(); // 2 pops and 1 push
            return instruction_0(imul);
        }

        R0 irem() {
            log("irem");
            operand_stack.shrink(); // 2 pops and 1 push
            return instruction_0(irem);
        }

        R1 invokestatic(int index, int number_of_arguments) {
            log("invokestatic #{}", index);
            operand_stack.shrink(number_of_arguments - 1); // pops number_of_arguments and pushes one value
            return instruction_2(invokestatic, index);
        }

        int position() {
            return function_code.size();
        }

        void set_to_current_position(int location) {
            update_2(location, position());
        }

        void update_2(int location, int value) {
            assert value <= 0xFFFF; // 2 bytes

            log(location, "UPDATE: {}", value);

            function_code.set(location,     (byte) (value >>> 8));
            function_code.set(location + 1, (byte) (value >>> 0));
        }

        R0 instruction_0(byte instruction) {
            var position = function_code.size();
            function_code.add(instruction);
            return new R0(position);
        }

        R1 instruction_1(byte instruction, int operand) {
            assert operand <= 0xFF; // 1 byte
            var position = function_code.size();
            function_code.add(instruction);
            function_code.add((byte) operand);
            return new R1(position, position + 1);
        }

        R1 instruction_2(byte instruction, int operands) {
            assert operands <= 0xFFFF; // 2 bytes
            var position = function_code.size();
            function_code.add(instruction);
            function_code.add((byte) (operands >>> 8));
            function_code.add((byte) (operands >>> 0));
            return new R1(position, position + 1);
        }

        void reset() {
            function_code.clear();
            operand_stack.reset();
        }

        static Type jvm_type(final String type) {
            return switch (type) {
                case "int" -> Type.I;
                case "bool" -> Type.Z;
                default -> throw new RuntimeException("Conversion of type " + type + " is not implemented yet.");
            };
        }
    }

    static class LocalVariables {
        record LocalVariable(Integer stack_position, CodeGen.Type type, String name) {}
        private final LinkedList<Map<String, LocalVariable>> scopes = new LinkedList<>();
        private int stack_position = 0;

        void scope_push() {
            scopes.push(new HashMap<>());
        }

        void scope_pop() {
            assert scopes.peek() != null;
            stack_position -= scopes.pop().size();
        }

        void add(final String name, final CodeGen.Type type) {
            assert scopes.peek() != null;
            scopes.peek().put(name, new LocalVariable(stack_position++, type, name));
        }

        Map<String, LocalVariable> scope_peek() {
            return scopes.peek();
        }

        LocalVariable get(final String name) {
            assert scopes.peek() != null;
            for (final Map<String, LocalVariable> scope : scopes) {
                if (scope.containsKey(name)) {
                    return scope.get(name);
                }
            }
            throw new RuntimeException("Local variable not found: " + name);
        }

        void reset() {
            scopes.clear();
            stack_position = 0;
        }
    }
    private final LocalVariables local_variables = new LocalVariables();

    static class ConstantPool {

        final static Logger logger = LoggerFactory.getLogger(ConstantPool.class);

        enum ConstantPoolObjectKind {
            CLASS(0x07),
            UTF8(0x01),
            METHOD_REF(0x0A),
            NAME_AND_TYPE(0x0C)
            ;

            public final byte code;

            ConstantPoolObjectKind(final int code) {
                this.code = (byte) code;
            }
        }

        record Class (String name) {}
        record Utf8 (String value) {}
        record MethodRef(String className, String methodName, String type) {}
        record NameAndType(String methodName, String type) {}

        private ArrayList<Byte> constant_pool = new ArrayList<>(1024 * 1024); // 1MB initial capacity.
        private int constant_pool_size = 0; // Number of elements in the constant pool.
        private Map<Object, Integer> constant_pool_lookup_table = new HashMap<>();

        int add_class(String name) {
            final Class constant_pool_object = new Class(name);

            if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
                final Integer index = constant_pool_lookup_table.get(constant_pool_object);
                logger.debug("Class {} was already registered in #{}", name, index);
                return index;
            }

            constant_pool_size += 1;
            constant_pool_lookup_table.put(constant_pool_object, constant_pool_size);

            // start writing a class object in the constant pool
            constant_pool.add(ConstantPoolObjectKind.CLASS.code);

            // placeholder for: utf8 class name
            final int refs_index = constant_pool.size();
            constant_pool.add((byte) 0); // Utf8
            constant_pool.add((byte) 0); // Utf8

            // add utf-8 class name to the constant pool
            final int class_name_utf8_index = add_utf_8(name);

            // update the bytes that reference the utf-8 class name location
            constant_pool.set(refs_index, (byte) (class_name_utf8_index >>> 8));
            constant_pool.set(refs_index + 1, (byte) (class_name_utf8_index >>> 0));

            return constant_pool_lookup_table.get(constant_pool_object);
        }

        int add_utf_8(String value) {
            final Utf8 constant_pool_object = new Utf8(value);

            if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
                final Integer index = constant_pool_lookup_table.get(constant_pool_object);
                logger.debug("UTF-8 String \"{}\" was already registered in #{}", value, index);
                return index;
            }

            constant_pool_size += 1;
            constant_pool_lookup_table.put(constant_pool_object, constant_pool_size);

            // start writing an utf-8 value in the constant pool
            constant_pool.add(ConstantPoolObjectKind.UTF8.code);

            // add the utf-8 value length in bytes
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            constant_pool.add((byte) (bytes.length >>> 8));
            constant_pool.add((byte) (bytes.length >>> 0));

            // add the utf-8 value contents
            for (int i = 0; i < bytes.length; i++) {
                constant_pool.add(bytes[i]);
            }

            return constant_pool_size;
        }

        int add_method_ref(String className, String methodName, String type) {
            final MethodRef constant_pool_object = new MethodRef(className, methodName, type);

            if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
                final Integer index = constant_pool_lookup_table.get(constant_pool_object);
                logger.debug("MethodRef {}.{}{} was already registered in #{}", className, methodName, type, index);
                return index;
            }

            constant_pool_size += 1;
            constant_pool_lookup_table.put(constant_pool_object, constant_pool_size);

            // start writing a method ref object in the constant pool
            constant_pool.add(ConstantPoolObjectKind.METHOD_REF.code);

            // placeholder for: Class & NameAndType
            final int refs_index = constant_pool.size();
            constant_pool.add((byte) 0); // Class
            constant_pool.add((byte) 0); // Class
            constant_pool.add((byte) 0); // NameAndType
            constant_pool.add((byte) 0); // NameAndType

            // add Class and NameAndType
            final int class_index = add_class(className);
            final int name_and_type_index = add_name_and_type(methodName, type);

            // update the placeholders
            constant_pool.set(refs_index, (byte) (class_index >>> 8));
            constant_pool.set(refs_index + 1, (byte) (class_index >>> 0));
            constant_pool.set(refs_index + 2, (byte) (name_and_type_index >>> 8));
            constant_pool.set(refs_index + 3, (byte) (name_and_type_index >>> 0));

            return constant_pool_lookup_table.get(constant_pool_object);
        }

        private int add_name_and_type(final String methodName, final String type) {
            final NameAndType constant_pool_object = new NameAndType(methodName, type);

            if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
                final Integer index = constant_pool_lookup_table.get(constant_pool_object);
                logger.debug("NameAndType {}{} was already registered in #{}", methodName, type, index);
                return index;
            }

            constant_pool_size += 1;
            constant_pool_lookup_table.put(constant_pool_object, constant_pool_size);

            // start writing a name and type object in the constant pool
            constant_pool.add(ConstantPoolObjectKind.NAME_AND_TYPE.code);

            // placeholder for: Utf8 (Name) & Utf8 (Type)
            final int refs_index = constant_pool.size();
            constant_pool.add((byte) 0); // Utf8 (Name)
            constant_pool.add((byte) 0); // Utf8 (Name)
            constant_pool.add((byte) 0); // Utf8 (Type)
            constant_pool.add((byte) 0); // Utf8 (Type)

            // add Utf8 (Name) and Utf8 (Type)
            final int utf8_name_index = add_utf_8(methodName);
            final int utf8_type_index = add_utf_8(type);

            // update the placeholders
            constant_pool.set(refs_index, (byte) (utf8_name_index >>> 8));
            constant_pool.set(refs_index + 1, (byte) (utf8_name_index >>> 0));
            constant_pool.set(refs_index + 2, (byte) (utf8_type_index >>> 8));
            constant_pool.set(refs_index + 3, (byte) (utf8_type_index >>> 0));

            return constant_pool_lookup_table.get(constant_pool_object);
        }

        int index_of(Object key) {
            return Optional.ofNullable(constant_pool_lookup_table.get(key)).orElseThrow();
        }

        int size() {
            return constant_pool_size;
        }

        byte[] to_byte_array() {
            final byte[] bytes = new byte[constant_pool.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = constant_pool.get(i);
            }
            return bytes;
        }
    }

    private void write_4(final OutputStream outputStream, final int bytes) throws IOException {
        outputStream.write(new byte[] {
                (byte) (bytes >>> 24),
                (byte) (bytes >>> 16),
                (byte) (bytes >>> 8),
                (byte) (bytes >>> 0),
        });
    }


    private void write_2(final OutputStream outputStream, final int bytes) throws IOException {
        assert bytes <= 0xFF_FF;
        outputStream.write(new byte[] {
                (byte) (bytes >>> 8),
                (byte) (bytes >>> 0),
        });
    }


    private void write_1(final OutputStream outputStream, final int bytes) throws IOException {
        assert bytes <= 0xFF;
        outputStream.write(bytes);
    }

    @Override
    public Integer visitProg(final TygerParser.ProgContext ctx) {
        register_all_methods(ctx.functionDeclarationExpression());

        // Visit all function declarations and register the max stack size.
        var max_stack_size = ctx.functionDeclarationExpression().stream()
                .map(f -> f.accept(this))
                .max(Integer::compareTo)
                .orElseThrow();

        constant_pool.add_class(generatedClassName);

        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream os = new BufferedOutputStream(fos)
        ) {

            write_4(os, 0xCA_FE_BA_BE);                 // Magic Number, the famous CAFE BABE
            write_4(os, 0x00_00_00_3D);                 // Version: Java 17

            write_2(os, constant_pool.size() + 1);      // Constants Pool size
            os.write(constant_pool.to_byte_array());    // Write the Constant Pool

            write_2(os, 0x10_11);                       // Modifiers: SYNTHETIC, FINAL, PUBLIC
            write_2(os, constant_pool.index_of(         // This class, in the Constants Pool
                    new ConstantPool.Class(generatedClassName)
            ));
            write_2(os, 0x00_00);                       // Super class, in the Constants Pool

            write_2(os, 0x00_00);                       // Number of interfaces
            write_2(os, 0x00_00);                       // Number of fields

            write_2(os, methods.size());                // Number of methods
            for (final Method method : methods.values()) {
                write_2(os, 0x0009);                    // access modifiers: public static
                write_2(os, constant_pool.index_of(     // method name
                        new ConstantPool.Utf8(method.name)
                ));
                write_2(os, constant_pool.index_of(     // type descriptor
                        new ConstantPool.Utf8(method.signature)
                ));
                write_2(os, 0x0000);                    // Number of method attributes
            }

            write_2(os, 0x00_00);                       // Number of attributes

        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return max_stack_size;
    }

    private void register_all_methods(final List<TygerParser.FunctionDeclarationExpressionContext> methodsCtx) {
        methodsCtx.forEach(ctx -> {
            final String signature = create_type_signature_string(ctx);
            final String method_name = ctx.identifier().getText();
            final CodeGen.Type return_type = code_gen.jvm_type(ctx.typeIdentifier().getText());

            methods.put(method_name, new Method(method_name, signature, return_type));
        });
    }

    @Override
    public Integer visitFunctionDeclarationExpression(final TygerParser.FunctionDeclarationExpressionContext ctx) {
        // reset internal structures
        code_gen.reset();
        local_variables.reset();

        local_variables.scope_push();

        final Method method = methods.get(ctx.identifier().getText());

        constant_pool.add_method_ref(generatedClassName, method.name, method.signature);
        constant_pool.add_utf_8("Code");

        var argsList = ctx.argsList();
        while (argsList != null) {
            final CodeGen.Type type = code_gen.jvm_type(argsList.typeIdentifier().getText());
            final String name = argsList.identifier().getText();
            local_variables.add(name, type);
            argsList = argsList.argsList();
        }

        logger.debug("=".repeat(80));
        logger.debug("=== {}.{}", method.name, method.signature);
        local_variables.scope_peek().values().forEach(local ->
                logger.debug("=== {}: {} ({})", local.stack_position, local.name, local.type));
        logger.debug("=".repeat(80));
        var max_stack_size = ctx.blockExpression().accept(this);

        return max_stack_size;
    }

    private String create_type_signature_string(final TygerParser.FunctionDeclarationExpressionContext ctx) {
        //TODO: call the overloaded version instead.
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        var argsList = ctx.argsList();
        while (argsList != null) {
            builder.append(code_gen.jvm_type(argsList.typeIdentifier().getText()));
            argsList = argsList.argsList();
        }
        builder.append(")");
        builder.append(code_gen.jvm_type(ctx.typeIdentifier().getText()));
        return builder.toString();
    }

    private String create_type_signature_string(final CodeGen.Type returnType, final CodeGen.Type... argumentTypes) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (int i = 0; i < argumentTypes.length; i++) {
            builder.append(argumentTypes[i].name());
        }
        builder.append(")");
        builder.append(returnType.name());
        return builder.toString();
    }

    @Override
    public Integer visitIfExpression(final TygerParser.IfExpressionContext ctx) {
        // stack: Ø

        int max_stack_size_condition = ctx.condition.accept(this); // stack: condition_result

        code_gen.iconst_1(); // stack: condition_result | 1
        CodeGen.R1 if_icmpne = code_gen.if_icmpne(); // stack: Ø

        int max_stack_size_then_block = ctx.block.accept(this);

        final CodeGen.R1 _goto = code_gen._goto(); // goto to skip else block.
        code_gen.set_to_current_position(if_icmpne.op_1); // set if_icmpne jump location

        int max_stack_size_else_block;
        if (ctx.elseBlock != null) {
            max_stack_size_else_block = ctx.elseBlock.accept(this);
        } else {
            code_gen.aconst_null(); // stack: null
            max_stack_size_else_block = 1;
        }

        code_gen.set_to_current_position(_goto.op_1);

        return IntStream.of(
                max_stack_size_condition + 1,
                max_stack_size_then_block,
                max_stack_size_else_block
        ).max().getAsInt();
    }

    @Override
    public Integer visitBlockExpression(final TygerParser.BlockExpressionContext ctx) {

        final Iterator<TygerParser.ExpressionContext> iterator = ctx.expression().iterator();
        while (iterator.hasNext()) {
            final TygerParser.ExpressionContext next = iterator.next();
            next.accept(this);

            // drop any unused values. only the last expression can leave a value for a return.
            while (iterator.hasNext() && code_gen.operand_stack.size > 0) {
                code_gen.pop();
            }

            if (!iterator.hasNext()) {
                types.put(ctx, types.get(next));
            }
        }

        return 0;
    }

    @Override
    public Integer visitBinaryExpression(final TygerParser.BinaryExpressionContext ctx) {
        ctx.left.accept(this);
        ctx.right.accept(this);

        final String operator = ctx.op.getText();
        final CodeGen.Type type_left = types.get(ctx.left);
        final CodeGen.Type type_right = types.get(ctx.right);

        final RuntimeException not_implemented = binary_operator_not_implemented(operator, type_left, type_right);

        // TODO: replace with table
        var x = switch (operator) {
            case "+" -> {
                if (type_left == CodeGen.Type.I && type_right == CodeGen.Type.I) {
                    types.put(ctx, CodeGen.Type.I);
                    code_gen.iadd();
                    yield 0;
                } else {
                    throw not_implemented;
                }
            }
            case "-" -> {
                if (type_left == CodeGen.Type.I && type_right == CodeGen.Type.I) {
                    types.put(ctx, CodeGen.Type.I);
                    code_gen.isub();
                    yield 0;
                } else {
                    throw not_implemented;
                }
            }
            case "/" -> {
                if (type_left == CodeGen.Type.I && type_right == CodeGen.Type.I) {
                    types.put(ctx, CodeGen.Type.I);
                    code_gen.idiv();
                    yield 0;
                } else {
                    throw not_implemented;
                }
            }
            case "*" -> {
                if (type_left == CodeGen.Type.I && type_right == CodeGen.Type.I) {
                    types.put(ctx, CodeGen.Type.I);
                    code_gen.imul();
                    yield 0;
                } else {
                    throw not_implemented;
                }
            }
            case "%" -> {
                if (type_left == CodeGen.Type.I && type_right == CodeGen.Type.I) {
                    types.put(ctx, CodeGen.Type.I);
                    code_gen.irem();
                    yield 0;
                } else {
                    throw not_implemented;
                }
            }
            case "|" -> throw not_implemented;
            case "&" -> throw not_implemented;
            case ">>" -> throw not_implemented;
            case "<<" -> throw not_implemented;
            case "and" -> throw not_implemented;
            case "or" -> throw not_implemented;
            case "==" -> {
                if (type_left == CodeGen.Type.I && type_right == CodeGen.Type.I) {
                    types.put(ctx, CodeGen.Type.I);
                    var if_icmpne = code_gen.if_icmpne();
                    code_gen.iconst_1();
                    var _goto = code_gen._goto();
                    code_gen.set_to_current_position(if_icmpne.op_1);
                    code_gen.iconst_0();
                    code_gen.set_to_current_position(_goto.op_1);
                    yield 0;
                } else {
                    throw not_implemented;
                }
            }
            case "!=" -> throw not_implemented;
            case ">" -> throw not_implemented;
            case ">=" -> throw not_implemented;
            case "<=" -> throw not_implemented;
            case "<" -> throw not_implemented;
            default -> throw new RuntimeException("Binary operator " + operator + " is not implemented.");
        };

        return 0;
    }

    RuntimeException binary_operator_not_implemented(String operator, CodeGen.Type left, CodeGen.Type right) {
        return new RuntimeException(String.format(
                "Code generation for binary operation `%s %s %s` is not implemented yet.",
                left, operator, right
        ));
    }

    @Override
    public Integer visitFunctionCallExpression(final TygerParser.FunctionCallExpressionContext ctx) {
        final List<CodeGen.Type> argumentTypes = new ArrayList<>();
        final Method method = methods.get(ctx.identifier().getText());
        types.put(ctx, method.type);

        int number_of_arguments = 0;
        TygerParser.ExpressionListContext argumentExpression = ctx.expressionList();
        while(argumentExpression != null) {
            number_of_arguments++;
            argumentExpression.accept(this);
            argumentTypes.add(types.get(argumentExpression));
            argumentExpression = argumentExpression.expressionList();
        }

        int index = constant_pool.add_method_ref(generatedClassName, method.name, method.signature);
        code_gen.invokestatic(index, number_of_arguments);

        return Math.max(number_of_arguments, 1);
    }

    @Override
    public Integer visitExpressionList(final TygerParser.ExpressionListContext ctx) {
        final Integer result = ctx.expression().accept(this);
        types.put(ctx, types.get(ctx.expression()));
        return result;
    }

    @Override
    public Integer visitIdentifierExpression(final TygerParser.IdentifierExpressionContext ctx) {
        final String name = ctx.identifier().getText();
        final LocalVariables.LocalVariable local = local_variables.get(name);
        types.put(ctx, local.type);

        if (local.type == CodeGen.Type.I) {
            switch (local.stack_position) {
                case 0 -> code_gen.iload_0();
                case 1 -> code_gen.iload_1();
                case 2 -> code_gen.iload_2();
                case 3 -> code_gen.iload_3();
                default -> code_gen.iload(local.stack_position);
            }
        } else {
            throw new RuntimeException("Generating code for identifier expression is not implemented yet for type: " + local.type);
        }

        return 1;
    }

    @Override
    public Integer visitGroupedExpression(final TygerParser.GroupedExpressionContext ctx) {
        final Integer result = ctx.expression().accept(this);
        types.put(ctx, types.get(ctx.expression()));
        return result;
    }

    @Override
    public Integer visitLiteralExpression_(final TygerParser.LiteralExpression_Context ctx) {
        final Integer result = ctx.literalExpression().accept(this);
        types.put(ctx, types.get(ctx.literalExpression()));
        return result;
    }

    @Override
    public Integer visitLiteralExpression(final TygerParser.LiteralExpressionContext ctx) {
        final String literal = ctx.getText();

        if (ctx.BOOLEAN_LITERAL() != null) {
            types.put(ctx, CodeGen.Type.Z);
            if ("true".equals(literal)) {
                code_gen.iconst_1();
            } else if ("false".equals(literal)) {
                code_gen.iconst_0();
            }
        } else if (ctx.INTEGER_LITERAL() != null) {
            types.put(ctx, CodeGen.Type.I);
            int value = Integer.parseInt(literal);
            if (value == 0) {
                code_gen.iconst_0();
            } else if (value == 1) {
                code_gen.iconst_1();
            } else if (value == 2) {
                code_gen.iconst_2();
            } else if (value == 3) {
                code_gen.iconst_3();
            } else if (value == 4) {
                code_gen.iconst_4();
            } else if (value == 5) {
                code_gen.iconst_5();
            } else if (value <= 0xFF) {
                code_gen.bipush(value);
            } else if (value <= 0xFFFF) {
                code_gen.sipush(value);
            } else {
                throw new RuntimeException("TODO: generation of integers larger than two bytes is not implemented yet.");
            }
        } else {
            throw new RuntimeException("TODO: generation of non-integer, non-boolean literals is not implemented yet.");
        }
        return 1;
    }
}
