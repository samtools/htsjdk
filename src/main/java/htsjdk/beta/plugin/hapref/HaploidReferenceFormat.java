package htsjdk.beta.plugin.hapref;

import htsjdk.utils.ValidationUtils;

public enum HaploidReferenceFormat {

    FASTA;

    public static HaploidReferenceFormat formatFromContentSubType(final String contentSubType) {
        ValidationUtils.nonNull(contentSubType, "contentSubType");
        for (final HaploidReferenceFormat f : HaploidReferenceFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return f;
            }
        }
        return null;
    }

}
