package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.Slice;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by vadim on 25/08/2015.
 */
public class CRAIEntryTest extends HtsjdkTest {
    @Test
    public void testFromContainer() {
        final Container container = new Container();
        final Slice slice = new Slice();
        slice.sequenceId = 1;
        slice.alignmentStart = 2;
        slice.alignmentSpan = 3;
        slice.containerOffset = 4;
        slice.offset = 5;
        slice.size = 6;
        container.landmarks = new int[]{7};
        container.slices = new Slice[]{slice};

        final List<CRAIEntry> entries = container.getCraiEntries();
        Assert.assertNotNull(entries);
        Assert.assertEquals(entries.size(), 1);
        final CRAIEntry entry = entries.get(0);

        Assert.assertEquals(entry.getSequenceId(), slice.sequenceId);
        Assert.assertEquals(entry.getAlignmentStart(), slice.alignmentStart);
        Assert.assertEquals(entry.getAlignmentSpan(), slice.alignmentSpan);
        Assert.assertEquals(entry.getContainerStartByteOffset(), slice.containerOffset);
        Assert.assertEquals(entry.getSliceByteOffset(), slice.offset);
        Assert.assertEquals(entry.getSliceByteSize(), slice.size);
    }

    @Test
    public void testFromCraiLine() {
        int counter = 1;
        final int sequenceId = counter++;
        final int alignmentStart = counter++;
        final int alignmentSpan = counter++;
        final int containerOffset = Integer.MAX_VALUE + counter++;
        final int sliceOffset = counter++;
        final int sliceSize = counter++;

        final String line = String.format("%d\t%d\t%d\t%d\t%d\t%d", sequenceId, alignmentStart, alignmentSpan, containerOffset, sliceOffset, sliceSize);
        final CRAIEntry entry = new CRAIEntry(line);
        Assert.assertNotNull(entry);
        Assert.assertEquals(entry.getSequenceId(), sequenceId);
        Assert.assertEquals(entry.getAlignmentStart(), alignmentStart);
        Assert.assertEquals(entry.getAlignmentSpan(), alignmentSpan);
        Assert.assertEquals(entry.getContainerStartByteOffset(), containerOffset);
        Assert.assertEquals(entry.getSliceByteOffset(), sliceOffset);
        Assert.assertEquals(entry.getSliceByteSize(), sliceSize);
    }

    @Test
    public void testIntersectsZeroSpan() {
        Assert.assertFalse(CRAIEntry.intersect(newEntry(1, 1, 1), newEntry(1, 1, 0)));
    }

    @Test
    public void testIntersectsSame() {
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1, 1), newEntry(1, 1, 1)));
    }

    @Test
    public void testIntersectsIncluded() {
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1, 2), newEntry(1, 1, 1)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1, 2), newEntry(1, 2, 1)));

        // is symmetrical?
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1, 1), newEntry(1, 1, 2)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 2, 1), newEntry(1, 1, 2)));
    }

    @Test
    public void testIntersectsOvertlaping() {
        Assert.assertFalse(CRAIEntry.intersect(newEntry(1, 1, 2), newEntry(1, 0, 1)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1, 2), newEntry(1, 0, 2)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1, 2), newEntry(1, 2, 1)));
        Assert.assertFalse(CRAIEntry.intersect(newEntry(1, 1, 2), newEntry(1, 3, 1)));
    }

    @Test
    public void testIntersectsAnotherSequence() {
        Assert.assertTrue(CRAIEntry.intersect(newEntry(10, 1, 2), newEntry(10, 2, 1)));
        Assert.assertFalse(CRAIEntry.intersect(newEntry(10, 1, 2), newEntry(11, 2, 1)));
    }

    @Test
    public void testCompareTo () {
        final List<CRAIEntry> list = new ArrayList<>(2);
        CRAIEntry e1;
        CRAIEntry e2;

        e1 = newEntry(100, 0, 0);
        e2 = newEntry(200, 0, 0);
        list.add(e2);
        list.add(e1);
        Assert.assertTrue(list.get(1).getSequenceId() < list.get(0).getSequenceId());
        Collections.sort(list);
        Assert.assertTrue(list.get(0).getSequenceId() < list.get(1).getSequenceId());

        list.clear();
        e1 = newEntry(1, 100, 0);
        e2 = newEntry(1, 200, 0);
        list.add(e2);
        list.add(e1);
        Assert.assertTrue(list.get(1).getAlignmentStart() < list.get(0).getAlignmentStart());
        Collections.sort(list);
        Assert.assertTrue(list.get(0).getAlignmentStart() < list.get(1).getAlignmentStart());

        list.clear();
        e1 = newEntryContOffset(100);
        e2 = newEntryContOffset(200);
        list.add(e2);
        list.add(e1);
        Assert.assertTrue(list.get(1).getContainerStartByteOffset() < list.get(0).getContainerStartByteOffset());
        Collections.sort(list);
        Assert.assertTrue(list.get(0).getContainerStartByteOffset() < list.get(1).getContainerStartByteOffset());
    }

    public static CRAIEntry newEntry(final int seqId, final int start, final int span) {
        return newEntry(seqId, start, span, 0, 0, 0);
    }

    public static CRAIEntry newEntry(final int sequenceId,
                                     final int start,
                                     final int span,
                                     final int containerStartOffset,
                                     final int sliceOffset,
                                     final int sliceSize) {
        return new CRAIEntry(sequenceId, start, span, containerStartOffset, sliceOffset, sliceSize);
    }

    public static CRAIEntry newEntrySeqStart(final int seqId, final int start) {
        return newEntry(seqId, start, 0);
    }

    public static  CRAIEntry newEntryContOffset(final int containerStartOffset) {
        return newEntry(1, 0, 0, containerStartOffset, 0, 0);
    }

    public static CRAIEntry updateStart(final CRAIEntry toClone, final int alignmentStart) {
        return newEntry(toClone.getSequenceId(),
                alignmentStart,
                toClone.getAlignmentSpan());
    }

    public static CRAIEntry updateStartContOffset(final CRAIEntry toClone,
                                                  final int alignmentStart,
                                                  final int containerStartOffset) {
        return newEntry(toClone.getSequenceId(),
                alignmentStart,
                toClone.getAlignmentSpan(),
                containerStartOffset,
                toClone.getSliceByteOffset(),
                toClone.getSliceByteSize());
    }
}
