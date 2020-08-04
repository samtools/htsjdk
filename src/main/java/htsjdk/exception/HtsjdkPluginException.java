package htsjdk.exception;

/**
 * Base class for exceptions resulting from ill-behaved codec plugins
 */
public class HtsjdkPluginException extends HtsjdkException {
    /**
     * Constructs an HtsjdkPluginException exception.
     *
     * @param message detailed message.
     */
    public HtsjdkPluginException(String message) {
        super(message);
    }

}
