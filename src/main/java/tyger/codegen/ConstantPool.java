package tyger.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConstantPool {
    
    final static Logger logger = LoggerFactory.getLogger(ConstantPool.class);

    enum ConstantPoolObjectKind {
        CLASS(0x07),
        UTF8(0x01),
        FIELD_REF(0x09),
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
    record FieldRef(String className, String fieldName, String type) {}
    record MethodRef(String className, String methodName, String type) {}
    record NameAndType(String methodName, String type) {}

    private ByteBuffer byte_buffer = new ByteBuffer();
    private int constant_pool_size = 0; // Number of elements in the constant pool.

    record Location(int constant_pool_index, int byte_buffer_index) {}
    private Map<Object, Location> constant_pool_lookup_table = new HashMap<>();

    /**
     * Adds a class to the constant pool.
     *
     * A class uses three bytes:
     * - 1 byte: tag byte
     * - 2 bytes: reference to a UTF8 string (class name)
     */
    public int add_class(String name) {
        final ConstantPool.Class constant_pool_object = new ConstantPool.Class(name);

        // check if the class has already been registered
        if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
            final Location location = constant_pool_lookup_table.get(constant_pool_object);
            logger.trace("Class {} was already registered in #{}", name, location.constant_pool_index);
            return location.constant_pool_index;
        }

        // register the class in the lookup table
        final Location location = new Location(++constant_pool_size, byte_buffer.size());
        constant_pool_lookup_table.put(constant_pool_object, location);
        logger.trace("Adding {} @ {}", constant_pool_object, location);

        // write the class in the constant pool
        byte_buffer.write_1(ConstantPoolObjectKind.CLASS.code); // tag byte
        byte_buffer.write_2(0); // placeholder: Utf8

        // write the utf-8 class name
        final int utf_8_index = add_utf_8(name);

        // update the reference
        byte_buffer.set_2(location.byte_buffer_index + 1, utf_8_index);

        return location.constant_pool_index;
    }

    /**
     * Adds a UTF-8 string to the constant pool.
     *
     * A UTF-8 string uses (3 + n) bytes:
     * - 1 byte: tag byte
     * - 2 bytes: the UTF-8 string length, in bytes (n)
     * - n bytes: the content of the UTF-8 string
     */
    public int add_utf_8(String value) {
        final ConstantPool.Utf8 constant_pool_object = new ConstantPool.Utf8(value);

        // check if this class has already been registered
        if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
            final Location location = constant_pool_lookup_table.get(constant_pool_object);
            logger.trace("UTF-8 String \"{}\" was already registered in #{}", value, location.constant_pool_index);
            return location.constant_pool_index;
        }

        // register the new class in the lookup table
        final Location location = new Location(++constant_pool_size, byte_buffer.size());
        constant_pool_lookup_table.put(constant_pool_object, location);
        logger.trace("Adding {} @ {}", constant_pool_object, location);

        // write the utf-8 string in the constant pool
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte_buffer.write_1(ConstantPoolObjectKind.UTF8.code); // tag byte
        byte_buffer.write_2(bytes.length); // utf-8 value length in bytes
        byte_buffer.write_all(bytes); // utf-8 value content

        return location.constant_pool_index;
    }

    /**
     * Adds a Method Ref to the constant pool.
     *
     * A Method Ref uses 5 bytes:
     * - 1 byte: tag byte
     * - 2 bytes: reference to a Class
     * - 2 bytes: reference to a Name and Type
     */
    public int add_method_ref(String className, String methodName, String type) {
        final ConstantPool.MethodRef constant_pool_object = new ConstantPool.MethodRef(className, methodName, type);

        // check if the method has already been registered
        if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
            final Location location = constant_pool_lookup_table.get(constant_pool_object);
            logger.trace("MethodRef {}.{}{} was already registered in #{}", className, methodName, type, location.constant_pool_index);
            return location.constant_pool_index;
        }

        // register the method ref in the lookup table
        final Location location = new Location(++constant_pool_size, byte_buffer.size());
        constant_pool_lookup_table.put(constant_pool_object, location);
        logger.trace("Adding {} @ {}", constant_pool_object, location);

        // write the method ref in the constant pool
        byte_buffer.write_1(ConstantPoolObjectKind.METHOD_REF.code); // tag byte
        byte_buffer.write_2(0); // placeholder: Class
        byte_buffer.write_2(0); // placeholder: NameAndType

        // write the inner objects
        final int class_index = add_class(className);
        final int name_and_type_index = add_name_and_type(methodName, type);

        // update the references
        byte_buffer.set_2(location.byte_buffer_index + 1, class_index);
        byte_buffer.set_2(location.byte_buffer_index + 3, name_and_type_index);


        return location.constant_pool_index;
    }

    /**
     * Adds a Field Ref to the constant pool.
     *
     * A Field Ref uses 5 bytes:
     * - 1 byte: tag byte
     * - 2 bytes: reference to a Class
     * - 2 bytes: reference to a Name and Type
     */
    public int add_field_ref(String className, String fieldName, String type) {
        final ConstantPool.FieldRef constant_pool_object = new ConstantPool.FieldRef(className, fieldName, type);

        // check if the field ref has already been registered
        if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
            final Location location = constant_pool_lookup_table.get(constant_pool_object);
            logger.trace("FieldRef {}.{}{} was already registered in #{}", className, fieldName, type, location.constant_pool_index);
            return location.constant_pool_index;
        }

        // register the field ref in the lookup table
        final Location location = new Location(++constant_pool_size, byte_buffer.size());
        constant_pool_lookup_table.put(constant_pool_object, location);
        logger.trace("Adding {} @ {}", constant_pool_object, location);

        // write the field ref
        byte_buffer.write_1(ConstantPoolObjectKind.FIELD_REF.code); // tag byte
        byte_buffer.write_2(0); // placeholder: Class
        byte_buffer.write_2(0); // placeholder: NameAndType

        // write the inner objects
        final int class_index = add_class(className);
        final int name_and_type_index = add_name_and_type(fieldName, type);

        // update the references
        byte_buffer.set_2(location.byte_buffer_index + 1, class_index);
        byte_buffer.set_2(location.byte_buffer_index + 3, name_and_type_index);

        return location.constant_pool_index;
    }

    /**
     * Adds a Name and Type to the constant pool.
     *
     * A Field Ref uses 5 bytes:
     * - 1 byte: tag byte
     * - 2 bytes: reference to a UTF-8 (name)
     * - 2 bytes: reference to a UTF-8 (type)
     */
    private int add_name_and_type(final String name, final String type) {
        final ConstantPool.NameAndType constant_pool_object = new ConstantPool.NameAndType(name, type);

        // check if the field ref has already been registered
        if (constant_pool_lookup_table.containsKey(constant_pool_object)) {
            final Location location = constant_pool_lookup_table.get(constant_pool_object);
            logger.trace("NameAndType {}{} was already registered in #{}", name, type, location.constant_pool_index);
            return location.constant_pool_index;
        }

        // register the field ref in the lookup table
        final Location location = new Location(++constant_pool_size, byte_buffer.size());
        constant_pool_lookup_table.put(constant_pool_object, location);
        logger.trace("Adding {} @ {}", constant_pool_object, location);

        // write the field ref
        byte_buffer.write_1(ConstantPoolObjectKind.NAME_AND_TYPE.code); // tag byte
        byte_buffer.write_2(0); // placeholder: Utf8 (name)
        byte_buffer.write_2(0); // placeholder: Utf8 (type)

        // write the inner objects
        final int utf_8_name_index = add_utf_8(name);
        final int utf_8_type_index = add_utf_8(type);

        // update the references
        byte_buffer.set_2(location.byte_buffer_index + 1, utf_8_name_index);
        byte_buffer.set_2(location.byte_buffer_index + 3, utf_8_type_index);

        return location.constant_pool_index;
    }

    public int index_of(Object key) {
        return Optional.ofNullable(constant_pool_lookup_table.get(key))
                .map(location -> location.constant_pool_index)
                .orElseThrow();
    }

    public int index_of_utf8(String value) {
        return index_of(new Utf8(value));
    }

    public int size() {
        return constant_pool_size;
    }

    public byte[] to_byte_array() {
        return byte_buffer.to_byte_array();
    }
}
