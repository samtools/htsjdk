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
     * Convert a string content subtype to an file format value from this enum, or Optional.empty() if
     * no value matches.
     *
     * @param contentSubType string content subtype
     * @return enum value from {@link ReadsFormat} that matches {@code contentSubType}
     */
    public Optional<ReadsFormat> contentSubTypeToFormat(final String contentSubType) {
        ValidationUtils.nonNull(contentSubType, "contentSubType");
        for (final ReadsFormat f : ReadsFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
