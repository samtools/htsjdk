package htsjdk.samtools.cram.ref;

import htsjdk.samtools.SAMSequenceRecord;

/**
 * Interface used to supply a reference source when reading CRAM files.
 */
public interface CRAMReferenceSource {

    /**
     * Get the reference bases for an entire reference contig.
     *
     * @param sequenceRecord the SAMSequenceRecord identifying the reference being requested
     * @param tryNameVariants if true, attempt to match the requested sequence name against the reference by
     *                       using common name variations, such as adding or removing a leading "chr" prefix
     *                       from the requested name. if false, use exact match
     * @return the upper-cased, normalized (see {@link htsjdk.samtools.cram.build.Utils#normalizeBase})
     * bases representing the requested sequence, or null if the sequence cannot be found
     */
    byte[] getReferenceBases(final SAMSequenceRecord sequenceRecord, final boolean tryNameVariants);

    /**
     * Get the reference bases for a region of a reference contig.
     *
     * @param sequenceRecord the SAMSequenceRecord for the reference contig being requested
     * @param zeroBasedOffset the zero based offset of the starting reference base
     * @param requestedRegionLength the length of the requested reference region
     * @return the bases for the reference region
     */
    byte[] getReferenceBasesByRegion(
            final SAMSequenceRecord sequenceRecord,
            final int zeroBasedOffset,
            final int requestedRegionLength);
}
