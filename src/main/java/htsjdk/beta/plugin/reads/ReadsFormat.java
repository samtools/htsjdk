package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsFormat;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * Enum representing the formats supported by reads codecs.
 */
public enum ReadsFormat implements HtsFormat<ReadsFormat> {

    /**
     * SAM file format
     */
    SAM,

    /**
     * BAM file format
     */
    BAM,

    /**
     * CRAM file format
     */
    CRAM,

    /**
     * GA4GH htsget BAM format
     */
    HTSGET_BAM;

    /**
     * Convert a format string to an file format value from this enum, or Optional.empty() if
     * no value matches.
     *
     * @param formatString format string to convert
     * @return enum value from {@link ReadsFormat} that matches {@code format}
     */
    public Optional<ReadsFormat> formatStringToEnum(final String formatString) {
        ValidationUtils.nonNull(formatString, "format");
        for (final ReadsFormat readsFormat : ReadsFormat.values()) {
            if (readsFormat.name().equals(formatString)) {
                return Optional.of(readsFormat);
            }
        }
        return Optional.empty();
    }

}
