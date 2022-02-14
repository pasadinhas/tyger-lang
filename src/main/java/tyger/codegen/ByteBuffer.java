package tyger.codegen;

import java.util.ArrayList;

public class ByteBuffer {

    private final ArrayList<Byte> bytes;

    public ByteBuffer() {
        this(1024 * 1024); // 1MB default initial capacity.
    }

    public ByteBuffer(int initial_capacity) {
        bytes = new ArrayList<>(initial_capacity);
    }

    public void write_1(final int b) {
        assert b <= 0xFF; // 1 byte
        write_1((byte) b);
    }

    public void write_1(final byte b) {
        bytes.add(b);
    }

    public void write_2(final byte b1, final byte b2) {
        write_1(b1);
        write_1(b2);
    }

    public void write_2(final int b) {
        assert b <= 0xFFFF; // 2 bytes
        write_1((byte) (b >>> 8));
        write_1((byte) (b >>> 0));
    }

    public void write_2(final byte b1, final int b2) {
        write_1(b1);
        write_1(b2);
    }

    public void write_3(final byte b, final int ops) {
        assert ops <= 0xFFFF; // 2 bytes
        write_1(b);
        write_2(ops);
    }

    public void write_4(final int bytes) {
        assert bytes <= 0xFFFFFFFF; // 4 bytes
        write_1((byte) (bytes >>> 24));
        write_1((byte) (bytes >>> 16));
        write_1((byte) (bytes >>>  8));
        write_1((byte) (bytes >>>  0));
    }



    public void write_all(final byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            this.write_1(bytes[i]);
        }
    }

    public int size() {
        return bytes.size();
    }

    public void set(final int index, final byte b) {
        assert bytes.get(index) == 0;
        bytes.set(index, b);
    }

    public void set_1(final int index, final byte b) {
        set_1(index, b);
    }

    public void set_2(final int index, final int b) {
        assert b <= 0xFFFF; // 2 bytes
        set(index, (byte) (b >>> 8));
        set(index + 1, (byte) (b >>> 0));
    }

    public void reset() {
        bytes.clear();
    }

    public byte[] to_byte_array() {
        final byte[] byte_array = new byte[bytes.size()];
        for (int i = 0; i < byte_array.length; i++) {
            byte_array[i] = bytes.get(i);
        }
        return byte_array;
    }
}
