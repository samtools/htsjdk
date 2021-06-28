package htsjdk.beta.plugin;

/**
 * Each codec has an associated type that determines the common set of interfaces exposed by all codecs
 * of that type. For example, {@link #ALIGNED_READS} codecs all expose a set interfaces for reading
 * and writing aligned reads data.
 * <p>
 *     The packages containing the definitions of the common interfaces that are defined for each of the four
 *     different codec types are:
 * <ul>
 *     <li> For {@link HtsCodecType#ALIGNED_READS} codecs, see the {@link htsjdk.beta.plugin.reads} package </li>
 *     <li> For {@link HtsCodecType#HAPLOID_REFERENCE} codecs, see the {@link htsjdk.beta.plugin.hapref} package </li>
 *     <li> For {@link HtsCodecType#VARIANT_CONTEXTS} codecs, see the {@link htsjdk.beta.plugin.variants} package </li>
 *     <li> For {@link HtsCodecType#FEATURES} codecs, see the {@link htsjdk.beta.plugin.features} package </li>
 * </ul>
 * There can be many codecs of a given type, each representing a different underlying serialized
 * format and or access mechanism/protocol (for example, SAM, BAM, and CRAM are underlying
 * serialization formats corresponding to the {@link #ALIGNED_READS} codec type).
 */
public enum HtsCodecType {

    //TODO: where would a FASTQ codec fit ? in the same category (which implies the same interfaces) ?
    // should this be called SEQUENCE ?
    HAPLOID_REFERENCE,      // FASTA
    ALIGNED_READS,          // SAM, BAM, CRAM, htsgetbam, sra,...
    VARIANT_CONTEXTS,       // VCF, BCF
    FEATURES,               // GFF, BED, etc
}
