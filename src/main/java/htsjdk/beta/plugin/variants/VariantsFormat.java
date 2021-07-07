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
     * @param formatString format string to convert
     * @return enum value from {@link VariantsFormat} that matches {@code format}, or Optional.empty() if
     * no value matches
     */
    public Optional<VariantsFormat> formatStringToEnum(final String formatString) {
        ValidationUtils.nonNull(formatString, "format");
        for (final VariantsFormat variantsFormat : VariantsFormat.values()) {
            if (variantsFormat.name().equals(formatString)) {
                return Optional.of(variantsFormat);
            }
        }
        return Optional.empty();
    }
}
