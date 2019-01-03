package htsjdk.samtools.cram.structure.slice;

import htsjdk.samtools.cram.CRAIEntry;

/**
 * Contains the necessary information about a {@link IndexableSlice} for BAI indexing.
 *
 * It differs from a {@link CRAIEntry} because CRAI does not store recordCount and index.
 */
public class SliceBAIMetadata {
    private final int sequenceId;
    private final SliceAlignment sliceAlignment;
    private final int byteOffset;
    private final long containerByteOffset;
    private final int byteSize;
    private final int index;

    /**
     * Construct a new SliceBAIMetadata object
     *
     * @param sequenceId the reference sequence ID of this Slice, or REFERENCE_INDEX_MULTI or REFERENCE_INDEX_NONE
     * @param sliceAlignment the alignment start position, length of the alignment, and number of records in this Slice
     * @param byteOffset the start byte position in the stream for this Slice
     * @param containerByteOffset the start byte position in the stream for this Slice's Container
     * @param byteSize the size of this Slice when serialized, in bytes
     * @param index the index of this Slice in its Container
     */
    public SliceBAIMetadata(final int sequenceId,
                            final SliceAlignment sliceAlignment,
                            final int byteOffset,
                            final long containerByteOffset,
                            final int byteSize,
                            final int index) {

        this.sequenceId = sequenceId;
        this.sliceAlignment = sliceAlignment;
        this.byteOffset = byteOffset;
        this.containerByteOffset = containerByteOffset;
        this.byteSize = byteSize;
        this.index = index;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public int getAlignmentStart() {
        return sliceAlignment.getStart();
    }

    public int getAlignmentSpan() {
        return sliceAlignment.getSpan();
    }

    public int getRecordCount() {
        return sliceAlignment.getCount();
    }

    public int getByteOffset() {
        return byteOffset;
    }

    public long getContainerByteOffset() {
        return containerByteOffset;
    }

    public int getByteSize() {
        return byteSize;
    }

    public int getIndex() {
        return index;
    }
}
