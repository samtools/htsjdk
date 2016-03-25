package htsjdk.samtools.cram;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A class representing CRAI index entry: file and alignment offsets for each slice.
 * Created by vadim on 10/08/2015.
 */
public class CRAIEntry implements Comparable<CRAIEntry>, Cloneable {
    public int sequenceId;
    public int alignmentStart;
    public int alignmentSpan;
    public long containerStartOffset;
    public int sliceOffset;
    public int sliceSize;
    public int sliceIndex;

    private static int CRAI_INDEX_COLUMNS = 6;
    private static String entryFormat = "%d\t%d\t%d\t%d\t%d\t%d";

    public CRAIEntry() {
    }

    /**
     * Create a CRAI Entry from a serialized CRAI index line.
     *
     * @param line string formatted as a CRAI index entry
     * @throws CRAIIndex.CRAIIndexException
     */
    public CRAIEntry(final String line) throws CRAIIndex.CRAIIndexException {
        final String[] chunks = line.split("\t");
        if (chunks.length != CRAI_INDEX_COLUMNS) {
            throw new CRAIIndex.CRAIIndexException(
                    "Malformed CRAI index entry: expecting " + CRAI_INDEX_COLUMNS + " columns but got " + chunks.length);
        }

        try {
            sequenceId = Integer.parseInt(chunks[0]);
            alignmentStart = Integer.parseInt(chunks[1]);
            alignmentSpan = Integer.parseInt(chunks[2]);
            containerStartOffset = Long.parseLong(chunks[3]);
            sliceOffset = Integer.parseInt(chunks[4]);
            sliceSize = Integer.parseInt(chunks[5]);
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
        return String.format(entryFormat,
                sequenceId, alignmentStart, alignmentSpan,
                containerStartOffset, sliceOffset, sliceSize);
    }

    @Override
    public String toString() { return serializeToString(); }

    public static List<CRAIEntry> fromContainer(final Container container) {
        final List<CRAIEntry> entries = new ArrayList<>(container.slices.length);
        for (int i = 0; i < container.slices.length; i++) {
            final Slice s = container.slices[i];
            final CRAIEntry e = new CRAIEntry();
            e.sequenceId = s.sequenceId;
            e.alignmentStart = s.alignmentStart;
            e.alignmentSpan = s.alignmentSpan;
            e.containerStartOffset = s.containerOffset;
            e.sliceOffset = container.landmarks[i];
            e.sliceSize = s.size;

            e.sliceIndex = i;
            entries.add(e);
        }
        return entries;
    }

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

        return (int) (containerStartOffset - o.containerStartOffset);
    }

    @Override
    public CRAIEntry clone() throws CloneNotSupportedException {
        super.clone();
        final CRAIEntry entry = new CRAIEntry();
        entry.sequenceId = sequenceId;
        entry.alignmentStart = alignmentStart;
        entry.alignmentSpan = alignmentSpan;
        entry.containerStartOffset = containerStartOffset;
        entry.sliceOffset = sliceOffset;
        entry.sliceSize = sliceSize;
        return entry;
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

            return (int) (o1.containerStartOffset - o2.containerStartOffset);
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

            return (int) (o1.containerStartOffset - o2.containerStartOffset);
        }
    };

    public static Comparator<CRAIEntry> byStartDesc = new Comparator<CRAIEntry>() {

        @Override
        public int compare(CRAIEntry o1, CRAIEntry o2) {
            if (o1.sequenceId != o2.sequenceId) {
                if (o1.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
                    return 1;
                if (o2.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
                    return -1;
                return -o2.sequenceId + o1.sequenceId;
            }
            if (o1.alignmentStart != o2.alignmentStart)
                return o1.alignmentStart - o2.alignmentStart;

            return (int) (o1.containerStartOffset - o2.containerStartOffset);
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
}
