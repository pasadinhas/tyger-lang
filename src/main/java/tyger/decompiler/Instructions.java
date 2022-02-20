package tyger.decompiler;

import java.util.Map;

public class Instructions {

    record Instruction(String name, int[] arguments) {}

    private static final Map<Integer, Instruction> instructions = Map.ofEntries(
            Map.entry(0x01, new Instruction("aconst_null", new int[] {})),
            Map.entry(0x03, new Instruction("iconst_0", new int[] {})),
            Map.entry(0x04, new Instruction("iconst_1", new int[] {})),
            Map.entry(0x05, new Instruction("iconst_2", new int[] {})),
            Map.entry(0x06, new Instruction("iconst_3", new int[] {})),
            Map.entry(0x07, new Instruction("iconst_4", new int[] {})),
            Map.entry(0x08, new Instruction("iconst_5", new int[] {})),
            Map.entry(0x10, new Instruction("bipush", new int[] {1})),
            Map.entry(0x11, new Instruction("sipush", new int[] {2})),
            Map.entry(0x15, new Instruction("iload", new int[] {1})),
            Map.entry(0x1a, new Instruction("iload_0", new int[] {})),
            Map.entry(0x1b, new Instruction("iload_1", new int[] {})),
            Map.entry(0x1c, new Instruction("iload_2", new int[] {})),
            Map.entry(0x1d, new Instruction("iload_3", new int[] {})),
            Map.entry(0x36, new Instruction("istore", new int[] {1})),
            Map.entry(0x3b, new Instruction("istore_0", new int[] {})),
            Map.entry(0x3c, new Instruction("istore_1", new int[] {})),
            Map.entry(0x3d, new Instruction("istore_2", new int[] {})),
            Map.entry(0x3e, new Instruction("istore_3", new int[] {})),
            Map.entry(0x57, new Instruction("pop", new int[] {})),
            Map.entry(0x60, new Instruction("iadd", new int[] {})),
            Map.entry(0x64, new Instruction("isub", new int[] {})),
            Map.entry(0x6c, new Instruction("idiv", new int[] {})),
            Map.entry(0x68, new Instruction("imul", new int[] {})),
            Map.entry(0x70, new Instruction("irem", new int[] {})),
            Map.entry(0xa0, new Instruction("if_icmpne", new int[] {2})),
            Map.entry(0xa7, new Instruction("goto", new int[] {2})),
            Map.entry(0xac, new Instruction("ireturn", new int[] {})),
            Map.entry(0xb1, new Instruction("return", new int[] {})),
            Map.entry(0xb2, new Instruction("getstatic", new int[] {2})),
            Map.entry(0xb6, new Instruction("invokevirtual", new int[] {2})),
            Map.entry(0xb8, new Instruction("invokestatic", new int[] {2}))
    );

    public static Instruction get(final int opcode) {
        return instructions.get(opcode);
    }
}
