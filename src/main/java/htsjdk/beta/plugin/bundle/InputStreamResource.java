package htsjdk.beta.plugin.bundle;

import htsjdk.exception.HtsjdkPluginException;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * An {@link BundleResource} backed by an {@link java.io.InputStream}.
 */
public class InputStreamResource extends BundleResourceBase {
    private static final long serialVersionUID = 1L;
    private final InputStream rawInputStream;           // the stream as provided by the caller
    private BufferedInputStream bufferedInputStream;    // buffered stream wrapper to allow for signature probing

    /**
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
     * Note that it is the caller's responsibility to ensure that {@code inputStream} is closed once the
     * resulting resource is no longer being used.
     *
     * @param inputStream The {@link InputStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     * @param contentSubType The content subtype for this resource. May not be null or 0-length.
     */
    public InputStreamResource(
            final InputStream inputStream,
            final String displayName,
            final String contentType,
            final String contentSubType) {
        super(displayName, contentType, contentSubType);
        ValidationUtils.nonNull(inputStream, "input stream");
        this.rawInputStream = inputStream;
    }

    @Override
    public Optional<InputStream> getInputStream() {
        return Optional.of(bufferedInputStream == null ? rawInputStream : bufferedInputStream);
    }

    @Override
    public SignatureProbingStream getSignatureProbingStream(final int signatureProbeLength) {
        ValidationUtils.validateArg(signatureProbeLength > 0, "signatureProbeLength must be > 0");

        if (bufferedInputStream != null) {
            throw new HtsjdkPluginException(
                    String.format("Only one probing stream can be created for an Input stream resource"));
        }

        final byte[] signaturePrefix = new byte[signatureProbeLength];
        try {
            // for an InputStreamResource, we don't want this code to close the actual rawInputStream that
            // was provided by the caller, since we don't have any way to reconstitute it, so don't use
            // try-with-resources
            bufferedInputStream = new BufferedInputStream(rawInputStream, signatureProbeLength);
            // mark, read, and then reset the buffered stream so that when the actual stream is consumed,
            // once signature probing is set, it will be consumed from the beginning
            bufferedInputStream.mark(signatureProbeLength);
            bufferedInputStream.read(signaturePrefix);
            // reset the buffered input stream so the next consumer sees the beginning of the stream
            bufferedInputStream.reset();
        } catch (final IOException e) {
            throw new RuntimeIOException(
                    String.format("Error during signature probing on %s with prefix size %d",
                            this.getDisplayName(),
                            signatureProbeLength),
                    e);
        }
        return new SignatureProbingStream(signatureProbeLength, signaturePrefix);
    }

    @Override
    public boolean isInput() { return true; }

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
