package htsjdk.beta.plugin.reads;

import htsjdk.utils.ValidationUtils;

import java.util.Optional;

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

    public static Optional<ReadsFormat> contentSubTypeToFormat(final String contentSubType) {
        ValidationUtils.nonNull(contentSubType, "contentSubType");
        for (final ReadsFormat f : ReadsFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
