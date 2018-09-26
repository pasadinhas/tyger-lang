/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class ExecutionContext {
    public class StackFrame {
        public Map<String, Object> bindings = new HashMap<>();

        public void put(final String name, final Object value) {
            bindings.put(name, value);
        }

        public boolean contains(final String name) {
            return bindings.containsKey(name);
        }

        public Object get(final String name) {
            return bindings.get(name);
        }
    }

    public Stack<StackFrame> stackFrames = new Stack<>();

    public StackFrame pushStackFrame() {
        return stackFrames.push(new StackFrame());
    }

    public StackFrame popStackFrame() {
        return stackFrames.pop();
    }

    public void addBinding(final String name, final Object value) {
        stackFrames.peek().put(name, value);
    }

    public Object get(final String name) {
        for (int i = stackFrames.size() - 1; i >= 0; i--) {
            final StackFrame stackFrame = stackFrames.get(i);
            if (stackFrame.contains(name)) {
                return stackFrame.get(name);
            }
        }

        throw new RuntimeException(String.format("Unknown identifier: %s", name));
    }
}
