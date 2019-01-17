package htsjdk.samtools.cram.structure.slice;

import htsjdk.samtools.cram.CRAMException;

public class UnmappedSliceMetadata extends SliceMetadata {
    /**
     * Create a metadata object with multiple records.
     *
     * @param count number of records
     */
    public UnmappedSliceMetadata(final int count) {
        super(count);
    }

    /**
     * Combine another UnmappedSliceMetadata with this one
     *
     * @param other the other object to combine
     * @return the combined UnmappedSliceMetadata (as a SliceMetadata)
     * @throws CRAMException when attempting to combine different subclasses
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends SliceMetadata> T add(final T other) {
        if (this.getClass() != other.getClass()) {
            throw new CRAMException("Cannot combine MappedSliceMetadata objects with UnmappedSliceMetadata objects");
        }

        return (T) new UnmappedSliceMetadata(this.count + other.count);
    }
}