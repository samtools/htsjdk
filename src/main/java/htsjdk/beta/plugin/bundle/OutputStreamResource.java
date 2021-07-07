package htsjdk.beta.plugin.bundle;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.utils.ValidationUtils;

import java.io.OutputStream;
import java.util.Optional;

/**
 * An output {@link BundleResource} backed by an {@link java.io.OutputStream}.
 */
public class OutputStreamResource extends BundleResourceBase {
    private static final long serialVersionUID = 1L;
    private final OutputStream outputStream;

    /**
     * @param outputStream The {@link OutputStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     */
    public OutputStreamResource(final OutputStream outputStream, final String displayName, final String contentType) {
        this(outputStream, displayName, contentType, null);
    }

    /**
     * @param outputStream The {@link OutputStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     * @param format The format for this resource. May not be null or 0-length.
     */
    public OutputStreamResource(
            final OutputStream outputStream,
            final String displayName,
            final String contentType,
            final String format) {
        super(displayName, contentType, format);
        ValidationUtils.nonNull(outputStream, "output stream");
        this.outputStream = outputStream;
    }

    @Override
    public Optional<OutputStream> getOutputStream() {
        return Optional.of(outputStream);
    }

    @Override
    public SignatureStream getSignatureStream(int signatureProbeLength) {
        throw new RuntimeException(
                "Cannot create a signature stream for an output stream resource. Use hasInputType to determine if signature stream ");
    }

    @Override
    public Optional<SeekableStream> getSeekableStream() {
        throw new RuntimeException(
                "Cannot create a signature stream on an output stream resource. Use hasSeekableStream to determine if signature stream can be used as seekable stream");
    }

    @Override
    public boolean hasOutputType() { return true; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutputStreamResource)) return false;
        if (!super.equals(o)) return false;

        OutputStreamResource that = (OutputStreamResource) o;

        return getOutputStream().equals(that.getOutputStream());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getOutputStream().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", super.toString(), outputStream);
    }
}
