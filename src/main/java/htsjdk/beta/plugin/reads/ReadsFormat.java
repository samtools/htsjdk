package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsFormat;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * Enum representing the formats supported by reads codecs.
 */
public enum ReadsFormat implements HtsFormat<ReadsFormat> {
    SAM,
    BAM,
    CRAM,
    HTSGET_BAM;

    /**
     * Convert a format string to an file format value from this enum, or Optional.empty() if
     * no value matches.
     *
     * @param format string format
     * @return enum value from {@link ReadsFormat} that matches {@code format}
     */
    public Optional<ReadsFormat> formatStringToEnum(final String format) {
        ValidationUtils.nonNull(format, "format");
        for (final ReadsFormat f : ReadsFormat.values()) {
            if (f.name().equals(format)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
