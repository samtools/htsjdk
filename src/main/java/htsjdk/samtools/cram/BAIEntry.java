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
        } else if (sliceReferenceContext.equals(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT) &&
                (alignmentStart != 0 || alignmentSpan != 0)) {
            //TODO : put the start/span in this error message
            throw new CRAMException("Attempt to unmapped with non zero alignment start or span");
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
