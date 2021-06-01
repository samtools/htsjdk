package htsjdk.beta.plugin.bundle;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * An input resource backed by a {@link htsjdk.samtools.seekablestream.SeekableStream}.
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
     * @param contentSubType The content subtype for this resource. May not be null or 0-length.
     */
    public SeekableStreamResource(
            final SeekableStream seekableStream,
            final String displayName,
            final String contentType,
            final String contentSubType) {
        super(seekableStream, displayName, contentType, contentSubType);
        ValidationUtils.nonNull(seekableStream, "seekable input stream");
        this.seekableStream = seekableStream;
    }

    //TODO: this needs access to the cloudWrapper...
    @Override
    public Optional<SeekableStream> getSeekableStream() { return Optional.of(seekableStream); }

    //TODO: this needs access to the cloudWrapper...
    @Override
    public SignatureProbingInputStream getSignatureProbingStream(final int requestedPrefixSize) {
        //TODO: is the super class' implementation sufficient ?
        throw new IllegalStateException("Signature probing not yet implemented for seekable stream inputs");
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
