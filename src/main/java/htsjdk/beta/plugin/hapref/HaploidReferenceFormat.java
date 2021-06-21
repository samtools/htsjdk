package htsjdk.beta.plugin.hapref;

import htsjdk.utils.ValidationUtils;

import java.util.Optional;

public enum HaploidReferenceFormat {

    FASTA;

    public static Optional<HaploidReferenceFormat> contentSubTypeToFormat(final String contentSubType) {
        ValidationUtils.nonNull(contentSubType, "contentSubType");
        for (final HaploidReferenceFormat f : HaploidReferenceFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
