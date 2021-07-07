package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsFormat;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * Enum representing the formats supported by haploid reference codecs.
 */
public enum HaploidReferenceFormat implements HtsFormat<HaploidReferenceFormat> {

    /**
     * Fasta format
     */
    FASTA;

    /**
     * Convert a string representing a format to a file format value from this enum, or Optional.empty()
     * if no value matches.
     *
     * @param formatString string format
     * @return enum value from {@link HaploidReferenceFormat} that matches {@code format}
     */
    public Optional<HaploidReferenceFormat> formatStringToEnum(final String formatString) {
        ValidationUtils.nonNull(formatString, "format");
        for (final HaploidReferenceFormat haploidReferenceFormat : HaploidReferenceFormat.values()) {
            if (haploidReferenceFormat.name().equals(formatString)) {
                return Optional.of(haploidReferenceFormat);
            }
        }
        return Optional.empty();
    }

}
