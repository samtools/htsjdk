package htsjdk.variant.variantcontext.writer;

/**
 * Defines the policy VCFWriter will use when writing a VCF file from a version before 4.3.
 *
 * This is necessary as VCF 4.3 is stricter than 4.2, meaning that some valid 4.2 files are invalid 4.3
 * and automatically transitioning from pre 4.3 to 4.3+ is not always possible.
 * This class is a temporary workaround to allow opt-in 4.3 writing support in a way that does not break
 * workflows that may process 4.2 files that are invalid 4.3, but should be removed once proper versioning
 * support for VCF is incorporated into htsjdk
 */
public enum VCF42To43VersionTransitionPolicy {
    /**
     * Write pre 4.3 files as 4.2, to which automatic transitioning should always be possible, and
     * write 4.3+ files as 4.3.
     */
    DO_NOT_TRANSITION,

    /**
     * Inspect the headers of pre 4.3 files to determine if they can be automatically transitioned to 4.3,
     * and if automatic transition is possible write them as 4.3, or else write them as 4.2.
     */
    TRANSITION_IF_POSSIBLE,

    /**
     * Inspect the headers of pre 4.3 files to determine if they can be automatically transitioned to 4.3,
     * and abort with an error if automatic transition is not possible
     */
    FAIL_IF_CANNOT_TRANSITION,
}
