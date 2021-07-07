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
     * Convert a string representing a format to a file format value from this enum, or Optional.empty() if
     * no value matches.
     *
     * @param format string format
     * @return enum value from {@link HaploidReferenceFormat} that matches {@code format}
     */
    public Optional<HaploidReferenceFormat> formatStringToEnum(final String format) {
        ValidationUtils.nonNull(format, "format");
        for (final HaploidReferenceFormat f : HaploidReferenceFormat.values()) {
            if (f.name().equals(format)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
