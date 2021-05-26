package htsjdk.beta.plugin.bundle;

import htsjdk.utils.ValidationUtils;

import java.io.InputStream;
import java.util.Optional;

/**
 * An input resource backed by an {@link java.io.InputStream}.
 */
public class InputStreamResource extends BundleResourceBase {
    private static final long serialVersionUID = 1L;
    private final InputStream inputStream;

    /**
     * @param inputStream The {@link InputStream} to use for this resource. May not be null.
     * @param displayName The display name for this resource. May not be null or 0-length.
     * @param contentType The content type for this resource. May not be null or 0-length.
     */
    public InputStreamResource(final InputStream inputStream, final String displayName, final String contentType) {
        this(inputStream, displayName, contentType, null);
    }

    /**
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
        this.inputStream = inputStream;
    }

    @Override
    public Optional<InputStream> getInputStream() { return Optional.of(inputStream); }

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
        return String.format("%s: %s", super.toString(), inputStream);
    }
}
