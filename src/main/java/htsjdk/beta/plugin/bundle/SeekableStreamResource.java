package htsjdk.beta.plugin.bundle;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Inherited;
import java.util.Optional;

/**
 * A {@link BundleResource} backed by a {@link htsjdk.samtools.seekablestream.SeekableStream}.
 */
public class SeekableStreamResource extends InputStreamResource {
    private static final long serialVersionUID = 1L;
    private final SeekableStream seekableStream;

    /**
     * @param seekableStream The {@link SeekableStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     */
    public SeekableStreamResource(final SeekableStream seekableStream, final String displayName, final String contentType) {
        this(seekableStream, displayName, contentType, null);
    }

    /**
     * @param seekableStream The {@link SeekableStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     * @param format The format for this resource. May not be null or 0-length.
     */
    public SeekableStreamResource(
            final SeekableStream seekableStream,
            final String displayName,
            final String contentType,
            final String format) {
        super(seekableStream, displayName, contentType, format);
        ValidationUtils.nonNull(seekableStream, "seekable input stream");
        this.seekableStream = seekableStream;
    }

    /**
     * @return the seekable stream managed by this resource, without resetting the stream's state
     */
    @Override
    public Optional<InputStream> getInputStream() {
        // note, this doesn't reset the stream...
        return Optional.of(seekableStream);
    }

    @Override
    public Optional<SeekableStream> getSeekableStream() { return Optional.of(seekableStream); }

    /**
     * {@inheritDoc}
     *
     * @param signatureProbeLength {@inheritDoc}
     * @return Obtain a SignatureProbingStream on this resource. Resets the underlying seekable stream.
     */
    @Override
    public SignatureProbingStream getSignatureProbingStream(final int signatureProbeLength) {
        //we don't want to call the super class' implementation here
        final byte[] signaturePrefix = new byte[signatureProbeLength];
        try {
            // for a SeekableStreamResource, we don't want this code to close the actual SeekableStream that
            // was provided by the caller, so don't use try-with-resources, just seek, read, and reset,
            // so that when the actual stream is subsequently consumed, it will be consumed from the beginning
            seekableStream.seek(0);
            seekableStream.read(signaturePrefix);
            // reset the buffered input stream so the next consumer sees the beginning of the stream
            seekableStream.seek(0);
        } catch (final IOException e) {
            throw new RuntimeIOException(
                    String.format("Error creating signature probing for seekable stream resource with prefix size %d",
                            signatureProbeLength),
                    e);
        }
        return new SignatureProbingStream(signatureProbeLength, signaturePrefix);

    }

    @Override
    public boolean isInput() { return true; }

    @Override
    public boolean hasSeekableStream() { return true; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeekableStreamResource)) return false;
        if (!super.equals(o)) return false;

        SeekableStreamResource that = (SeekableStreamResource) o;

        return getSeekableStream().equals(that.getSeekableStream());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getSeekableStream().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", super.toString(), seekableStream);
    }
}
