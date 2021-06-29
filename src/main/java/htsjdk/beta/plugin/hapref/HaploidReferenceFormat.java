package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsFormat;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * Enum representing the formats supported by haploid reference codecs.
 */
public enum HaploidReferenceFormat implements HtsFormat<HaploidReferenceFormat> {

    FASTA;

    /**
     * Convert a string content subtype to an file format value from this enum, or Optional.empty() if
     * no value matches.
     *
     * @param contentSubType string content subtype
     * @return enum value from {@link HaploidReferenceFormat} that matches {@code contentSubType}
     */
    public Optional<HaploidReferenceFormat> contentSubTypeToFormat(final String contentSubType) {
        ValidationUtils.nonNull(contentSubType, "contentSubType");
        for (final HaploidReferenceFormat f : HaploidReferenceFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
