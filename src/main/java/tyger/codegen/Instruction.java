package tyger.codegen;

public enum Instruction {
    aconst_null(0x01),
    iconst_0(0x03),
    iconst_1(0x04),
    iconst_2(0x05),
    iconst_3(0x06),
    iconst_4(0x07),
    iconst_5(0x08),
    bipush(0x10),
    sipush(0x11),
    iload(0x15),
    iload_0(0x1a),
    iload_1(0x1b),
    iload_2(0x1c),
    iload_3(0x1d),
    pop(0x57),
    iadd(0x60),
    isub(0x64),
    idiv(0x6c),
    imul(0x68),
    irem(0x70),
    if_icmpne(0xa0),
    _goto(0xa7),
    ireturn(0xac),
    _return(0xb1),
    getstatic(0xb2),
    invokevirtual(0xb6),
    invokestatic(0xb8),
    ;

    public final byte opcode;

    Instruction(final int opcode) {
        this((byte) opcode);
    }
    
    Instruction(final byte opcode) {
        this.opcode = opcode;
    }

}
