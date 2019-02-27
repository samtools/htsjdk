package htsjdk.samtools.cram;

import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * A class representing CRAI index entry: file and alignment offsets for each slice.
 * Created by vadim on 10/08/2015.
 */
public class CRAIEntry implements Comparable<CRAIEntry> {
    private final int sequenceId;
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

    public CRAIEntry(final int sequenceId,
                     final int alignmentStart,
                     final int alignmentSpan,
                     final long containerStartByteOffset,
                     final int sliceByteOffset,
                     final int sliceByteSize) {
        this.sequenceId = sequenceId;
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
            sequenceId = Integer.parseInt(chunks[0]);
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
                sequenceId, alignmentStart, alignmentSpan,
                containerStartByteOffset, sliceByteOffset, sliceByteSize);
    }

    @Override
    public String toString() { return serializeToString(); }

    @Override
    public int compareTo(final CRAIEntry o) {
        if (o == null) {
            return 1;
        }
        if (sequenceId != o.sequenceId) {
            return sequenceId - o.sequenceId;
        }
        if (alignmentStart != o.alignmentStart) {
            return alignmentStart - o.alignmentStart;
        }

        return (int) (containerStartByteOffset - o.containerStartByteOffset);
    }

    public static Comparator<CRAIEntry> byEnd = new Comparator<CRAIEntry>() {

        @Override
        public int compare(final CRAIEntry o1, final CRAIEntry o2) {
            if (o1.sequenceId != o2.sequenceId) {
                return o2.sequenceId - o1.sequenceId;
            }
            if (o1.alignmentStart + o1.alignmentSpan != o2.alignmentStart + o2.alignmentSpan) {
                return o1.alignmentStart + o1.alignmentSpan - o2.alignmentStart - o2.alignmentSpan;
            }

            return (int) (o1.containerStartByteOffset - o2.containerStartByteOffset);
        }
    };

    public static final Comparator<CRAIEntry> byStart = new Comparator<CRAIEntry>() {

        @Override
        public int compare(final CRAIEntry o1, final CRAIEntry o2) {
            if (o1.sequenceId != o2.sequenceId) {
                return o2.sequenceId - o1.sequenceId;
            }
            if (o1.alignmentStart != o2.alignmentStart) {
                return o1.alignmentStart - o2.alignmentStart;
            }

            return (int) (o1.containerStartByteOffset - o2.containerStartByteOffset);
        }
    };

    public static final Comparator<CRAIEntry> byStartDesc = new Comparator<CRAIEntry>() {

        @Override
        public int compare(CRAIEntry o1, CRAIEntry o2) {
            if (o1.sequenceId != o2.sequenceId) {
                if (o1.sequenceId == ReferenceContext.REF_ID_UNMAPPED)
                    return 1;
                if (o2.sequenceId == ReferenceContext.REF_ID_UNMAPPED)
                    return -1;
                return -o2.sequenceId + o1.sequenceId;
            }
            if (o1.alignmentStart != o2.alignmentStart)
                return o1.alignmentStart - o2.alignmentStart;

            return (int) (o1.containerStartByteOffset - o2.containerStartByteOffset);
        }
    };

    public static boolean intersect(final CRAIEntry e0, final CRAIEntry e1) {
        if (e0.sequenceId != e1.sequenceId) {
            return false;
        }
        if (e0.sequenceId < 0) {
            return false;
        }

        final int a0 = e0.alignmentStart;
        final int a1 = e1.alignmentStart;

        final int b0 = a0 + e0.alignmentSpan;
        final int b1 = a1 + e1.alignmentSpan;

        return Math.abs(a0 + b0 - a1 - b1) < (e0.alignmentSpan + e1.alignmentSpan);

    }

    public int getSequenceId() {
        return sequenceId;
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
}
