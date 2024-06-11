package htsjdk.beta.plugin;

import htsjdk.beta.plugin.hapref.HaploidReferenceFormats;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.variants.VariantsFormats;

/**
 * The plugin framework defines a set of supported content types, each of which represents a type
 * of HTS data such as "aligned reads". Each content type has an associated set of interfaces that
 * are used with that type (for example, codecs with content type {@link #ALIGNED_READS} expose a
 * set interfaces for reading and writing aligned reads data). The content types and the packages
 * containing the common interfaces that are defined for each type are:
 * <p>
 * <ul>
 *     <li> For {@link HtsContentType#HAPLOID_REFERENCE} codecs, see the {@link htsjdk.beta.plugin.hapref} package </li>
 *     <li> For {@link HtsContentType#ALIGNED_READS} codecs, see the {@link htsjdk.beta.plugin.reads} package </li>
 *     <li> For {@link HtsContentType#VARIANT_CONTEXTS} codecs, see the {@link htsjdk.beta.plugin.variants} package </li>
 *     <li> For {@link HtsContentType#FEATURES} codecs, see the {@link htsjdk.beta.plugin.features} package </li>
 * </ul>
 * <p>
 * There can be many codecs for a given content type, each representing a different version of an
 * underlying format (i.e, {@link ReadsFormats#SAM},
 * {@link ReadsFormats#BAM} or {@link ReadsFormats#CRAM}
 * for {@link HtsContentType#ALIGNED_READS}) and or access mechanism or protocol (for example,
 * {@link ReadsFormats#HTSGET_BAM}).
 */
public enum HtsContentType {

    //where would a FASTQ codec fit ? in the same category (which implies the same interfaces) ?
    /**
     * Haploid reference content type (see {@link HaploidReferenceFormats} for related formats)
     */
    HAPLOID_REFERENCE,

    /**
     * Aligned reads content type (see {@link ReadsFormats} for related formats)
     */
    ALIGNED_READS,

    /**
     * Haploid reference content type (see {@link VariantsFormats} for related formats)
     */
    VARIANT_CONTEXTS,

    /**
     * Features content type (see {@link htsjdk.beta.plugin.features} for related formats)
     */
    FEATURES,
}
