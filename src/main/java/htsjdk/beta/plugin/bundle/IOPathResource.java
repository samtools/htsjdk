package htsjdk.beta.plugin.bundle;

import htsjdk.io.IOPath;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Optional;

/**
 * An bundle resource backed by an {@link IOPath}.
 */
public class IOPathResource extends BundleResourceBase {
    private static final long serialVersionUID = 1L;
    private final IOPath ioPath;

    /**
     * @param ioPath The IOPath for this resource. May not be null.
     * @param contentType The content type for this resource. May not be null or 0-length.
     */
    public IOPathResource(final IOPath ioPath, final String contentType) {
        this(ioPath, contentType,null);
    }

    /**
     * @param ioPath The IOPath for this resource. May not be null.
     * @param contentType The content type for this resource. May not be mull or 0-length.
     * @param contentSubType The content subtype for this resource. May not be null or 0-length.
     */
    public IOPathResource(final IOPath ioPath, final String contentType, final String contentSubType) {
        super(ValidationUtils.nonNull(ioPath, "ioPath").getRawInputString(),
                contentType,
                contentSubType);
        this.ioPath = ioPath;
    }

    @Override
    public Optional<IOPath> getIOPath() { return Optional.of(ioPath); }

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
    public Optional<SeekableStream> getSeekableStream() {
        try {
            return Optional.of(new SeekablePathStream(getIOPath().get().toPath()));
        } catch (final IOException e) {
            throw new RuntimeIOException(toString(), e);
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
