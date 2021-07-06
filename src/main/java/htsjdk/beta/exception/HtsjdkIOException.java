package htsjdk.beta.exception;

/**
 * A RuntimeException-derived class for propagating IOExceptions caught/thrown by the plugin framework.
 */
public class HtsjdkIOException extends HtsjdkException {

    /**
     * Construct an HtsjdkIOException exception.
     *
     * @param message detailed message.
     */
    public HtsjdkIOException(String message) {
        super(message);
    }

    /**
     * Construct an HtsjdkIOException exception with a specified cause.
     *
     * @param message detailed message.
     * @param cause cause of the exception.
     */
    public HtsjdkIOException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an HtsjdkIOException exception with a message constructed from the cause.
     *
     * @param cause cause of the exception.
     */
    public HtsjdkIOException(Throwable cause) {
        super(cause);
    }
}
