package htsjdk.samtools.cram.structure;

// AlignmentContext has (used in CompressionHeader, Slice, CRAMRecord):
//    private final ReferenceContext referenceContext;
//    private final int alignmentStart;
//    private final int alignmentSpan;

// Slice has:
// private AlignmentContext alignmentContext;
// private int mappedReadsCount = 0;   // mapped (rec.getReadUnmappedFlag() != true)
// private int unmappedReadsCount = 0; // unmapped (rec.getReadUnmappedFlag() == true)
// private int unplacedReadsCount = 0; // nocoord (alignmentStart == SAMRecord.NO_ALIGNMENT_START)

// AlignmentSpan has:
//  start: minimum alignment start of the reads represented by this span uses a 1-based coordinate system
//  span: from minimum alignment start to maximum alignment end of reads represented by this span == max(end) - min(start) + 1
//  private final int start;
//  private final int span;
//  private final int mappedCount;
//  private final int unmappedCount;

// BAIEntry has:
//    final ReferenceContext sliceReferenceContext; // Note: this should never be a multiple ref context
//    final int alignmentStart;
//    final int alignmentSpan;
//    final int alignedReads;     // mapped
//    final int unplacedReads;    // nocoord
//    final int unalignedReads;   // unmapped

//    final long containerOffset;
//    final long sliceHeaderBlockByteOffset;
//    final int landmarkIndex;

//************
// Proposed:
// AlignmentContext has:
//    private final ReferenceContext referenceContext;
//    private final int alignmentStart;
//    private final int alignmentSpan;

// AlignmentSpan has:
//  final AlignmentContext alignmentContext
//    final int mappedReadCount;     // mapped
//    final int unplacedReadCount;   // nocoord
//    final int unmappedReadCount;   // unmapped

// Slice has:
// private AlignmentContext alignmentContext;
// private int mappedReadsCount = 0;   // mapped (rec.getReadUnmappedFlag() != true)
// private int unmappedReadsCount = 0; // unmapped (rec.getReadUnmappedFlag() == true)
// private int unplacedReadsCount = 0; // nocoord (alignmentStart == SAMRecord.NO_ALIGNMENT_START)

// BAIEntry has:
//    final ReferenceContext sliceReferenceContext; // Note: this should never be a multiple ref context
//    final int alignmentStart;
//    final int alignmentSpan;
//    final int alignedReads;     // mapped
//    final int unplacedReads;    // nocoord
//    final int unalignedReads;   // unmapped

//    final long containerOffset;
//    final long sliceHeaderBlockByteOffset;
//    final int landmarkIndex;

/**
 * A span of reads on a single reference.  Immutable.
 *
 * Holds alignment start and span values as well as counts of how many are mapped vs. unmapped.
 */
public class AlignmentSpan {
    /**
     * A constant to represent a span of unmapped-unplaced reads.
     */
    public static final AlignmentSpan UNPLACED_SPAN =
            new AlignmentSpan(
                    AlignmentContext.NO_ALIGNMENT_START,
                    AlignmentContext.NO_ALIGNMENT_SPAN,
                    0, 0, 0);

    // minimum alignment start of the reads represented by this span
    // uses a 1-based coordinate system
    private final int alignmentStart;
    // span from minimum alignment start to maximum alignment end
    // of the reads represented by this span, equal to max(end) - min(start) + 1
    private final int alignmentSpan;

    private final int mappedCount;
    private final int unmappedCount;
    private final int unmappedUnplacedCount; // unmapped AND unplaced

    public AlignmentSpan(
            final AlignmentContext alignmentContext,
            final int mappedCount,
            final int unmappedCount,
            final int unmappedUnplacedCount) {
        this.alignmentStart = alignmentContext.getAlignmentStart();
        this.alignmentSpan = alignmentContext.getAlignmentSpan();
        this.mappedCount = mappedCount;
        this.unmappedCount = unmappedCount;
        this.unmappedUnplacedCount = unmappedUnplacedCount;
    }

    /**
     * Create a new span with a multiple reads in it.
     *
     * @param alignmentStart minimum alignment start of the reads represented by this span, using a 1-based coordinate system
     * @param alignmentSpan  span from minimum alignment start to maximum alignment end of the reads represented by this span
     *              span = max(end) - min(start) + 1
     * @param mappedCount number of mapped reads in the span
     * @param unmappedCount number of unmapped reads in the span
     */
    public AlignmentSpan(
            final int alignmentStart,
            final int alignmentSpan,
            final int mappedCount,
            final int unmappedCount,
            final int unmappedUnplacedCount) {
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
        this.mappedCount = mappedCount;
        this.unmappedCount = unmappedCount;
        this.unmappedUnplacedCount = unmappedUnplacedCount;
    }

    /**
     * Combine two AlignmentSpans
     *
     * @param a the first AlignmentSpan to combine
     * @param b the second AlignmentSpan to combine
     * @return the new combined AlignmentSpan
     */
    public static AlignmentSpan combine(final AlignmentSpan a, final AlignmentSpan b) {
        final int start = Math.min(a.getAlignmentStart(), b.getAlignmentStart());

        int span;
        if (a.getAlignmentStart() == b.getAlignmentStart()) {
            span = Math.max(a.getAlignmentSpan(), b.getAlignmentSpan());
        }
        else {
            span = Math.max(a.getAlignmentStart() + a.getAlignmentSpan(), b.getAlignmentStart() + b.getAlignmentSpan()) - start;
        }

        final int mappedCount = a.mappedCount + b.mappedCount;
        final int unmappedCount = a.unmappedCount + b.unmappedCount;
        final int unplacedCount = a.unmappedUnplacedCount + b.unmappedUnplacedCount;

        return new AlignmentSpan(start, span, mappedCount, unmappedCount, unplacedCount);
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }

    // mapped and placed only
    public int getMappedCount() {
        return mappedCount;
    }

    // unmapped, placed or unplaced
    public int getUnmappedCount() {
        return unmappedCount;
    }

    // unmapped unplaced only, overlaps with getUnmappedCount (which includes unmapped placed and unplaced)
    public int getUnmappedUnplacedCount() { return unmappedUnplacedCount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlignmentSpan that = (AlignmentSpan) o;

        if (getAlignmentStart() != that.getAlignmentStart()) return false;
        if (getAlignmentSpan() != that.getAlignmentSpan()) return false;
        if (getMappedCount() != that.getMappedCount()) return false;
        if (getUnmappedCount() != that.getUnmappedCount()) return false;
        return getUnmappedUnplacedCount() == that.getUnmappedUnplacedCount();
    }

    @Override
    public int hashCode() {
        int result = getAlignmentStart();
        result = 31 * result + getAlignmentSpan();
        result = 31 * result + getMappedCount();
        result = 31 * result + getUnmappedCount();
        result = 31 * result + getUnmappedUnplacedCount();
        return result;
    }
}
