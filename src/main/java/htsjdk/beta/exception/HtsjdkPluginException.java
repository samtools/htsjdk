package htsjdk.beta.exception;

/**
 * Base class for exceptions resulting from ill-behaved codec plugins.
 */
public class HtsjdkPluginException extends HtsjdkException {
    /**
     * Construct an HtsjdkPluginException.
     *
     * @param message detailed message.
     */
    public HtsjdkPluginException(String message) {
        super(message);
    }

}
