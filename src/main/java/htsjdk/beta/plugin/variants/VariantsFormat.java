package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsFormat;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * Enum representing the formats supported by variants codecs.
 */
public enum VariantsFormat implements HtsFormat<VariantsFormat> {
    VCF,
    BCF;

    /**
     * Convert a format string to a file format value from this enum, or Optional.empty() if
     * no value matches.
     *
     * @param format string forat
     * @return enum value from {@link VariantsFormat} that matches {@code format}, or Optional.empty() if
     * no value matches
     */
    public Optional<VariantsFormat> formatStringToEnum(final String format) {
        ValidationUtils.nonNull(format, "format");
        for (final VariantsFormat f : VariantsFormat.values()) {
            if (f.name().equals(format)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
