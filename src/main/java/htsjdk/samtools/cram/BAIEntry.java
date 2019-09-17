package htsjdk.samtools.cram;

import htsjdk.samtools.cram.ref.ReferenceContext;

// values used to construct BAI index for a CRAM
public class BAIEntry {
    //TODO: replace with alignmentContext/alignmntSpan
    final ReferenceContext sliceReferenceContext; // Note: this should never be a multiple ref context

    final int alignmentStart;
    final int alignmentSpan;

    final int alignedReads;     // mapped
    final int unplacedReads;    // nocoord
    final int unalignedReads;   // unmapped

    final long containerOffset;
    final long sliceHeaderBlockByteOffset;
    final int landmarkIndex;

    public BAIEntry(
            final ReferenceContext sliceReferenceContext,
            final int alignmentStart,
            final int alignmentSpan,
            final int alignedReads,     // mapped (rec.getReadUnmappedFlag() != true)
            final int unplacedReads,    // nocoord alignmentStart == SAMRecord.NO_ALIGNMENT_START
            final int unaligned,        // unmapped (rec.getReadUnmappedFlag() == true)
            final long containerOffset,
            final long sliceHeaderBlockByteOffset,
            final int landmarkIndex
    ) {
        // Note: this should never be a multiple ref context
        if (sliceReferenceContext.equals(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)) {
            throw new CRAMException("Attempt to create BAI entry from a multi ref context");
        } else if ((sliceReferenceContext.equals(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT) &&
                //TODO: many tests fail if we don't allow alignmentStart == -1 or alignmentSpan == 1
                ((alignmentStart != 0 && alignmentStart != -1) || (alignmentSpan != 0 && alignmentSpan != 1)))) {
            throw new CRAMException(
                    String.format("Attempt to unmapped with non zero alignment start (%d) or span (%d)",
                            alignmentStart,
                            alignmentSpan));
        }
        this.sliceReferenceContext = sliceReferenceContext;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
        this.alignedReads = alignedReads;
        this.unplacedReads = unplacedReads;
        this.unalignedReads = unaligned;
        this.containerOffset = containerOffset;
        this.sliceHeaderBlockByteOffset = sliceHeaderBlockByteOffset;
        this.landmarkIndex = landmarkIndex;
    }

    /**
     * Create a BAIEntry from a CRAIEntry (used to read a .crai as a .bai). Note that
     * there are no mapped/unmapped/unplaced counts present in the crai, which makes
     * BAIEntries created this way less full featured (i.e., wrong), but that is inherent
     * in the idea of converting a CRAi to a BAI to satisfy an index query).
     *
     * HTSJDK needs a native implementation satifying queries using a CRAI directly.
     * see https://github.com/samtools/htsjdk/issues/851
     *
     * @param craiEntry
     */
    public BAIEntry(final CRAIEntry craiEntry) {
        this.sliceReferenceContext = new ReferenceContext(craiEntry.getSequenceId());
        this.alignmentStart = craiEntry.getAlignmentStart();
        this.alignmentSpan = craiEntry.getAlignmentSpan();
        this.alignedReads = 0;
        this.unplacedReads = 0;
        this.unalignedReads = 0;
        this.containerOffset = craiEntry.getContainerStartByteOffset();
        this.sliceHeaderBlockByteOffset = craiEntry.getSliceByteOffsetFromCompressionHeaderStart();
        this.landmarkIndex = 0;
    }

    public static BAIEntry combine(final BAIEntry a, final BAIEntry b) {
        if (!a.getEntryReferenceContext().equals(b.getEntryReferenceContext()) ||
                (a.getSliceHeaderBlockByteOffset() != b.getSliceHeaderBlockByteOffset()) ||
                (a.getLandmarkIndex() != b.getLandmarkIndex()) ||
                (a.getContainerOffset() == b.getContainerOffset())) {
            throw new CRAMException(String.format(
                    "Can't combine BAIEntries from different ref contexts (%s/%s)",
                    a.getEntryReferenceContext(),
                    b.getEntryReferenceContext()));
        }
        final int start = Math.min(a.getAlignmentStart(), b.getAlignmentStart());

        int span;
        if (a.getAlignmentStart() == b.getAlignmentStart()) {
            span = Math.max(a.getAlignmentSpan(), b.getAlignmentSpan());
        }
        else {
            span = Math.max(a.getAlignmentStart() + a.getAlignmentSpan(), b.getAlignmentStart() + b.getAlignmentSpan()) - start;
        }

        final int mappedCount = a.getAlignedReads() + b.getAlignedReads();
        final int unmappedCount = a.getUnalignedReads() + b.getUnalignedReads();
        final int unplacedCount = a.getUnplacedReads() + b.getUnplacedReads();

        return new BAIEntry(
                a.getEntryReferenceContext(),
                start,
                span,
                mappedCount,
                unplacedCount,
                unmappedCount,
                a.getContainerOffset(),
                a.getSliceHeaderBlockByteOffset(),
                a.getLandmarkIndex());
    }

    // Note: this should never be a multiple ref context
    public ReferenceContext getEntryReferenceContext() {
        return sliceReferenceContext;
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }

    public int getAlignedReads() {
        return alignedReads;
    }

    public int getUnplacedReads() {
        return unplacedReads;
    }

    public int getUnalignedReads() {
        return unalignedReads;
    }

    public long getContainerOffset() {
        return containerOffset;
    }

    public long getSliceHeaderBlockByteOffset() {
        return sliceHeaderBlockByteOffset;
    }

    public int getLandmarkIndex() {
        return landmarkIndex;
    }

}
