package htsjdk.beta.plugin.variants;

import htsjdk.utils.ValidationUtils;

import java.util.Optional;

public enum VariantsFormat {
    VCF,
    BCF;

    /**
     * Convert a string content subtype to an file format value from this enum, or Optional.empty() if
     * no value matches.
     *
     * @param contentSubType string content subtype
     * @return enum value from {@link VariantsFormat} that matches {@code contentSubType}
     */
    public static Optional<VariantsFormat> contentSubTypeToFormat(final String contentSubType) {
        ValidationUtils.nonNull(contentSubType, "contentSubType");
        for (final VariantsFormat f : VariantsFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
