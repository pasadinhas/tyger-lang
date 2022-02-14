package tyger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Utility class that tries to decompile .class files to help understand why code generation is failing.
 */
public class ClassDecompiler {
    private static final Logger logger = LoggerFactory.getLogger(ClassDecompiler.class);

    private int current = 0;
    private final byte[] bytes;
    private final String filename;

    public static void main(String[] args) throws IOException {
        new ClassDecompiler("math.class").run();
    }

    public ClassDecompiler(String filename) throws IOException {
        this.filename = filename;
        this.bytes = Files.readAllBytes(Path.of(filename));
    }

    public void run() {
        print("Decompiling {} ({} bytes)", filename, bytes.length);
        println();
        print_4(expect_4(0xCAFEBABE), "magic");
        print_2(expect_2(0x0000), "minor");
        print_2(expect_2(0x003D), "major");

        println();
        final int constant_pool_count = read_2();
        print_2(constant_pool_count, "constant_pool_count");
        println();

        for (int i = 1; i < constant_pool_count; i++) {
            print("#{}:", i);

            final int tag = read_1();

            if (tag == 0x07) {
                print_1(tag, "tag = Class");
                print_2(read_2(), "name_index");
                println();
                continue;
            }

            if (tag == 0x01) {
                print_1(tag, "tag = Utf8");
                final int length = read_2();
                print_2(length, "length");
                print(read_utf8(length));
                println();
                continue;
            }

            throw new RuntimeException(String.format("Oops... Did not expect tag %02X", tag));
        }

        print_2(expect_2(0x1011), "access_flags");
        print_2(read_2(), "this_class");
        print_2(read_2(), "super_class");
        print_2(expect_2(0x0000), "interfaces_count"); // expected to be zero
        print_2(expect_2(0x0000), "fields_count"); // expected to be zero
        print_2(expect_2(0x0000), "methods_count"); // expected to be zero
        print_2(expect_2(0x0000), "attributes_count"); // expected to be zero
    }

    private String read_utf8(final int length) {
        final byte[] utf8_bytes = Arrays.copyOfRange(this.bytes, current, current + length);
        current += length;
        return new String(utf8_bytes, StandardCharsets.UTF_8);
    }

    private int expect_4(int expected) {
        assert expected <= 0xFFFFFFFF;
        final int result = read_4();
        if (result != expected) {
            return expectation_failed(expected, result, 4);
        }
        return  result;
    }

    private int expect_2(int expected) {
        assert expected <= 0xFFFF;
        final int result = read_2();
        if (result != expected) {
            return expectation_failed(expected, result, 2);
        }
        return  result;
    }

    private int expectation_failed(final int expected, final int actual, final int n) {
        String hex_format = String.format("%%0%dX", n * 2);
        String error_format = String.format("Expected %s but got %s", hex_format, hex_format);
        throw new RuntimeException(String.format(error_format, expected, actual));
    }

    private int read_1() {
        return bytes[current++] & 0xFF;
    }

    private int read_2() {
        return ((bytes[current++] << 8) & 0xFF00)|(bytes[current++] & 0xFF);
    }

    private int read_4() {
        return (bytes[current++]<<24)&0xff000000|
                (bytes[current++]<<16)&0x00ff0000|
                (bytes[current++]<< 8)&0x0000ff00|
                (bytes[current++]<< 0)&0x000000ff;
    }

    private void print_4(int i, String comment) {
        comment = (comment == null) ? "" : "# " + comment;
        print("{}", String.format("%08X\t\t\t\t\t\t\t%s", i, comment));
    }

    private void print_4(int i) {
        print_4(i, null);
    }

    private void print_1(int i, String comment) {
        comment = (comment == null) ? "" : "# " + comment;
        print("{}", String.format("%02X\t\t\t\t\t\t\t\t\t%s", i, comment));
    }

    private void print_1(int i) {
        print_1(i, null);
    }

    private void print_2(int i, String comment) {
        comment = (comment == null) ? "" : "# " + comment;
        print("{}", String.format("%04X\t\t\t\t\t\t\t\t%s", i, comment));
    }

    private void print_2(int i) {
        print_2(i, null);
    }

    private void print(String format, Object... args) {
        logger.info(format, args);
    }

    private void println() {
        logger.info("");
    }

}
