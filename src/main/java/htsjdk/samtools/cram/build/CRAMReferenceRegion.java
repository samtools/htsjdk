package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.CompressorCache;
import htsjdk.samtools.cram.structure.block.Block;

/**
 * Class representing a (cached) region of a reference, and a CRAMReferenceSource for retrieving additional
 * regions.
 */
public class CRAMReferenceRegion {

    private final CRAMReferenceSource referenceSource;
    private final SAMFileHeader samFileHeader;

    private byte[] referenceBases = null; // cache the reference bases
    private int referenceBasesContextID = ReferenceContext.UNINITIALIZED_REFERENCE_ID;

    /**
     * @param cramReferenceSource {@link CRAMReferenceSource} to use to obtain reference bases
     * @param samFileHeader {@link SAMFileHeader} to use to resolve reference contig names to reference index
     */
    public CRAMReferenceRegion(final CRAMReferenceSource cramReferenceSource, final SAMFileHeader samFileHeader) {
        this.referenceSource = cramReferenceSource;
        this.samFileHeader = samFileHeader;
    }

    /**
     * @return the currently cached reference bases (may ne null)
     */
    public byte[] getCurrentReferenceBases() {
        return referenceBases;
    }

    /**
     * Return the reference bases for the given reference index.
     * @param referenceIndex
     *
     * @return bases for the entire reference contig specifed by {@code referenceIndex}
     */
    public byte[] getReferenceBases(final int referenceIndex) {
        // for non-coord sorted this could cause a lot of thrashing
        if (referenceIndex != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            if (referenceBases == null || referenceIndex != referenceBasesContextID) {
                final SAMSequenceRecord sequence = samFileHeader.getSequence(referenceIndex);
                referenceBases = referenceSource.getReferenceBases(sequence, true);
                if (referenceBases == null) {
                    throw new SAMException(
                            String.format(
                                    "A reference must be supplied (reference sequence %s not found).",
                                    sequence));
                }

                referenceBasesContextID = referenceIndex;
            }
            return referenceBases;
        }

        // retain whatever cached reference bases we may have to minimize subsequent re-fetching
        return null;
    }

    public void setEmbeddedReference(final byte[] embeddedReferenceBytes, final int embeddedReferenceIndex) {
        referenceBasesContextID = embeddedReferenceIndex;
        referenceBases = embeddedReferenceBytes;
    }

}
