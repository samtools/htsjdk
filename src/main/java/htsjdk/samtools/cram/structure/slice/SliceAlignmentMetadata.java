package htsjdk.samtools.cram.structure.slice;

import java.util.Objects;

/**
 * A span of reads on a single reference.
 */
public class SliceAlignmentMetadata {
    private final int start;
    private final int span;
    private final int count;

    /**
     * Create a metadata object with a single record.
     *
     * @param start alignment start
     * @param span  alignment span
     */
    public SliceAlignmentMetadata(final int start, final int span) {
        this(start, span, 1);
    }

    /**
     * Create a metadata object with a multiple records.
     *
     * @param start alignment start
     * @param span  alignment span
     * @param count number of records
     */
    public SliceAlignmentMetadata(final int start, final int span, final int count) {
        this.start = start;
        this.span = span;
        this.count = count;
    }

    /**
     * Combine two SliceAlignmentMetadata objects
     *
     * @param a one object to combine
     * @param b the other object to combine
     * @return the combined SliceAlignmentMetadata
     */
    public static SliceAlignmentMetadata add(final SliceAlignmentMetadata a, final SliceAlignmentMetadata b) {
        final int start = Math.min(a.start, b.start);
        final int span = Math.max(a.start + a.span, b.start + b.span) - start;
        final int count = a.count + b.count;
        return new SliceAlignmentMetadata(start, span, count);
    }

    public int getAlignmentStart() {
        return start;
    }

    public int getAlignmentSpan() {
        return span;
    }

    public int getRecordCount() {
        return count;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;

        final SliceAlignmentMetadata that = (SliceAlignmentMetadata)obj;

        return this.start == that.start &&
                this.span == that.span &&
                this.count == that.count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, span, count);
    }
}
