package htsjdk.beta.io.bundle;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.reads.ReadsFormats;

/**
 * Namespace for standard constants to specify content type and format for resources
 * contained in a {@link Bundle}.
 */
public class BundleResourceType {

    /**
     * Primary content types for use in resource bundles. These names are aligned with enum values
     * in {@link htsjdk.beta.plugin.HtsContentType}, since each one represents the name of the
     * primary resource required by {@link htsjdk.beta.plugin.HtsCodec}s for that
     * {@link htsjdk.beta.plugin.HtsContentType}.
     */
    public static final String ALIGNED_READS = HtsContentType.ALIGNED_READS.name();
    public static final String HAPLOID_REFERENCE = HtsContentType.HAPLOID_REFERENCE.name();
    public static final String VARIANT_CONTEXTS = HtsContentType.VARIANT_CONTEXTS.name();
    public static final String FEATURES = HtsContentType.FEATURES.name();

    /**
     * file format names for content type {@link BundleResourceType#ALIGNED_READS}
     */
    public static final String READS_SAM = ReadsFormats.SAM;
    public static final String READS_BAM = ReadsFormats.BAM;
    public static final String READS_CRAM = ReadsFormats.CRAM;
    public static final String READS_HTSGET_BAM = ReadsFormats.HTSGET_BAM;

    /**
     * secondary content types names for primary content type {@link #ALIGNED_READS} resources
     */
    public static final String READS_INDEX = "READS_INDEX";

    /**
     * file format names for secondary content type {@link BundleResourceType#READS_INDEX} resources
     */
    public static final String READS_INDEX_BAI = "BAI";
    public static final String READS_INDEX_CRAI = "CRAI";
    public static final String READS_INDEX_CSI = "CSI";

    /**
     * secondary content types names for primary content type {@link #VARIANT_CONTEXTS} resources
     */
    public static final String VARIANTS_INDEX = "VARIANTS_INDEX";

    /**
     * secondary content type names for {@link BundleResourceType#HAPLOID_REFERENCE} resources
     */
    public static final String REFERENCE_DICTIONARY = "REFERENCE_DICTIONARY";
    public static final String REFERENCE_INDEX = "REFERENCE_INDEX";

    // General secondary content types
    public static final String MD5 = "MD5";

}
