package htsjdk.exception;

/**
 * Exception type for all exceptions caused at runtime by HTSJDK.
 */
public class HtsjdkException extends RuntimeException {

    /**
     * Constructs an HtsjdkException.
     *
     * @param message detailed message.
     */
    public HtsjdkException(String message) {
        super(message);
    }

    /**
     * Constructs an HtsjdkException with a specified cause.
     *
     * @param message detailed message.
     * @param cause   cause of the exception.
     */
    public HtsjdkException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an HtsjdkException with a message constructed from the cause.
     *
     * @param cause cause of the exception.
     */
    public HtsjdkException(Throwable cause) {
        super(cause);
    }
}
