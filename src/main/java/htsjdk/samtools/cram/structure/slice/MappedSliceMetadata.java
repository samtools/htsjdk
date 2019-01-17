package htsjdk.samtools.cram.structure.slice;

import htsjdk.samtools.cram.CRAMException;

public class MappedSliceMetadata extends SliceMetadata {
    private final int start;
    private final int span;

    /**
     * Create a metadata object with multiple records.
     *
     * @param start alignment start
     * @param span  alignment span
     * @param count number of records
     */
    public MappedSliceMetadata(final int start, final int span, final int count) {
        super(count);
        this.start = start;
        this.span = span;
    }

    /**
     * Create a metadata object with a single record.
     *
     * @param start alignment start
     * @param span  alignment span
     */
    public MappedSliceMetadata(final int start, final int span) {
        this(start, span, 1);
    }

    /**
     * Combine another MappedSliceMetadata with this one
     *
     * @param other the other object to combine
     * @return the combined MappedSliceMetadata (as a SliceMetadata)
     * @throws CRAMException when attempting to combine different subclasses
     */
    @Override
    public MappedSliceMetadata add(final SliceMetadata other) {
        if (this.getClass() != other.getClass()) {
            throw new CRAMException("Cannot combine MappedSliceMetadata objects with UnmappedSliceMetadata objects");
        }

        final MappedSliceMetadata that = (MappedSliceMetadata) other;

        final int start = Math.min(this.start, that.getAlignmentStart());
        final int span = Math.max(this.start + this.span, that.getAlignmentStart() + that.getAlignmentSpan()) - start;
        final int count = this.count + other.count;

        return new MappedSliceMetadata(start, span, count);
    }

    public int getAlignmentStart() {
        return start;
    }

    public int getAlignmentSpan() {
        return span;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MappedSliceMetadata that = (MappedSliceMetadata) o;

        if (start != that.start) return false;
        return span == that.span;
    }

    @Override
    public int hashCode() {
        int result = start;
        result = 31 * result + span;
        return result;
    }
}
