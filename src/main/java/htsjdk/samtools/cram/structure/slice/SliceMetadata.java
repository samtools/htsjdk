package htsjdk.samtools.cram.structure.slice;

/**
 * A utility class to hold a {@link Slice}'s indexing metadata
 */
public abstract class SliceMetadata {
    protected final int count;

    /**
     * Create a metadata object with a multiple records.
     * @param count number of records
     */
    public SliceMetadata(final int count) {
        this.count = count;
    }

    /**
     * Combine two SliceAlignmentMetadata objects
     *
     * @param a one object to combine
     * @param b the other object to combine
     * @return the combined SliceAlignmentMetadata
     */
    public static SliceMetadata combine(final SliceMetadata a, final SliceMetadata b) {
        return a.add(b);
    }

    abstract protected <T extends SliceMetadata> T add(final T other);

    public int getRecordCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SliceMetadata that = (SliceMetadata) o;

        return count == that.count;
    }

    @Override
    public int hashCode() {
        return count;
    }
}
