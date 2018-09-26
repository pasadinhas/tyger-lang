/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.typechecker;

import io.pasadinhas.lang.tyger.ExecutionContext;
import io.pasadinhas.lang.tyger.ast.Typed;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class Scope {
    public class Frame {
        public Map<String, Typed> bindings = new HashMap<>();
    }
    public Stack<Frame> frames = new Stack<>();

    public Frame push() {
        return frames.push(new Frame());
    }

    public Frame pop() {
        return frames.pop();
    }

    public Typed add(final String name, final Typed value) {
        return frames.peek().bindings.put(name, value);
    }

    public Typed lookup(final String name) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            final Frame frame = frames.get(i);
            if (frame.bindings.containsKey(name)) {
                return frame.bindings.get(name);
            }
        }

        throw new RuntimeException(String.format("Unknown identifier: %s", name));
    }
}
