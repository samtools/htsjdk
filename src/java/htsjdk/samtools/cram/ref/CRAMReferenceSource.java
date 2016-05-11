package htsjdk.samtools.cram.ref;

import htsjdk.samtools.SAMSequenceRecord;

/**
 * Interface used to supply a reference source when reading CRAM files.
 */
public interface CRAMReferenceSource {

    /**
     * getReferenceBases
     * @param sequenceRecord the SAMSequenceRecord identifying the reference
     *                       being requested
     * @param tryNameVariants if true, attempt to match the requested sequence name
     *                        against the reference by using common name variations,
     *                        such as adding or removing a leading "chr" prefix
     *                        from the requested name. if false, use exact match
     * @return the bases representing the requested sequence. or null if the sequence
     *          cannot be found
     */
    byte[] getReferenceBases(final SAMSequenceRecord sequenceRecord, final boolean tryNameVariants);
}
