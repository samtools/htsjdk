package htsjdk.beta.plugin.bundle;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.reads.ReadsFormats;

/**
 * Namespace for standard constants to specify content type and format for resources
 * contained in a {@link Bundle}.
 */
public class BundleResourceType {

    /**
     * Primary content types. Each of these represents a content type that is the  primary resource type
     * expected by {@link htsjdk.beta.plugin.HtsCodec}s of a given {@link htsjdk.beta.plugin.HtsContentType}.
     * {@link htsjdk.beta.plugin.HtsCodec}s of that type require a primary resource with this name to be
     * present in a {@link Bundle}.
     */
    public static final String ALIGNED_READS = HtsContentType.ALIGNED_READS.name();
    public static final String HAPLOID_REFERENCE = HtsContentType.HAPLOID_REFERENCE.name();
    public static final String VARIANT_CONTEXTS = HtsContentType.VARIANT_CONTEXTS.name();
    public static final String FEATURES = HtsContentType.FEATURES.name();

    /**
     * formats for content type {@link BundleResourceType#ALIGNED_READS}
     */
    public static final String READS_SAM = ReadsFormats.SAM;
    public static final String READS_BAM = ReadsFormats.BAM;
    public static final String READS_CRAM = ReadsFormats.CRAM;
    public static final String READS_HTSGET_BAM = ReadsFormats.HTSGET_BAM;

    /**
     * secondary content types for primary content type {@link #ALIGNED_READS}
     */
    public static final String READS_INDEX = "READS_INDEX";

    /**
     * formats for secondary content type {@link BundleResourceType#READS_INDEX}
     */
    public static final String READS_INDEX_BAI = "BAI";
    public static final String READS_INDEX_CRAI = "CRAI";
    public static final String READS_INDEX_CSI = "CSI";

    public static final String REFERENCE_DICTIONARY = "REFERENCE_DICTIONARY";

}
