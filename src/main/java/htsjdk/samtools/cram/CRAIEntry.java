package htsjdk.samtools.cram;

import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A class representing CRAI index entry: file and alignment offsets for each slice.
 * Created by vadim on 10/08/2015.
 */
public class CRAIEntry implements Comparable<CRAIEntry> {
    private final ReferenceContext referenceContext;
    private final int alignmentStart;
    private final int alignmentSpan;

    // this Slice's Container's offset in bytes from the beginning of the stream
    // equal to Slice.containerOffset and Container.offset
    private final long containerStartByteOffset;
    // this Slice's offset in bytes from the beginning of its Container
    // equal to Slice.offset and Container.landmarks[Slice.index]
    private final int sliceByteOffset;
    private final int sliceByteSize;

    private static final int CRAI_INDEX_COLUMNS = 6;
    private static final String ENTRY_FORMAT = "%d\t%d\t%d\t%d\t%d\t%d";

    public CRAIEntry(final ReferenceContext referenceContext,
                     final int alignmentStart,
                     final int alignmentSpan,
                     final long containerStartByteOffset,
                     final int sliceByteOffset,
                     final int sliceByteSize) {
        if (referenceContext.isMultipleReference()) {
            throw new CRAMException("Cannot directly index a multiref slice.  Index by its constituent references instead.");
        }

        this.referenceContext = referenceContext;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
        this.containerStartByteOffset = containerStartByteOffset;
        this.sliceByteOffset = sliceByteOffset;
        this.sliceByteSize = sliceByteSize;
    }

    /**
     * Create a CRAI Entry from a serialized CRAI index line.
     *
     * @param line string formatted as a CRAI index entry
     * @throws CRAIIndex.CRAIIndexException
     */
    public CRAIEntry(final String line) {
        final String[] chunks = line.split("\t");
        if (chunks.length != CRAI_INDEX_COLUMNS) {
            throw new CRAIIndex.CRAIIndexException(
                    "Malformed CRAI index entry: expecting " + CRAI_INDEX_COLUMNS + " columns but got " + chunks.length);
        }

        try {
            referenceContext = new ReferenceContext(Integer.parseInt(chunks[0]));
            alignmentStart = Integer.parseInt(chunks[1]);
            alignmentSpan = Integer.parseInt(chunks[2]);
            containerStartByteOffset = Long.parseLong(chunks[3]);
            sliceByteOffset = Integer.parseInt(chunks[4]);
            sliceByteSize = Integer.parseInt(chunks[5]);
        } catch (final NumberFormatException e) {
            throw new CRAIIndex.CRAIIndexException(e);
        }
    }

    /**
     * Serialize the entry to a CRAI index stream.
     * @param os stream to write to
     */
    public void writeToStream(OutputStream os) {
        try {
            os.write(serializeToString().getBytes());
            os.write('\n');
        }
        catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Format the entry as a string suitable for serialization in the CRAI index
     */
    private String serializeToString() {
        return String.format(ENTRY_FORMAT,
                referenceContext.getSerializableId(), alignmentStart, alignmentSpan,
                containerStartByteOffset, sliceByteOffset, sliceByteSize);
    }

    @Override
    public String toString() { return serializeToString(); }

    /**
     * Sort by numerical order of reference sequence ID, except that unmapped-unplaced reads come last
     *
     * For valid reference sequence ID (placed reads):
     * - sort by alignment start
     * - if alignment start is equal, sort by container offset
     * - if alignment start and container offset are equal, sort by slice offset
     *
     * For unmapped-unplaced reads:
     * - ignore (invalid) alignment start value
     * - sort by container offset
     * - if container offset is equal, sort by slice offset
     *
     * @param other the CRAIEntry to compare against
     * @return int representing the comparison result, suitable for ordering
     */
    @Override
    public int compareTo(final CRAIEntry other) {
        if (referenceContext != other.referenceContext) {
            if (referenceContext.isUnmappedUnplaced())
                return 1;
            if (other.referenceContext.isUnmappedUnplaced())
                return -1;
            return Integer.compare(referenceContext.getSequenceId(), other.referenceContext.getSequenceId());
        }

        // only sort by alignment start values for placed entries
        if (! referenceContext.isUnmappedUnplaced() && alignmentStart != other.alignmentStart) {
            return Integer.compare(alignmentStart, other.alignmentStart);
        }

        if (containerStartByteOffset != other.containerStartByteOffset) {
            return Long.compare(containerStartByteOffset, other.containerStartByteOffset);
        }

        return Long.compare(sliceByteOffset, other.sliceByteOffset);
    };

    public static boolean intersect(final CRAIEntry e0, final CRAIEntry e1) {
        // unmapped entries never intersect, even with themselves
        if (e0.referenceContext.isUnmappedUnplaced() || e1.referenceContext.isUnmappedUnplaced()) {
            return false;
        }

        if (e0.referenceContext != e1.referenceContext) {
            return false;
        }

        final int a0 = e0.alignmentStart;
        final int a1 = e1.alignmentStart;

        final int b0 = a0 + e0.alignmentSpan;
        final int b1 = a1 + e1.alignmentSpan;

        return Math.abs(a0 + b0 - a1 - b1) < (e0.alignmentSpan + e1.alignmentSpan);

    }

    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }

    public long getContainerStartByteOffset() {
        return containerStartByteOffset;
    }

    public int getSliceByteOffset() {
        return sliceByteOffset;
    }

    public int getSliceByteSize() {
        return sliceByteSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRAIEntry entry = (CRAIEntry) o;

        if (referenceContext != entry.referenceContext) return false;
        if (alignmentStart != entry.alignmentStart) return false;
        if (alignmentSpan != entry.alignmentSpan) return false;
        if (containerStartByteOffset != entry.containerStartByteOffset) return false;
        if (sliceByteOffset != entry.sliceByteOffset) return false;
        return sliceByteSize == entry.sliceByteSize;
    }

    @Override
    public int hashCode() {
        int result = referenceContext.hashCode();
        result = 31 * result + alignmentStart;
        result = 31 * result + alignmentSpan;
        result = 31 * result + (int) (containerStartByteOffset ^ (containerStartByteOffset >>> 32));
        result = 31 * result + sliceByteOffset;
        result = 31 * result + sliceByteSize;
        return result;
    }
}
