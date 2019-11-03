package htsjdk.samtools.cram.ref;


import htsjdk.samtools.SAMSequenceRecord;

/**
 * A lazy CRAMReferenceSource implementation placeholder for use when no explicit reference source has been
 * provided.
 */
public class CRAMLazyReferenceSource implements CRAMReferenceSource {
    byte[] referenceSequence;

    public byte[] getReferenceBases(final int referenceIndex) {
        return referenceSequence;
    }

    @Override
    public byte[] getReferenceBases(final SAMSequenceRecord sequenceRecord, final boolean tryNameVariants) {
        return referenceSequence;
    }

}
