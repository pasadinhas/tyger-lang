/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.typechecker;

public class Type {
    public final String code;

    public static final Type FUNCTION = new Type("Function");
    public static final Type BOOLEAN = new Type("boolean");
    public static final Type INTEGER = new Type("int");
    public static final Type FLOAT = new Type("float");
    public static final Type CHARACTER = new Type("char");
    public static final Type STRING = new Type("&String");

    public Type(final String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return String.format("Type{%s}", code);
    }
}
