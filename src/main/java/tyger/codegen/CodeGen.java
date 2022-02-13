package tyger.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HexFormat;

public class CodeGen {

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

    record R0(int position) {}
    record R1(int position, int op_1) {}
    record R2(int position, int op_1, int op_2) {}

    private enum Type { I, Z, V }
    private final ByteBuffer byte_buffer = new ByteBuffer();
    
    //
    //  Instruction set
    //

    public R0 aconst_null() {
        operand_stack.grow();
        return instruction_0(Instruction.aconst_null);
    }

    public R0 iconst_0() {
        operand_stack.grow();
        return instruction_0(Instruction.iconst_0);
    }

    public R0 iconst_1() {
        operand_stack.grow();
        return instruction_0(Instruction.iconst_1);
    }

    public R0 iconst_2() {
        operand_stack.grow();
        return instruction_0(Instruction.iconst_2);
    }

    public R0 iconst_3() {
        operand_stack.grow();
        return instruction_0(Instruction.iconst_3);
    }

    public R0 iconst_4() {
        log("iconst_4");
        operand_stack.grow();
        return instruction_0(Instruction.iconst_4);
    }

    public R0 iconst_5() {
        operand_stack.grow();
        return instruction_0(Instruction.iconst_5);
    }

    public R1 bipush(int value) {
        operand_stack.grow();
        return instruction_1(Instruction.bipush, value);
    }

    public R1 sipush(int value) {
        operand_stack.grow();
        return instruction_2(Instruction.sipush, value);
    }

    public R1 iload(int index) {
        assert index > 3;
        operand_stack.grow();
        return instruction_1(Instruction.iload, index);
    }

    public R0 iload_0() {
        operand_stack.grow();
        return instruction_0(Instruction.iload_0);
    }

    public R0 iload_1() {
        operand_stack.grow();
        return instruction_0(Instruction.iload_1);
    }

    public R0 iload_2() {
        operand_stack.grow();
        return instruction_0(Instruction.iload_2);
    }

    public R0 iload_3() {
        operand_stack.grow();
        return instruction_0(Instruction.iload_3);
    }

    public R1 if_icmpne() {
        return if_icmpne(0x0000);
    }

    public R1 if_icmpne(int jumpTo) {
        operand_stack.shrink(2);
        return instruction_2(Instruction.if_icmpne, jumpTo);
    }

    public R1 _goto() {
        return _goto(0x0000);
    }

    public R1 _goto(int jumpTo) {
        return instruction_2(Instruction._goto, jumpTo);
    }

    public R0 pop() {
        operand_stack.shrink();
        return instruction_0(Instruction.pop);
    }

    public R0 iadd() {
        operand_stack.shrink(); // 2 pops and 1 push
        return instruction_0(Instruction.iadd);
    }

    public R0 isub() {
        operand_stack.shrink(); // 2 pops and 1 push
        return instruction_0(Instruction.isub);
    }

    public R0 idiv() {
        operand_stack.shrink(); // 2 pops and 1 push
        return instruction_0(Instruction.idiv);
    }

    public R0 imul() {
        operand_stack.shrink(); // 2 pops and 1 push
        return instruction_0(Instruction.imul);
    }

    public R0 irem() {
        operand_stack.shrink(); // 2 pops and 1 push
        return instruction_0(Instruction.irem);
    }

    public R0 ireturn() {
        operand_stack.shrink();
        return instruction_0(Instruction.ireturn);
    }

    public R0 _return() {
        return instruction_0(Instruction._return);
    }

    public R1 getstatic(int index) {
        operand_stack.grow();
        return instruction_2(Instruction.getstatic, index);
    }

    public R1 invokevirtual(int index, int number_of_arguments) {
        operand_stack.shrink(number_of_arguments - 1); // pops number_of_arguments and pushes one value
        return instruction_2(Instruction.invokevirtual, index);
    }

    public R1 invokestatic(int index, int number_of_arguments) {
        operand_stack.shrink(number_of_arguments - 1); // pops number_of_arguments and pushes one value
        return instruction_2(Instruction.invokestatic, index);
    }

    //
    //  Handle instructions, generically.
    //

    R0 instruction_0(Instruction instruction) {
        log("{}", instruction.name());
        var position = byte_buffer.size();
        byte_buffer.write_1(instruction.opcode);
        return new R0(position);
    }

    R1 instruction_1(Instruction instruction, int operand) {
        assert operand <= 0xFF; // 1 byte
        log("{} {}", instruction, operand);
        var position = byte_buffer.size();
        byte_buffer.write_2(instruction.opcode, operand);
        return new R1(position, position + 1);
    }

    R1 instruction_2(Instruction instruction, int operands) {
        assert operands <= 0xFFFF; // 2 bytes
        log("{} {}", instruction, operands);
        var position = byte_buffer.size();
        byte_buffer.write_3(instruction.opcode, operands);
        return new R1(position, position + 1);
    }

    //
    //  Update previous instructions
    //

    void set_jump_offset(R1 operation) {
        update_2(operation.op_1, position() - operation.position);
    }

    void update_2(int location, int value) {
        assert value <= 0xFFFF; // 2 bytes

        log(location, "UPDATE: {} (0x{})", value, HexFormat.of().formatHex(new byte[]{
                (byte) (value >>> 8),
                (byte) (value >>> 0)
        }));

        byte_buffer.set(location,     (byte) (value >>> 8));
        byte_buffer.set(location + 1, (byte) (value >>> 0));
    }
    
    //
    //  private utilities
    //

    private void log(String format, Object... args) {
        log(position(), format, args);
    }

    private void log(int position, String format, Object... args) {
        logger.debug(String.format("%3d: ", position) + format, args);
    }

    //
    //  Retrieve data from code generation
    //
    
    public int position() {
        return byte_buffer.size();
    }

    public void reset() {
        byte_buffer.reset();
        operand_stack.reset();
    }

    public byte[] to_byte_array() {
        return byte_buffer.to_byte_array();
    }
}
