package htsjdk.beta.io.bundle;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.variants.VariantsFormats;

/**
 * Constants for specifying standard content types and formats for resources contained in a {@link Bundle}.
 *
 * Bundles generally contain one primary resource, plus one or more secondary resources such as an index or md5 file.
 * Each resource has an associated content type, and optionally a format. The bundle itself has a primary content
 * type, which is the content type of the primary resource (and the bundle must always contain a resource with the
 * content type that corresponds to the primary content type of the bundle).
 *
 * Although any string can be used as a primary content type in a bundle, the standard primary content types generally
 * correspond to one of the enum values in {@link htsjdk.beta.plugin.HtsContentType}, since each of these has a
 * corresponding {@link htsjdk.beta.plugin.HtsCodec} that handles that type of resource, such as reads or variants.
 *
 * Secondary resource content types can also be any string, but the standard secondary content types are defined
 * here, i.e., for primary content type "READS", a secondary content type might be "READS_INDEX".
 *
 * Finally, each resource in a bundle can have an optional format, which is a string that corresponds to the format
 * for that resource. For example, a primary content type of "READS" might have a format of "BAM".
 */
public class BundleResourceType {

    /**************************************** Common primary content types ******************************************/
    public static final String CT_ALIGNED_READS = "ALIGNED_READS";
    public static final String CT_VARIANT_CONTEXTS = "VARIANT_CONTEXTS";
    public static final String CT_HAPLOID_REFERENCE = "HAPLOID_REFERENCE";
    public static final String CT_FEATURES = "FEATURES";

    /****************************************** Resource types for READS ********************************************/
    /** Formats for primary content type {@link BundleResourceType#CT_ALIGNED_READS} */
    public static final String FMT_READS_SAM = ReadsFormats.SAM;
    public static final String FMT_READS_BAM = ReadsFormats.BAM;
    public static final String FMT_READS_CRAM = ReadsFormats.CRAM;
    public static final String FMT_READS_HTSGET_BAM = ReadsFormats.HTSGET_BAM;

    /** Secondary content types names for content type {@link #CT_ALIGNED_READS} resources */
    public static final String CT_READS_INDEX = "READS_INDEX";
    /** Formats for secondary content type {@link BundleResourceType#CT_READS_INDEX} resources */
    public static final String FMT_READS_INDEX_BAI = "BAI";
    public static final String FMT_READS_INDEX_CRAI = "CRAI";
    public static final String FMT_READS_INDEX_CSI = "CSI";

    /****************************************** Resource types for VARIANTS ******************************************/
    /** Format names for content type {@link BundleResourceType#CT_VARIANT_CONTEXTS} */
    public static final String FMT_VARIANTS_VCF = VariantsFormats.VCF;
    public static final String FMT_VARIANTS_BCF = VariantsFormats.BCF;

    /** Secondary content types for primary content type {@link #CT_VARIANT_CONTEXTS} resources */
    public static final String CT_VARIANTS_INDEX = "VARIANTS_INDEX";

    /****************************************** Resource types for HAPLOID REFERENCES ********************************/
    /** Secondary content types for {@link BundleResourceType#CT_HAPLOID_REFERENCE} resources*/
    public static final String CT_REFERENCE_DICTIONARY = "REFERENCE_DICTIONARY";
    public static final String CT_REFERENCE_INDEX = "REFERENCE_INDEX";


    /****************************************** Resource types for FEATURES ********************************/


    /****************************************** MISCELLANEOUS Resource types  ********************************/
    public static final String CT_MD5 = "MD5";

}
