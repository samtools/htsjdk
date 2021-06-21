package htsjdk.beta.plugin.variants;

import htsjdk.utils.ValidationUtils;

import java.util.Optional;

public enum VariantsFormat {
    VCF,
    BCF;

    public static Optional<VariantsFormat> contentSubTypeToFormat(final String subContentType) {
        ValidationUtils.nonNull(subContentType, "subContentType");
        for (final VariantsFormat f : VariantsFormat.values()) {
            if (f.name().equals(subContentType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
