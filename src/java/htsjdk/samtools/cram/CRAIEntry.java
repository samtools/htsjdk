package htsjdk.samtools.cram;

import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.Slice;

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

    public CRAIEntry() {
    }

    public static List<CRAIEntry> fromContainer(final Container container) {
        final List<CRAIEntry> entries = new ArrayList<CRAIEntry>(container.slices.length);
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

    public static CRAIEntry fromCraiLine(final String line) {
        final String[] chunks = line.split("\t");
        if (chunks.length != 6) {
            throw new CRAIIndex.CRAIIndexException("Expecting 6 columns but got " + chunks.length);
        }

        try {
            final CRAIEntry e = new CRAIEntry();
            e.sequenceId = Integer.valueOf(chunks[0]);
            e.alignmentStart = Integer.valueOf(chunks[1]);
            e.alignmentSpan = Integer.valueOf(chunks[2]);
            e.containerStartOffset = Integer.valueOf(chunks[3]);
            e.sliceOffset = Integer.valueOf(chunks[4]);
            e.sliceSize = Integer.valueOf(chunks[5]);
            return e;
        } catch (final NumberFormatException e) {
            throw new CRAIIndex.CRAIIndexException(e);
        }
    }

    public CRAIEntry(final String line) throws CRAIIndex.CRAIIndexException {
        final String[] chunks = line.split("\t");
        if (chunks.length != 6) {
            throw new CRAIIndex.CRAIIndexException("Expecting 6 columns but got " + chunks.length);
        }

        try {
            sequenceId = Integer.valueOf(chunks[0]);
            alignmentStart = Integer.valueOf(chunks[1]);
            alignmentSpan = Integer.valueOf(chunks[2]);
            containerStartOffset = Integer.valueOf(chunks[3]);
            sliceOffset = Integer.valueOf(chunks[4]);
            sliceSize = Integer.valueOf(chunks[5]);
        } catch (final NumberFormatException e) {
            throw new CRAIIndex.CRAIIndexException(e);
        }
    }

    @Override
    public String toString() {
        return String.format("%d\t%d\t%d\t%d\t%d\t%d", sequenceId, alignmentStart, alignmentSpan,
                containerStartOffset, sliceOffset, sliceSize);
    }

    @Override
    public int compareTo(final CRAIEntry o) {
        if (o == null) {
            return 1;
        }
        if (sequenceId != o.sequenceId) {
            return o.sequenceId - sequenceId;
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
