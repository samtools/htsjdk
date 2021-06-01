package htsjdk.beta.plugin.bundle;

import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * An input resource backed by an {@link java.io.InputStream}.
 */
public class InputStreamResource extends BundleResourceBase {
    private static final long serialVersionUID = 1L;
    private final InputStream rawInputStream;           // the stream as provided by the caller
    private BufferedInputStream bufferedInputStream;    // buffered stream wrapper to allow for signature probing
    private int signaturePrefixSize = -1;
    private byte[] signaturePrefix;

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
    public SignatureProbingInputStream getSignatureProbingStream(final int requestedPrefixSize) {
        if (signaturePrefix == null) {
            signaturePrefix = new byte[requestedPrefixSize];
            try {
                // for InputStreamResource, we don't want this code to close the actual rawInputStream
                // that was provided by the caller, since we don't have any way to reconstitute it. so
                // we don't use try-with-resources here
                bufferedInputStream = new BufferedInputStream(rawInputStream, requestedPrefixSize);
                // mark, read, and then reset the buffered stream so that when the actual stream is consumed,
                // once signature probing is set, it will be consumed from the beginning
                bufferedInputStream.mark(requestedPrefixSize);
                bufferedInputStream.read(signaturePrefix);
                // reset the buffered input stream so that when the actual codec goes to consume it,
                // it starts from the beginning
                bufferedInputStream.reset();
                this.signaturePrefixSize = requestedPrefixSize;
            } catch (final IOException e) {
                throw new RuntimeIOException(
                        String.format("Error during signature probing with prefix size %d", requestedPrefixSize),
                        e);
            }
        } else if (requestedPrefixSize > signaturePrefixSize) {
            throw new IllegalArgumentException(
                    String.format("A signature probing size of %d was requested, but a probe size of %d has already been established",
                            requestedPrefixSize, signaturePrefixSize));
        }
        return new SignatureProbingInputStream(signaturePrefix, signaturePrefixSize);
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
