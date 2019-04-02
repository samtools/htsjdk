package htsjdk.samtools.cram;

import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * A class representing CRAI index entry: file and alignment offsets for each slice.
 * Created by vadim on 10/08/2015.
 */
public class CRAIEntry implements Comparable<CRAIEntry> {
    private final AlignmentContext alignmentContext;

    // this Slice's Container's offset in bytes from the beginning of the stream
    // equal to Slice.containerByteOffset and Container.byteOffset
    private final long containerStartByteOffset;
    // this Slice's offset in bytes from the beginning of its Container's Compression Header
    // equal to Slice.byteOffsetFromCompressionHeaderStart and Container.landmarks[Slice.index]
    private final int sliceByteOffsetFromCompressionHeaderStart;
    private final int sliceByteSize;

    private static final int CRAI_INDEX_COLUMNS = 6;
    private static final String ENTRY_FORMAT = "%d\t%d\t%d\t%d\t%d\t%d";

    public CRAIEntry(final AlignmentContext alignmentContext,
                     final long containerStartByteOffset,
                     final int sliceByteOffsetFromCompressionHeaderStart,
                     final int sliceByteSize) {
        if (alignmentContext.getReferenceContext().isMultipleReference()) {
            throw new CRAMException("Cannot directly index a multiref slice.  Index by its constituent references instead.");
        }

        this.alignmentContext = alignmentContext;
        this.containerStartByteOffset = containerStartByteOffset;
        this.sliceByteOffsetFromCompressionHeaderStart = sliceByteOffsetFromCompressionHeaderStart;
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
            alignmentContext = new AlignmentContext(
                    new ReferenceContext(Integer.parseInt(chunks[0])),
                    Integer.parseInt(chunks[1]),
                    Integer.parseInt(chunks[2]));
            containerStartByteOffset = Long.parseLong(chunks[3]);
            sliceByteOffsetFromCompressionHeaderStart = Integer.parseInt(chunks[4]);
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
        final int refSeqId = alignmentContext.getReferenceContext().getSerializableId();
        final int alignmentStart = alignmentContext.getAlignmentStart();
        final int alignmentSpan = alignmentContext.getAlignmentSpan();

        return String.format(ENTRY_FORMAT,
                refSeqId, alignmentStart, alignmentSpan,
                containerStartByteOffset, sliceByteOffsetFromCompressionHeaderStart, sliceByteSize);
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
        int contextComparison = alignmentContext.compareTo(other.alignmentContext);
        if (contextComparison != 0) {
            return contextComparison;
        }

        if (containerStartByteOffset != other.containerStartByteOffset) {
            return Long.compare(containerStartByteOffset, other.containerStartByteOffset);
        }

        return Long.compare(sliceByteOffsetFromCompressionHeaderStart, other.sliceByteOffsetFromCompressionHeaderStart);
    };

    public static boolean intersect(final CRAIEntry e0, final CRAIEntry e1) {
        final AlignmentContext alignment0 = e0.alignmentContext;
        final AlignmentContext alignment1 = e1.alignmentContext;

        final ReferenceContext refContext0 = alignment0.getReferenceContext();
        final ReferenceContext refContext1 = alignment1.getReferenceContext();

        // unmapped entries never intersect, even with themselves
        if (refContext0.isUnmappedUnplaced() || refContext1.isUnmappedUnplaced()) {
            return false;
        }

        if (refContext0 != refContext1) {
            return false;
        }

        final int a0 = alignment0.getAlignmentStart();
        final int a1 = alignment1.getAlignmentStart();

        final int b0 = a0 + alignment0.getAlignmentSpan();
        final int b1 = a1 + alignment1.getAlignmentSpan();

        return Math.abs(a0 + b0 - a1 - b1) < (alignment0.getAlignmentSpan() + alignment1.getAlignmentSpan());

    }

    public AlignmentContext getAlignmentContext() {
        return alignmentContext;
    }

    public ReferenceContext getReferenceContext() {
        return alignmentContext.getReferenceContext();
    }

    public int getAlignmentStart() {
        return alignmentContext.getAlignmentStart();
    }

    public int getAlignmentSpan() {
        return alignmentContext.getAlignmentSpan();
    }

    public long getContainerStartByteOffset() {
        return containerStartByteOffset;
    }

    public int getSliceByteOffsetFromCompressionHeaderStart() {
        return sliceByteOffsetFromCompressionHeaderStart;
    }

    public int getSliceByteSize() {
        return sliceByteSize;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        CRAIEntry otherEntry = (CRAIEntry) other;
        return containerStartByteOffset == otherEntry.containerStartByteOffset &&
                sliceByteOffsetFromCompressionHeaderStart == otherEntry.sliceByteOffsetFromCompressionHeaderStart &&
                sliceByteSize == otherEntry.sliceByteSize &&
                alignmentContext.equals(otherEntry.alignmentContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alignmentContext, containerStartByteOffset, sliceByteOffsetFromCompressionHeaderStart, sliceByteSize);
    }
}
