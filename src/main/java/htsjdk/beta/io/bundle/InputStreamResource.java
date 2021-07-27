package htsjdk.beta.io.bundle;

import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * A {@link BundleResource} backed by an {@link java.io.InputStream}.
 */
public class InputStreamResource extends BundleResourceBase {
    private static final long serialVersionUID = 1L;
    private static final int MINIMUM_STREAM_BUFFER_SIZE = 64*1024;
    private final InputStream rawInputStream;           // the stream as provided by the caller
    private BufferedInputStream bufferedInputStream;    // buffered stream wrapper to compensate for signature probing

    /**
     * Create a {@link BundleResource} backed by an InputStream.
     *
     * Note that it is the caller's responsibility to ensure that {@code inputStream} is closed once the
     * resulting resource is no longer being used.
     *
     * @param inputStream The {@link InputStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     */
    public InputStreamResource(final InputStream inputStream, final String displayName, final String contentType) {
        this(inputStream, displayName, contentType, null);
    }

    /**
     * Create a {@link BundleResource} backed by an InputStream, specifying a display name, content
     * type, and format.
     *
     * Note that it is the caller's responsibility to ensure that {@code inputStream} is closed once the
     * resulting resource is no longer being used.
     *
     * @param inputStream The {@link InputStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     * @param format The format for this resource. May not be null or 0-length.
     */
    public InputStreamResource(
            final InputStream inputStream,
            final String displayName,
            final String contentType,
            final String format) {
        super(displayName, contentType, format);
        ValidationUtils.nonNull(inputStream, "input stream");
        this.rawInputStream = inputStream;
    }

    @Override
    public Optional<InputStream> getInputStream() {
        return Optional.of(bufferedInputStream == null ? rawInputStream : bufferedInputStream);
    }

    @Override
    public SignatureStream getSignatureStream(final int signatureProbeLength) {
        ValidationUtils.validateArg(signatureProbeLength > 0, "signatureProbeLength must be > 0");
        if (bufferedInputStream != null) {
            throw new HtsjdkPluginException(
                    String.format("Only one SignatureStream stream can be created for an InputStream resource"));
        }

        final byte[] signaturePrefix = new byte[signatureProbeLength];
        try {
            // Don't use try-with-resources here since we don't want to close the actual rawInputStream
            // that was provided by the caller. Create the buffered stream using at least
            // MINIMUM_STREAM_BUFFER_SIZE to ensure a reasonable buffer size since the buffered stream
            // becomes the stream returned by getInputStream(). Since we need to consumer part of it in
            // order to get the signature bytes, mark, read, and then reset it so that when the stream
            // is ultimately consumed once signature probing is complete, it will be consumed from the
            // beginning.
            bufferedInputStream = new BufferedInputStream(
                    rawInputStream,
                    Integer.max(signatureProbeLength, MINIMUM_STREAM_BUFFER_SIZE));
            bufferedInputStream.mark(signatureProbeLength);
            bufferedInputStream.read(signaturePrefix);
            bufferedInputStream.reset();
        } catch (final IOException e) {
            throw new HtsjdkIOException(
                    String.format("Error during signature probing on %s with prefix size %d",
                            this.getDisplayName(), signatureProbeLength),
                    e);
        }
        return new SignatureStream(signatureProbeLength, signaturePrefix);
    }

    @Override
    public boolean hasInputType() { return true; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InputStreamResource)) return false;
        if (!super.equals(o)) return false;

        InputStreamResource that = (InputStreamResource) o;

        return getInputStream().equals(that.getInputStream());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getInputStream().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", super.toString(), rawInputStream);
    }
}
