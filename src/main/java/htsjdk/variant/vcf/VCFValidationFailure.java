package htsjdk.variant.vcf;

import htsjdk.utils.ValidationUtils;

import java.util.Collection;
import java.util.stream.Collectors;

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

    public static <T> String createVersionTransitionErrorMessage(
        final Collection<VCFValidationFailure<T>> errors,
        final VCFHeaderVersion originalVersion
    ) {
        return String.format(
            "Version transition from VCF version %s to %s failed with validation error(s):\n%s%s",
            originalVersion.getVersionString(), VCFHeader.DEFAULT_VCF_VERSION.getVersionString(),
            errors.stream()
                .limit(5)
                .map(VCFValidationFailure::getSourceMessage)
                .collect(Collectors.joining("\n")),
            errors.size() > 5 ? "\n+ " + (errors.size() - 5) + " additional error(s)" : ""
        );
    }

}
