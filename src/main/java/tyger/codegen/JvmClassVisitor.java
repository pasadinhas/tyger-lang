package tyger.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tyger.TygerBaseVisitor;
import tyger.TygerParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JvmClassVisitor extends TygerBaseVisitor<Void> {

    private final String generatedClassName = "tyger";
    private final File file;
    private final ConstantPool constant_pool = new ConstantPool();

    record Method(String methodName, String typeDescriptor) {}
    private final List<Method> methods = new ArrayList<>();

    public JvmClassVisitor(final File file) {
        this.file = file;
    }

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
    public Void visitProg(final TygerParser.ProgContext ctx) {
        ctx.functionDeclarationExpression().forEach(f -> f.accept(this));


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
            for (final Method method : methods) {
                write_2(os, 0x0009);                    // access modifiers: public static
                write_2(os, constant_pool.index_of(     // method name
                        new ConstantPool.Utf8(method.methodName)
                ));
                write_2(os, constant_pool.index_of(     // type descriptor
                        new ConstantPool.Utf8(method.typeDescriptor)
                ));
                write_2(os, 0x0000);                    // Number of method attributes
            }

            write_2(os, 0x00_00);                       // Number of attributes

        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    @Override
    public Void visitFunctionDeclarationExpression(final TygerParser.FunctionDeclarationExpressionContext ctx) {
        final String type_descriptor = create_type_signature_string(ctx);
        final String method_name = ctx.identifier().getText();

        constant_pool.add_method_ref(generatedClassName, method_name, type_descriptor);
        methods.add(new Method(method_name, type_descriptor));

        return null;
    }

    private String create_type_signature_string(final TygerParser.FunctionDeclarationExpressionContext ctx) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        var argsList = ctx.argsList();
        while (argsList != null) {
            builder.append(tyger_type_to_java_method_descriptor_type(argsList.typeIdentifier().getText()));
            argsList = argsList.argsList();
        }
        builder.append(")");
        builder.append(tyger_type_to_java_method_descriptor_type(ctx.typeIdentifier().getText()));
        return builder.toString();
    }

    private String tyger_type_to_java_method_descriptor_type(final String type) {
        return switch (type) {
            case "int" -> "I";
            case "bool" -> "B";
            default -> throw new RuntimeException("Conversion of type " + type + " is not implemented yet.");
        };
    }
}
