package htsjdk.beta.plugin.bundle;

import htsjdk.beta.plugin.HtsCodecType;
import htsjdk.beta.plugin.reads.ReadsFormat;

/**
 * Namespace for standard constants to specify content type and content subtype for resources
 * contained in a {@link Bundle}.
 */
public class BundleResourceType {

    /**
     * Primary content types. Each of these represents a content type that is required
     * to be present in a {@link Bundle} by that type's corresponding codecs.
     */
    public static final String ALIGNED_READS = HtsCodecType.ALIGNED_READS.name();
    public static final String HAPLOID_REFERENCE = HtsCodecType.HAPLOID_REFERENCE.name();
    public static final String VARIANT_CONTEXTS = HtsCodecType.VARIANT_CONTEXTS.name();
    public static final String FEATURES = HtsCodecType.FEATURES.name();

    /**
     * content subtypes for content type {@link BundleResourceType#ALIGNED_READS}
     */
    public static final String READS_SAM = ReadsFormat.SAM.name();
    public static final String READS_BAM = ReadsFormat.BAM.name();
    public static final String READS_CRAM = ReadsFormat.CRAM.name();
    public static final String READS_HTSGET_BAM = ReadsFormat.HTSGET_BAM.name();

    /**
     * secondary content types for primary content type {@link #ALIGNED_READS}
     */
    public static final String READS_INDEX = "READS_INDEX";

    /**
     * content subtypes for secondary content type {@link BundleResourceType#READS_INDEX}
     */
    public static final String READS_INDEX_BAI = "BAI";
    public static final String READS_INDEX_CRAI = "CRAI";
    public static final String READS_INDEX_CSI = "CSI";

    public static final String REFERENCE_DICTIONARY = "REFERENCE_DICTIONARY";

}
