package htsjdk.beta.plugin.reads;

import htsjdk.utils.ValidationUtils;

/**
 * Represents the possible underlying serialized formats for reads data.
 */
public enum ReadsFormat {
    SAM,
    BAM,
    CRAM,
    HTSGET_BAM;
//    HTSGET_CRAM,
//    SRA

    public static ReadsFormat formatFromContentSubType(final String contentSubType) {
        ValidationUtils.nonNull(contentSubType, "contentSubType");
        for (final ReadsFormat f : ReadsFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return f;
            }
        }
        return null;
    }

}
