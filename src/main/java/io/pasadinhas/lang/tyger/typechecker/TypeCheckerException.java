/*
 * The copyright of this file belongs to Feedzai. The file cannot be
 * reproduced in whole or part, stored in a retrieval system,
 * transmitted in any form, or by any means electronic, mechanical,
 * photocopying, or otherwise, without prior permission of the owner.
 *
 * © 2018 Feedzai, Strictly Confidential
 */

package io.pasadinhas.lang.tyger.typechecker;

public class TypeCheckerException extends RuntimeException {
    public TypeCheckerException() {
    }

    public TypeCheckerException(final String message) {
        super(message);
    }

    public TypeCheckerException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public TypeCheckerException(final Throwable cause) {
        super(cause);
    }

    public TypeCheckerException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
