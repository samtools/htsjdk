package htsjdk.beta.plugin;

/**
 * Each codec has an associated content type that determines a common set of interfaces exposed by
 * all codecs for that content type. For example, codecs with content type {@link #ALIGNED_READS}
 * all expose a set interfaces for reading and writing aligned reads data.
 * <p>
 *     The packages containing the definitions of the common interfaces that are defined for each of the four
 *     content types are:
 * <ul>
 *     <li> For {@link HtsContentType#ALIGNED_READS} codecs, see the {@link htsjdk.beta.plugin.reads} package </li>
 *     <li> For {@link HtsContentType#HAPLOID_REFERENCE} codecs, see the {@link htsjdk.beta.plugin.hapref} package </li>
 *     <li> For {@link HtsContentType#VARIANT_CONTEXTS} codecs, see the {@link htsjdk.beta.plugin.variants} package </li>
 *     <li> For {@link HtsContentType#FEATURES} codecs, see the {@link htsjdk.beta.plugin.features} package </li>
 * </ul>
 * There can be many codecs for a given content type, each representing a different underlying serialized
 * format and or access mechanism/protocol (for example, SAM, BAM, and CRAM are underlying serialization
 * formats corresponding to the {@link #ALIGNED_READS} content type).
 */
public enum HtsContentType {

    //TODO: where would a FASTQ codec fit ? in the same category (which implies the same interfaces) ?
    // should this be called SEQUENCE ?
    HAPLOID_REFERENCE,      // FASTA
    ALIGNED_READS,          // SAM, BAM, CRAM, htsgetbam, sra,...
    VARIANT_CONTEXTS,       // VCF, BCF
    FEATURES,               // GFF, BED, etc
}
