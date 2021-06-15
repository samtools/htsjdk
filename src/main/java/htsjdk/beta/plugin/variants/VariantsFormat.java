package htsjdk.beta.plugin.variants;

import htsjdk.utils.ValidationUtils;

public enum VariantsFormat {
    VCF,
    BCF;

    public static VariantsFormat formatFromContentSubType(final String subContentType) {
        ValidationUtils.nonNull(subContentType, "subContentType");
        for (final VariantsFormat f : VariantsFormat.values()) {
            if (f.name().equals(subContentType)) {
                return f;
            }
        }
        return null;
    }
}
