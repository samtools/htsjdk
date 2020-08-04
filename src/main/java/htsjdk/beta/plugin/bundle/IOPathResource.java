package htsjdk.beta.plugin.bundle;

import htsjdk.beta.plugin.registry.SignatureProbingInputStream;
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
 * An bundle resource backed by an {@link IOPath}.
 */
public class IOPathResource extends BundleResource implements Serializable {
    private static final long serialVersionUID = 1L;
    private final IOPath ioPath;
    private int signaturePrefixSize = -1;
    private byte[] signaturePrefix;

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
    public boolean isRandomAccessResource() {
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
    public SignatureProbingInputStream getSignatureProbingStream(final int requestedPrefixSize) {
        if (signaturePrefix == null) {
            signaturePrefix = new byte[requestedPrefixSize];
            try {
                // we don't want this code to close the underlying stream, so don't use try-with-resources
                final InputStream inputStream = new BufferedInputStream(getInputStream().get(), requestedPrefixSize);
                inputStream.mark(requestedPrefixSize);
                inputStream.read(signaturePrefix);
                inputStream.reset();
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
