package htsjdk.beta.exception;

/**
 * Exception thrown when a requested operation is not supported by a plugin codec.
 */
public class HtsjdkUnsupportedOperationException extends HtsjdkPluginException {

    public HtsjdkUnsupportedOperationException(final String message) {
        super(message);
    }
}
