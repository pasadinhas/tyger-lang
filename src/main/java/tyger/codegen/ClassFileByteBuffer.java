package tyger.codegen;

public class ClassFileByteBuffer extends ByteBuffer {

    public ClassFileByteBuffer() {
    }

    public ClassFileByteBuffer(final int initial_capacity) {
        super(initial_capacity);
    }

    public void write_file_header() {
        write_4(0xCAFEBABE); // Magic Number, the famous CAFE BABE
        write_4(0x0000003D); // Version: Java 17 equivalent
    }

    public void write_constant_pool(final ConstantPool constant_pool) {
        write_2(constant_pool.size() + 1);        // Constants Pool size
        write_all(constant_pool.to_byte_array()); // Constant Pool contents
    }

}
