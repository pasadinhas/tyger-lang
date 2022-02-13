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
    private final File file;
    private final String module_name;
    private final ConstantPool constant_pool = new ConstantPool();
    private final CodeGen code_gen = new CodeGen();
    private final Map<ParserRuleContext, CodeGen.Type> types = new HashMap<>();

    record MethodDefinition(String name, String signature, CodeGen.Type type) {}
    private final Map<String, MethodDefinition> method_definitions = new HashMap<>();

    record MethodCode(MethodDefinition definition, byte[] code, int max_stack_size, int max_local_variables) {}
    private final Map<String, MethodCode> methods_code = new HashMap<>();

    public JvmClassVisitor(final String module_name) {
        this.file = new File(module_name + ".class");
        this.module_name = module_name;
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

            void log() {
                // logger.debug("OperandStack[size={},max={}]", size, max);
            }

            int grow() {
                max = Math.max(++size, max);
                log();
                return size;
            }

            int shrink() {
                return shrink(1);
            }

            int shrink(int n) {
                assert size > n - 1;
                size -= n;
                log();
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
        private static final byte ireturn = (byte) 0xac;
        private static final byte _return = (byte) 0xb1;
        private static final byte getstatic = (byte) 0xb2;
        private static final byte invokevirtual = (byte) 0xb6;
        private static final byte invokestatic = (byte) 0xb8;

        record R0(int position) {}
        record R1(int position, int op_1) {}
        record R2(int position, int op_1, int op_2) {}

        private enum Type { I, Z, V }
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
            operand_stack.shrink(2);
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

        R0 ireturn() {
            log("ireturn");
            operand_stack.shrink();
            return instruction_0(ireturn);
        }

        R0 _return() {
            log("return");
            return instruction_0(_return);
        }

        R1 getstatic(int index) {
            log("getstatic #{}", index);
            operand_stack.grow();
            return instruction_2(getstatic, index);
        }

        R1 invokevirtual(int index, int number_of_arguments) {
            log("invokevirtual #{}", index);
            operand_stack.shrink(number_of_arguments - 1); // pops number_of_arguments and pushes one value
            return instruction_2(invokevirtual, index);
        }

        R1 invokestatic(int index, int number_of_arguments) {
            log("invokestatic #{}", index);
            operand_stack.shrink(number_of_arguments - 1); // pops number_of_arguments and pushes one value
            return instruction_2(invokestatic, index);
        }

        int position() {
            return function_code.size();
        }

        void set_jump_offset(R1 operation) {
            update_2(operation.op_1, position() - operation.position);
        }

        void update_2(int location, int value) {
            assert value <= 0xFFFF; // 2 bytes

            log(location, "UPDATE: {} (0x{})", value, HexFormat.of().formatHex(new byte[]{
                    (byte) (value >>> 8),
                    (byte) (value >>> 0)
            }));

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

        byte[] to_byte_array() {
            final byte[] bytes = new byte[function_code.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = function_code.get(i);
            }
            return bytes;
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

    void generate_main_method() {
        code_gen.reset();

        var system_out = constant_pool.add_field_ref("java/lang/System", "out", "Ljava/io/PrintStream;");
        var println = constant_pool.add_method_ref("java/io/PrintStream", "println", "(I)V");
        var tyger_main = constant_pool.add_method_ref(module_name, "main", "()I");

        code_gen.getstatic(system_out);
        code_gen.invokestatic(tyger_main, 0);
        code_gen.invokevirtual(println, 1);
        code_gen._return();

        var method_definition = new MethodDefinition("main", "([Ljava/lang/String;)V", CodeGen.Type.V);
        constant_pool.add_method_ref(module_name, method_definition.name, method_definition.signature);
        methods_code.put(method_definition.name + ":" + method_definition.signature, new MethodCode(
                method_definition,
                code_gen.to_byte_array(),
                code_gen.operand_stack.max,
                1
        ));
    }

    @Override
    public Integer visitModule(final TygerParser.ModuleContext ctx) {
        register_all_methods(ctx.functionDeclarationExpression());

        // Visit all function declarations and register the max stack size.
        var max_stack_size = ctx.functionDeclarationExpression().stream()
                .map(f -> f.accept(this))
                .max(Integer::compareTo)
                .orElseThrow();

        generate_main_method();
        constant_pool.add_class(module_name);
        final int java_lang_object_index = constant_pool.add_class("java/lang/Object");

        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream os = new BufferedOutputStream(fos)
        ) {

            write_4(os, 0xCA_FE_BA_BE);                                     // Magic Number, the famous CAFE BABE
            write_4(os, 0x00_00_00_3D);                                     // Version: Java 17

            write_2(os, constant_pool.size() + 1);                          // Constants Pool size
            os.write(constant_pool.to_byte_array());                        // Write the Constant Pool

            write_2(os, 0x10_11);                                           // Modifiers: SYNTHETIC, FINAL, PUBLIC
            write_2(os, constant_pool.index_of(                             // This class, in the Constants Pool
                    new ConstantPool.Class(module_name)
            ));
            write_2(os, java_lang_object_index);                            // Super class, in the Constants Pool

            write_2(os, 0x00_00);                                           // Number of interfaces
            write_2(os, 0x00_00);                                           // Number of fields

            write_2(os, methods_code.size());                               // Number of methods
            for (final MethodCode method : methods_code.values()) {
                write_2(os, 0x0009);                                        // access modifiers: public static
                write_2(os, constant_pool.index_of(                         // method name
                        new ConstantPool.Utf8(method.definition.name)
                ));
                write_2(os, constant_pool.index_of(                         // type descriptor
                        new ConstantPool.Utf8(method.definition.signature)
                ));
                write_2(os, 0x0001);                                        // Number of method attributes: 1 (Code attribute)
                write_2(os, constant_pool.index_of(
                        new ConstantPool.Utf8("Code")
                ));
                write_4(os,                                                 // Size of "Code" attribute:
                        2 +                                                 // - max_stack_size
                        2 +                                                 // - max_local_variables
                        4 +                                                 // - size of code
                        method.code.length +                                // - actual code
                        2 +                                                 // - exception table: 0
                        2                                                   // - number of attributes: 0
                );
                write_2(os, method.max_stack_size);                         // Max Stack Size
                write_2(os, method.max_local_variables);                    // Max Local Variables
                write_4(os, method.code.length);                            // Method Code length
                os.write(method.code);                                      // Method Code
                write_2(os, 0x0000);                                        // Exception table
                write_2(os, 0x0000);                                        // Number of Attributes: 0
            }

            write_2(os, 0x00_00);                                           // Number of attributes

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

            method_definitions.put(method_name, new MethodDefinition(method_name, signature, return_type));
        });
    }

    @Override
    public Integer visitFunctionDeclarationExpression(final TygerParser.FunctionDeclarationExpressionContext ctx) {
        // reset internal structures
        code_gen.reset();
        local_variables.reset();

        local_variables.scope_push();

        final MethodDefinition method_definition = method_definitions.get(ctx.identifier().getText());

        constant_pool.add_method_ref(module_name, method_definition.name, method_definition.signature);
        constant_pool.add_utf_8("Code");

        var argsList = ctx.argsList();
        int number_of_arguments = 0;
        while (argsList != null) {
            number_of_arguments++;
            final CodeGen.Type type = code_gen.jvm_type(argsList.typeIdentifier().getText());
            final String name = argsList.identifier().getText();
            local_variables.add(name, type);
            argsList = argsList.argsList();
        }

        logger.debug("=".repeat(80));
        logger.debug("=== {}.{}", method_definition.name, method_definition.signature);
        local_variables.scope_peek().values().forEach(local ->
                logger.debug("=== {}: {} ({})", local.stack_position, local.name, local.type));
        logger.debug("=".repeat(80));

        var max_stack_size = ctx.blockExpression().accept(this);
        switch (method_definition.type) {
            case I -> code_gen.ireturn();
            default -> throw new RuntimeException("Code generation for function return is not implemented for type: " + method_definition.type);
        }

        logger.debug("");
        logger.debug("var max_stack_size = {}", max_stack_size);
        logger.debug("code_gen.operand_stack.size = {}", code_gen.operand_stack.size);
        logger.debug("code_gen.operand_stack.max = {}", code_gen.operand_stack.max);

        methods_code.put(method_definition.name + ":" + method_definition.signature, new MethodCode(
                method_definition,
                code_gen.to_byte_array(),
                code_gen.operand_stack.max,
                number_of_arguments
        ));
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

        int stack_size_before_then_block = code_gen.operand_stack.size;
        int max_stack_size_then_block = ctx.block.accept(this);

        final CodeGen.R1 _goto = code_gen._goto(); // goto to skip else block.
        code_gen.set_jump_offset(if_icmpne);       // set if_icmpne jump location

        assert code_gen.operand_stack.size == stack_size_before_then_block + 1;
        code_gen.operand_stack.shrink(); // then-branch and else-branch are mutually exclusive. shrink the operand stack to "drop" the then-branch result
        int max_stack_size_else_block;
        if (ctx.elseBlock != null) {
            max_stack_size_else_block = ctx.elseBlock.accept(this);
        } else {
            code_gen.aconst_null(); // stack: null
            max_stack_size_else_block = 1;
        }

        code_gen.set_jump_offset(_goto);

        return IntStream.of(
                max_stack_size_condition + 1,
                max_stack_size_then_block,
                max_stack_size_else_block
        ).max().getAsInt();
    }

    @Override
    public Integer visitVariableDeclarationExpression(final TygerParser.VariableDeclarationExpressionContext ctx) {
        throw new RuntimeException("Generation of variable declarations is not implemented yet.");
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
                    code_gen.set_jump_offset(if_icmpne);
                    code_gen.operand_stack.shrink(); // only one of the constants is pushed
                    code_gen.iconst_0();
                    code_gen.set_jump_offset(_goto);
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
        final MethodDefinition method_definition = method_definitions.get(ctx.identifier().getText());
        types.put(ctx, method_definition.type);

        int number_of_arguments = 0;
        TygerParser.ExpressionListContext argumentExpression = ctx.expressionList();
        while(argumentExpression != null) {
            number_of_arguments++;
            argumentExpression.accept(this);
            argumentTypes.add(types.get(argumentExpression));
            argumentExpression = argumentExpression.expressionList();
        }

        int index = constant_pool.add_method_ref(module_name, method_definition.name, method_definition.signature);
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
