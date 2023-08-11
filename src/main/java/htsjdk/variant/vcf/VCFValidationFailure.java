package htsjdk.variant.vcf;

import htsjdk.utils.ValidationUtils;

/**
 * A class representing a VCF validation failure.
 * @param <T> a type representing the object that is being validated
 */
class VCFValidationFailure<T> {
    private final VCFHeaderVersion targetVersion;
    private final T source;
    private final String sourceMessage;

    /**
     * A VCF validation failure.
     *
     * @param targetVersion the version for which validation failed.
     * @param source the source object being validated
     * @param sourceMessage the validation failure reason
     */
    public VCFValidationFailure(final VCFHeaderVersion targetVersion, final T source, final String sourceMessage) {
        ValidationUtils.nonNull(targetVersion);
        ValidationUtils.nonNull(source);
        ValidationUtils.nonNull(sourceMessage);

        this.targetVersion = targetVersion;
        this.source = source;
        this.sourceMessage = sourceMessage;
    }

    /**
     * @return the source object being validated
     */
    public T getSource() {
        return source;
    }

    /**
     * @return The validation failure reason.
     */
    public String getSourceMessage() {
        return sourceMessage;
    }

    /**
     * @return A formatted message describing the validation failure reason and target version.
     */
    public String getFailureMessage() {
        return String.format(
                "Failure validating %s for reason %s, target version %s",
                source.toString(),
                sourceMessage,
                targetVersion);
    }

    /**
     * @return The version for which validation failed. May be  null.
     */
    public VCFHeaderVersion getTargetVersion() {
        return targetVersion;
    }

}
