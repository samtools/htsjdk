package org.htsjdk.core.exception;

/**
 * Exception type for all exceptions caused at runtime by HTSJDK.
 */
public class HtsjdkException extends RuntimeException {

    /**
     * Constructs an HTSJDK exception.
     *
     * @param message detailed message.
     */
    public HtsjdkException(String message) {
        super(message);
    }

    /**
     * Constructs an HTSJDK exception with a specified cause.
     *
     * @param message detailed message.
     * @param cause   cause of the exception.
     */
    public HtsjdkException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an HTSJDK exception with a message constructed from the cause.
     *
     * @param cause cause of the exception.
     */
    public HtsjdkException(Throwable cause) {
        super(cause);
    }
}
