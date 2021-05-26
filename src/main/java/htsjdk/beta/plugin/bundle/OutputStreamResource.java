package htsjdk.beta.plugin.bundle;

import htsjdk.utils.ValidationUtils;

import java.io.OutputStream;
import java.util.Optional;

/**
 * An output resource backed by an {@link java.io.OutputStream}.
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
     * @param contentSubType The content subtype for this resource. May not be null or 0-length.
     */
    public OutputStreamResource(
            final OutputStream outputStream,
            final String displayName,
            final String contentType,
            final String contentSubType) {
        super(displayName, contentType, contentSubType);
        ValidationUtils.nonNull(outputStream, "output stream");
        this.outputStream = outputStream;
    }

    @Override
    public Optional<OutputStream> getOutputStream() {
        return Optional.of(outputStream);
    }

    @Override
    public boolean isOutput() { return true; }

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
