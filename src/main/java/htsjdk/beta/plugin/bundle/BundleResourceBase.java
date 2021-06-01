package htsjdk.beta.plugin.bundle;

import htsjdk.io.IOPath;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.utils.ValidationUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Optional;

/**
 * Base class for {@link BundleResource} implementations.
 */
public abstract class BundleResourceBase implements BundleResource, Serializable {
    private static final long serialVersionUID = 1L;
    private final String displayName;
    private final String contentType;
    private final String contentSubType;

    /**
     *
     * @param displayName A user-recognizable name for this resource. Used for error messages. May not be null or
     *                    0 length.
     * @param contentType The content type for this resource. Can be any string, but it must be unique within a
     *                    given bundle. May not be null or zero length.
     * @param contentSubType The (optional) content subtype for this resource. Can be any string, i.e, "BAM" for
     *                       a resource with content type "READS". Predefined content subtype strings are defined
     *                       in {@link BundleResourceType}.
     */
    public BundleResourceBase(
            final String displayName,
            final String contentType,
            final String contentSubType) {
        ValidationUtils.nonEmpty(displayName, "display name");
        ValidationUtils.nonEmpty(contentType, "content type");
        this.displayName = displayName;
        this.contentType = contentType;
        this.contentSubType = contentSubType;
    }

    @Override
    public String getDisplayName() { return displayName; }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public Optional<String> getContentSubType() {
        return Optional.ofNullable(contentSubType);
    }

    @Override
    public Optional<IOPath> getIOPath() { return Optional.empty(); }

    @Override
    public Optional<InputStream> getInputStream() { return Optional.empty(); }

    @Override
    public Optional<OutputStream> getOutputStream() { return Optional.empty(); }

    @Override
    public Optional<SeekableStream> getSeekableStream() { return Optional.empty(); }

    @Override
    public boolean hasSeekableStream() { return false; }

    @Override
    public boolean isInput() { return false; }

    @Override
    public boolean isOutput() { return false; }

    @Override
    public String toString() {
        return String.format(
                "%s (%s): %s/%s",
                getClass().getSimpleName(),
                getDisplayName(),
                getContentType(),
                getContentSubType().orElse("NONE"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BundleResource)) return false;

        BundleResourceBase that = (BundleResourceBase) o;

        if (!displayName.equals(that.displayName)) return false;
        if (!contentType.equals(that.contentType)) return false;
        return contentSubType != null ? contentSubType.equals(that.contentSubType) : that.contentSubType == null;
    }

    @Override
    public int hashCode() {
        int result = displayName.hashCode();
        result = 31 * result + contentType.hashCode();
        result = 31 * result + (contentSubType != null ? contentSubType.hashCode() : 0);
        return result;
    }
}
