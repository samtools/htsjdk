package htsjdk.beta.plugin.bundle;

/**
 * Namespace for a standard constants to specify content type and content subtype in resources in a {@link Bundle}.
 */
public class BundleResourceType {

    /**
     * predefined content types
     */
    public static final String READS = "READS";
    public static final String LINEAR_REFERENCE = "LINEAR_REFERENCE";
    public static final String REFERENCE_DICTIONARY = "REFERENCE_DICTIONARY";
    public static final String VARIANTS = "VARIANTS";
    public static final String FEATURES = "FEATURES";
    public static final String READS_INDEX = "READS_INDEX";

    /**
     * predefined content subtypes
     */
    public static final String SUB_TYPE_UNKNOWN = "UNKNOWN";

    /**
     * content subtypes for content type {@link BundleResourceType#READS}
     */
    public static final String READS_SAM = "SAM";
    public static final String READS_BAM = "BAM";
    public static final String READS_CRAM = "CRAM";
    public static final String READS_SRA = "SRA";
    public static final String READS_HTSGET_BAM = "HTSGET_BAM";

    /**
     * content subtypes for content type {@link BundleResourceType#READS_INDEX}
     */
    public static final String READS_INDEX_BAI = "BAI";
    public static final String READS_INDEX_CRAI = "CRAI";
    public static final String READS_INDEX_CSI = "CSI";

}
