package htsjdk.samtools.cram.structure;

/**
 * A span of over a single reference. Immutable.
 *
 * Holds alignment start and span values as well as counts of how many are mapped vs. unmapped vs unmapped-unplaced.
 */
public class AlignmentSpan {

    // minimum alignment start of the reads represented by this span
    // uses a 1-based coordinate system
    private final int alignmentStart;
    // span from minimum alignment start to maximum alignment end
    // of the reads represented by this span, equal to max(end) - min(start) + 1
    private final int alignmentSpan;

    private final int mappedCount;
    private final int unmappedCount;
    private final int unmappedUnplacedCount; // unmapped AND unplaced (subset of unmapped)

    /**
     * This does not retain the alignmentContext referenceContext state (its just a shorthand for passing
     * the rest of the alignmentContext values in).
     *
     * @param alignmentContext
     * @param mappedCount
     * @param unmappedCount
     * @param unmappedUnplacedCount
     */
    public AlignmentSpan(
            final AlignmentContext alignmentContext, // does not retain the alignmentContext referenceContext
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
            final int mappedCount, // this is really "placed" reads, since it includes records that are unmapped/placed
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
            span = Math.max(
                    a.getAlignmentStart() + a.getAlignmentSpan(),
                    b.getAlignmentStart() + b.getAlignmentSpan()
            ) - start;
        }

        final int mappedCount = a.mappedCount + b.mappedCount;
        final int unmappedCount = a.unmappedCount + b.unmappedCount;
        final int unmappedUnplacedCount = a.unmappedUnplacedCount + b.unmappedUnplacedCount;

        return new AlignmentSpan(start, span, mappedCount, unmappedCount, unmappedUnplacedCount);
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
