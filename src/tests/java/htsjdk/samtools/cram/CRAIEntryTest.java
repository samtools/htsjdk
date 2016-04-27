package htsjdk.samtools.cram;

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
public class CRAIEntryTest {

    @Test
    public void testFromContainer() {
        final Container container = new Container();
        final Slice slice = new Slice();
        slice.sequenceId = 1;
        slice.alignmentStart = 2;
        slice.alignmentSpan = 3;
        slice.containerOffset = 4;
        container.landmarks = new int[]{5};
        container.slices = new Slice[]{slice};

        final List<CRAIEntry> entries = CRAIEntry.fromContainer(container);
        Assert.assertNotNull(entries);
        Assert.assertEquals(entries.size(), 1);
        final CRAIEntry entry = entries.get(0);

        Assert.assertEquals(entry.sequenceId, slice.sequenceId);
        Assert.assertEquals(entry.alignmentStart, slice.alignmentStart);
        Assert.assertEquals(entry.alignmentSpan, slice.alignmentSpan);
        Assert.assertEquals(entry.containerStartOffset, slice.containerOffset);
    }

    @Test
    public void testFromCraiLine() {
        int counter = 1;
        final int sequenceId = counter++;
        final int alignmentStart = counter++;
        final int alignmentSpan = counter++;
        final int containerOffset = Integer.MAX_VALUE + counter++;
        final int sliceOffset = counter++;
        final int sliceSise = counter++;

        final String line = String.format("%d\t%d\t%d\t%d\t%d\t%d", sequenceId, alignmentStart, alignmentSpan, containerOffset, sliceOffset, sliceSise);
        final CRAIEntry entry = new CRAIEntry(line);
        Assert.assertNotNull(entry);
        Assert.assertEquals(entry.sequenceId, sequenceId);
        Assert.assertEquals(entry.alignmentStart, alignmentStart);
        Assert.assertEquals(entry.alignmentSpan, alignmentSpan);
        Assert.assertEquals(entry.containerStartOffset, containerOffset);
    }

    @Test
    public void testIntersetcsZeroSpan() {
        Assert.assertFalse(CRAIEntry.intersect(newEntry(1, 1), newEntry(1, 0)));
    }

    @Test
    public void testIntersetcsSame() {
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1), newEntry(1, 1)));
    }

    @Test
    public void testIntersetcsIncluded() {
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 2), newEntry(1, 1)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 2), newEntry(2, 1)));

        // is symmetrical?
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 1), newEntry(1, 2)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(2, 1), newEntry(1, 2)));
    }

    @Test
    public void testIntersetcsOvertlaping() {
        Assert.assertFalse(CRAIEntry.intersect(newEntry(1, 2), newEntry(0, 1)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 2), newEntry(0, 2)));
        Assert.assertTrue(CRAIEntry.intersect(newEntry(1, 2), newEntry(2, 1)));
        Assert.assertFalse(CRAIEntry.intersect(newEntry(1, 2), newEntry(3, 1)));
    }

    @Test
    public void testIntersetcsAnotherSequence() {
        Assert.assertTrue(CRAIEntry.intersect(newEntry(10, 1, 2), newEntry(10, 2, 1)));
        Assert.assertFalse(CRAIEntry.intersect(newEntry(10, 1, 2), newEntry(11, 2, 1)));
    }

    @Test
    public void testCompareTo () {
        final List<CRAIEntry> list = new ArrayList<CRAIEntry>(2);
        CRAIEntry e1;
        CRAIEntry e2;

        e1 = new CRAIEntry();
        e1.sequenceId = 100;
        e2 = new CRAIEntry();
        e2.sequenceId = 200;
        list.add(e2);
        list.add(e1);
        Assert.assertTrue(list.get(1).sequenceId < list.get(0).sequenceId);
        Collections.sort(list);
        Assert.assertTrue(list.get(0).sequenceId < list.get(1).sequenceId);

        list.clear();
        e1 = new CRAIEntry();
        e1.alignmentStart = 100;
        e2 = new CRAIEntry();
        e2.alignmentStart = 200;
        list.add(e2);
        list.add(e1);
        Assert.assertTrue(list.get(1).alignmentStart < list.get(0).alignmentStart);
        Collections.sort(list);
        Assert.assertTrue(list.get(0).alignmentStart < list.get(1).alignmentStart);

        list.clear();
        e1 = new CRAIEntry();
        e1.containerStartOffset = 100;
        e2 = new CRAIEntry();
        e2.containerStartOffset = 200;
        list.add(e2);
        list.add(e1);
        Assert.assertTrue(list.get(1).containerStartOffset < list.get(0).containerStartOffset);
        Collections.sort(list);
        Assert.assertTrue(list.get(0).containerStartOffset < list.get(1).containerStartOffset);
    }

    private static CRAIEntry newEntry(final int start, final int span) {
        return newEntry(1, start, span);
    }

    private static CRAIEntry newEntry(final int seqId, final int start, final int span) {
        final CRAIEntry e1 = new CRAIEntry();
        e1.sequenceId = seqId;
        e1.alignmentStart = start;
        e1.alignmentSpan = span;
        return e1;
    }


}
