package htsjdk.beta.plugin;

/**
 * Each codec has a codec type that determines the common set of interfaces exposed by all codecs
 * of that type. For example, {@link #ALIGNED_READS} codecs all expose a set interfaces for reading
 * and writing aligned reads data.
 *
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
