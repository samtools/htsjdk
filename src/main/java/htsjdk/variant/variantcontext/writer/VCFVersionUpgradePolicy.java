package htsjdk.variant.variantcontext.writer;

/**
 * The policy {@link VCFWriter} will use to determine the version of VCF to write from a given VCF file. This has no
 * effect on the header written by {@link BCF2Writer}.
 * <p>
 * htsjdk's behavior to this point has been to stamp the most recent version of VCF onto all VCF files
 * written by VCFWriter regardless of the input VCF's original version. This had been possible as new versions
 * of VCF were backwards compatible and version upgrading was infallible. VCF 4.3 is stricter than previous versions,
 * meaning that some previously valid files are invalid 4.3 and upgrading from pre-4.3 to 4.3+ can sometimes fail.
 * <p>
 * This class is a temporary workaround to allow opt-in 4.3 writing support in a way that does not break
 * workflows that may process pre-4.3 files that are invalid 4.3, but should be removed once proper versioning
 * support for VCF is incorporated into htsjdk.
 */
public enum VCFVersionUpgradePolicy {
    /**
     * Interpret VCF files with exactly the version that they have on read. The VCF is assumed to be valid
     * for its version and no version validation will be performed. The written VCF will have the same version
     * as the one which was read.
     */
    DO_NOT_UPGRADE,

    /**
     * Inspect the headers of pre-4.3 files to determine if they can be automatically upgraded to 4.3,
     * and if automatic upgrade is possible write them as 4.3, or else write them as 4.2.
     */
    UPGRADE_OR_FALLBACK,

    /**
     * Inspect the headers of pre 4.3 files to determine if they can be automatically upgraded to 4.3,
     * and abort with an error if automatic upgrade is not possible
     */
    UPGRADE_OR_FAIL,
}
