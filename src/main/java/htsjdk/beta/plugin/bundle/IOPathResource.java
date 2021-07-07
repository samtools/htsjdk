package htsjdk.beta.plugin.bundle;

import htsjdk.io.IOPath;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Optional;

/**
 * An {@link BundleResource} backed by an {@link IOPath}.
 */
public class IOPathResource extends BundleResourceBase implements Serializable {
    private static final long serialVersionUID = 1L;
    private final IOPath ioPath;

    /**
     * @param ioPath The IOPath for this resource. May not be null.
     * @param contentType The content type for this resource. May not be null or 0-length.
     */
    public IOPathResource(final IOPath ioPath, final String contentType) {
        this(ioPath, contentType, null);
    }

    /**
     * @param ioPath The IOPath for this resource. May not be null.
     * @param contentType The content type for this resource. May not be mull or 0-length.
     * @param format The format for this resource. May not be null or 0-length.
     */
    public IOPathResource(final IOPath ioPath, final String contentType, final String format) {
        super(ValidationUtils.nonNull(ioPath, "ioPath").getRawInputString(),
                contentType,
                format);
        this.ioPath = ioPath;
    }

    @Override
    public Optional<IOPath> getIOPath() { return Optional.of(ioPath); }

    /**
     * @return create a new stream for the IOPath managed by this resource
     */
    @Override
    public Optional<InputStream> getInputStream() {
        return Optional.of(ioPath.getInputStream());
    }

    @Override
    public Optional<OutputStream> getOutputStream() { return Optional.of(ioPath.getOutputStream()); }

    @Override
    public boolean isInput() { return true; }

    @Override
    public boolean isOutput() { return true; }

    @Override
    public boolean hasSeekableStream() {
        // if hasFileSystemProvider is true, we'll be able to obtain a seekable stream
        // on the underlying path; otherwise return false.
        return ioPath.hasFileSystemProvider();
    }

    @Override
    public Optional<SeekableStream> getSeekableStream() {
        try {
            return Optional.of(new SeekablePathStream(getIOPath().get().toPath()));
        } catch (final IOException e) {
            throw new RuntimeIOException(toString(), e);
        }
    }

    @Override
    public SignatureProbingStream getSignatureProbingStream(final int signatureProbeLength) {
        ValidationUtils.validateArg(signatureProbeLength > 0, "signature probe length size must be > 0");

        // get a stream on the underlying IOPath, get reuseable signature probing buffer,
        try (final InputStream is = getInputStream().get();
             final InputStream inputStream = new BufferedInputStream(is, signatureProbeLength)) {
            final byte[] signaturePrefix = new byte[signatureProbeLength];
            inputStream.mark(signatureProbeLength);
            inputStream.read(signaturePrefix);
            inputStream.reset();
            return new SignatureProbingStream(signatureProbeLength, signaturePrefix);
        } catch (final IOException e) {
            throw new RuntimeIOException(
                    String.format("Error getting s signature probing stream with probe length %d", signatureProbeLength),
                    e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        IOPathResource that = (IOPathResource) o;

        return ioPath.equals(that.ioPath);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + ioPath.hashCode();
        return result;
    }

}
