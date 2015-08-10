package htsjdk.samtools.cram;

import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.Slice;

import java.util.Comparator;

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

    public CRAIEntry() {
    }

    public CRAIEntry(Container c) {
        for (int i = 0; i < c.slices.length; i++) {
            Slice s = c.slices[i];
            CRAIEntry e = new CRAIEntry();
            e.sequenceId = c.sequenceId;
            e.alignmentStart = s.alignmentStart;
            e.alignmentSpan = s.alignmentSpan;
            e.containerStartOffset = c.offset;
            e.sliceOffset = c.landmarks[i];
            e.sliceSize = s.size;

            e.sliceIndex = i;
        }
    }

    public CRAIEntry(String line) {
        String[] chunks = line.split("\t");
        if (chunks.length != 6)
            throw new RuntimeException("Invalid index format.");

        sequenceId = Integer.valueOf(chunks[0]);
        alignmentStart = Integer.valueOf(chunks[1]);
        alignmentSpan = Integer.valueOf(chunks[2]);
        containerStartOffset = Long.valueOf(chunks[3]);
        sliceOffset = Integer.valueOf(chunks[4]);
        sliceSize = Integer.valueOf(chunks[5]);
    }

    @Override
    public String toString() {
        return String.format("%d\t%d\t%d\t%d\t%d\t%d", sequenceId, alignmentStart, alignmentSpan,
                containerStartOffset, sliceOffset, sliceSize);
    }

    @Override
    public int compareTo(CRAIEntry o) {
        if (sequenceId != o.sequenceId)
            return o.sequenceId - sequenceId;
        if (alignmentStart != o.alignmentStart)
            return alignmentStart - o.alignmentStart;

        return (int) (containerStartOffset - o.containerStartOffset);
    }

    @Override
    public CRAIEntry clone() throws CloneNotSupportedException {
        CRAIEntry entry = new CRAIEntry();
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
        public int compare(CRAIEntry o1, CRAIEntry o2) {
            if (o1.sequenceId != o2.sequenceId)
                return o2.sequenceId - o1.sequenceId;
            if (o1.alignmentStart + o1.alignmentSpan != o2.alignmentStart + o2.alignmentSpan)
                return o1.alignmentStart + o1.alignmentSpan - o2.alignmentStart - o2.alignmentSpan;

            return (int) (o1.containerStartOffset - o2.containerStartOffset);
        }
    };

    public static Comparator<CRAIEntry> byStart = new Comparator<CRAIEntry>() {

        @Override
        public int compare(CRAIEntry o1, CRAIEntry o2) {
            if (o1.sequenceId != o2.sequenceId)
                return o2.sequenceId - o1.sequenceId;
            if (o1.alignmentStart != o2.alignmentStart)
                return o1.alignmentStart - o2.alignmentStart;

            return (int) (o1.containerStartOffset - o2.containerStartOffset);
        }
    };


    public static boolean intersect(CRAIEntry e0, CRAIEntry e1) {
        if (e0.sequenceId != e1.sequenceId)
            return false;
        if (e0.sequenceId < 0)
            return false;

        int a0 = e0.alignmentStart;
        int a1 = e1.alignmentStart;

        int b0 = a0 + e0.alignmentSpan;
        int b1 = a1 + e1.alignmentSpan;

        boolean result = Math.abs(a0 + b0 - a1 - b1) < (e0.alignmentSpan + e1.alignmentSpan);
        return result;

    }
}
