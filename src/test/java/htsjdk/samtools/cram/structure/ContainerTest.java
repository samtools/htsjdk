package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ContainerTest extends HtsjdkTest {

    @Test
    public static void singleRefSliceStateTest() {
        final Slice slice = new Slice();
        slice.sequenceId = 5;
        slice.alignmentStart = 10;
        slice.alignmentSpan = 15;

        final Container container = new Container();
        container.finalizeContainerState(slice);

        Assert.assertEquals(container.sequenceId, slice.sequenceId);
        Assert.assertEquals(container.alignmentStart, slice.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice.alignmentSpan);
    }

    @Test
    public static void singleRefSliceMultipleStateTest() {
        final Slice slice1 = new Slice();
        slice1.sequenceId = 5;
        slice1.alignmentStart = 10;
        slice1.alignmentSpan = 15;

        final Slice slice2 = new Slice();
        slice2.sequenceId = slice1.sequenceId;
        slice2.alignmentStart = 20;
        slice2.alignmentSpan = 20;

        final Container container = new Container();
        container.finalizeContainerState(slice1, slice2);

        Assert.assertEquals(container.sequenceId, slice1.sequenceId);
        Assert.assertEquals(container.alignmentStart, 10);
        Assert.assertEquals(container.alignmentSpan, 30);      // 20 + 20 - 10
    }

    @Test
    public static void multiRefSliceStateTest() {
        final Slice slice1 = new Slice();
        slice1.sequenceId = Slice.MULTI_REFERENCE;
        slice1.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice1.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice slice2 = new Slice();
        slice2.sequenceId = Slice.MULTI_REFERENCE;
        slice2.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice2.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.finalizeContainerState(slice1, slice2);

        Assert.assertEquals(container.sequenceId, slice1.sequenceId);
        Assert.assertEquals(container.alignmentStart, slice1.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice1.alignmentSpan);
    }

    @Test
    public static void unmappedSliceStateTest() {
        final Slice slice1 = new Slice();
        slice1.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        slice1.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice1.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice slice2 = new Slice();
        slice2.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        slice2.alignmentStart = Slice.NO_ALIGNMENT_START;
        slice2.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.finalizeContainerState(slice1, slice2);

        Assert.assertEquals(container.sequenceId, slice1.sequenceId);
        Assert.assertEquals(container.alignmentStart, slice1.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice1.alignmentSpan);
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void differentReferencesStateTest() {
        final Slice one = new Slice();
        one.sequenceId = 5;
        one.alignmentStart = 10;
        one.alignmentSpan = 15;

        final Slice another = new Slice();
        another.sequenceId = 2;
        another.alignmentStart = 1;
        another.alignmentSpan = 10;

        final Container container = new Container();
        container.finalizeContainerState(one, another);
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void singleAndUnmappedStateTest() {
        final Slice single = new Slice();
        single.sequenceId = 5;
        single.alignmentStart = 10;
        single.alignmentSpan = 15;

        final Slice unmapped = new Slice();
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = Slice.NO_ALIGNMENT_START;
        unmapped.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.finalizeContainerState(single, unmapped);
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void multiAndSingleStateTest() {
        final Slice multi = new Slice();
        multi.sequenceId = Slice.MULTI_REFERENCE;
        multi.alignmentStart = Slice.NO_ALIGNMENT_START;
        multi.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice single = new Slice();
        single.sequenceId = 5;
        single.alignmentStart = 10;
        single.alignmentSpan = 15;

        final Container container = new Container();
        container.finalizeContainerState(multi, single);
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void multiAndUnmappedStateTest() {
        final Slice multi = new Slice();
        multi.sequenceId = Slice.MULTI_REFERENCE;
        multi.alignmentStart = Slice.NO_ALIGNMENT_START;
        multi.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Slice unmapped = new Slice();
        unmapped.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        unmapped.alignmentStart = Slice.NO_ALIGNMENT_START;
        unmapped.alignmentSpan = Slice.NO_ALIGNMENT_SPAN;

        final Container container = new Container();
        container.finalizeContainerState(multi, unmapped);
    }
}
