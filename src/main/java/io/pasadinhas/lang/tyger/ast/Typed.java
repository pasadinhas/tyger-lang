/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.ast;

import io.pasadinhas.lang.tyger.typechecker.Type;
import io.pasadinhas.lang.tyger.typechecker.TypeChecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

abstract public class Typed implements TypeChecked {
    private static final Logger logger = LoggerFactory.getLogger(Typed.class);

    private final List<Consumer<Type>> listeners = new ArrayList<>();

    protected Type type = null;

    public Type getType() {
        return type;
    }

    public boolean hasType(final Type type) {
        return this.type.equals(type);
    }

    public void setType(final Type type) {
        if (type != null) {
            logger.warn("Overriding non-null type '{}' to '{}'.", this.type, type);
        }

        this.type = type;

        this.listeners.forEach(listener -> listener.accept(this.type));
    }

    public void listenToTypeChange(final Consumer<Type> typeConsumer) {
        this.listeners.add(typeConsumer);
    }
}
