package htsjdk.samtools.cram.structure.slice;

import htsjdk.samtools.SAMRecord;

import java.util.Objects;

/**
 * A span of reads on a single reference.
 */
public class SliceAlignment {
    /**
     * A constant to represent an unmapped span.
     */
    public static final SliceAlignment UNMAPPED_SPAN = new SliceAlignment(SAMRecord.NO_ALIGNMENT_START, SliceHeader.NO_ALIGNMENT_SPAN);

    private int start;
    private int span;
    private int count;

    /**
     * Create a new span with a single read in it.
     *
     * @param start alignment start of the span
     * @param span  alignment span
     */
    public SliceAlignment(final int start, final int span) {
        this.setStart(start);
        this.setSpan(span);
        this.count = 1;
    }

    /**
     * Create a new span with a multiple reads in it.
     *
     * @param start alignment start of the span
     * @param span  alignment span
     * @param count number of reads in the span
     */
    public SliceAlignment(final int start, final int span, final int count) {
        this.setStart(start);
        this.setSpan(span);
        this.count = count;
    }

    /**
     * Add alignments to this one.
     *
     * @param sliceAlignment the alignments of the reads to add
     */
    public void add(final SliceAlignment sliceAlignment) {
        final int newStart = Math.min(this.getStart(), sliceAlignment.getStart());
        final int newEnd = Math.max(this.getStart() + this.getSpan(), sliceAlignment.getStart() + sliceAlignment.getSpan());

        this.setStart(newStart);
        this.setSpan(newEnd - newStart);

        this.count += sliceAlignment.getCount();
    }

    public int getStart() {
        return start;
    }

    public void setStart(final int start) {
        this.start = start;
    }

    public int getSpan() {
        return span;
    }

    public void setSpan(final int span) {
        this.span = span;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;

        final SliceAlignment that = (SliceAlignment)obj;

        return this.start == that.start &&
                this.span == that.span &&
                this.count == that.count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, span, count);
    }

}
