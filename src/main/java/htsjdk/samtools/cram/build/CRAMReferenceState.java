package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;

public class CRAMReferenceState {

    private final CRAMReferenceSource referenceSource;
    private final SAMFileHeader samFileHeader;

    private byte[] referenceBases = null; // cache the reference bases
    private int referenceBasesContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;

    public CRAMReferenceState(final CRAMReferenceSource cramReferenceSource, final SAMFileHeader samFileHeader) {
        this.referenceSource = cramReferenceSource;
        this.samFileHeader = samFileHeader;
    }

    // might be null
    public byte[] getCurrentReferenceBases() {
        return referenceBases;
    }

    public byte[] getReferenceBases(final int referenceIndex) {
        // for non-coord sorted this could cause a lot of thrashing
        if (referenceIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            if (referenceBases == null ||
                    //referenceBasesContextID == ReferenceContext.UNINITIALIZED_REFERENCE_ID ||
                    referenceIndex != referenceBasesContextID) {
                final SAMSequenceRecord sequence = samFileHeader.getSequence(referenceIndex);
                //TODO remove me
                System.out.println(String.format("Retrieving reference sequence for index %d", referenceIndex));
                referenceBases = referenceSource.getReferenceBases(sequence, true);
                referenceBasesContextID = referenceIndex;
            }
            return referenceBases;
        }

        // retain whatever cached reference bases we may have to minimize subsequent re-fetching
        return null;
    }

}
