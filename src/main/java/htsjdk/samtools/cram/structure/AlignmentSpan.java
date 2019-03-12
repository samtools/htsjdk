package htsjdk.samtools.cram.structure;

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
            new AlignmentSpan(Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN, 0, 0);

    // minimum alignment start of the reads represented by this span
    // uses a 1-based coordinate system
    private final int start;
    // span from minimum alignment start to maximum alignment end
    // of the reads represented by this span, equal to max(end) - min(start) + 1
    private final int span;

    private final int mappedCount;
    private final int unmappedCount;

    /**
     * Create a new span with a multiple reads in it.
     *
     * @param start minimum alignment start of the reads represented by this span, using a 1-based coordinate system
     * @param span  span from minimum alignment start to maximum alignment end of the reads represented by this span
     *              span = max(end) - min(start) + 1
     * @param mappedCount number of mapped reads in the span
     * @param unmappedCount number of unmapped reads in the span
     */
    public AlignmentSpan(final int start, final int span, final int mappedCount, final int unmappedCount) {
        this.start = start;
        this.span = span;
        this.mappedCount = mappedCount;
        this.unmappedCount = unmappedCount;
    }

    /**
     * Combine two AlignmentSpans
     *
     * @param a the first AlignmentSpan to combine
     * @param b the second AlignmentSpan to combine
     * @return the new combined AlignmentSpan
     */
    public static AlignmentSpan combine(final AlignmentSpan a, final AlignmentSpan b) {
        final int start = Math.min(a.getStart(), b.getStart());

        int span;
        if (a.getStart() == b.getStart()) {
            span = Math.max(a.getSpan(), b.getSpan());
        }
        else {
            span = Math.max(a.getStart() + a.getSpan(), b.getStart() + b.getSpan()) - start;
        }

        final int mappedCount = a.mappedCount + b.mappedCount;
        final int unmappedCount = a.unmappedCount + b.unmappedCount;

        return new AlignmentSpan(start, span, mappedCount, unmappedCount);
    }

    public int getStart() {
        return start;
    }

    public int getSpan() {
        return span;
    }

    public int getMappedCount() {
        return mappedCount;
    }

    public int getUnmappedCount() {
        return unmappedCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlignmentSpan that = (AlignmentSpan) o;

        if (start != that.start) return false;
        if (span != that.span) return false;
        if (mappedCount != that.mappedCount) return false;
        return unmappedCount == that.unmappedCount;
    }

    @Override
    public int hashCode() {
        int result = start;
        result = 31 * result + span;
        result = 31 * result + mappedCount;
        result = 31 * result + unmappedCount;
        return result;
    }
}
