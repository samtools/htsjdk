package htsjdk.beta.exception;

/**
 * Base class for unexpected conditions caused by codec plugins.
 */
public class HtsjdkPluginException extends HtsjdkException {
    private static final long serialVersionUID = 1L;

    /**
     * Construct an HtsjdkPluginException.
     *
     * @param message detailed message.
     */
    public HtsjdkPluginException(String message) {
        super(message);
    }

}
