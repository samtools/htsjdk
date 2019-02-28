package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceContextType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

public class ContainerTest extends HtsjdkTest {
    @Test
    public static void singleRefSliceStateTest() {
        final Slice slice = new Slice(new ReferenceContext(5));
        slice.alignmentStart = 10;
        slice.alignmentSpan = 15;

        final Container container = Container.initializeFromSlices(Arrays.asList(slice));

        Assert.assertTrue(container.getReferenceContext().isMappedSingleRef());
        Assert.assertEquals(container.getReferenceContext().getType(), ReferenceContextType.SINGLE_REFERENCE_TYPE);
        Assert.assertEquals(container.getReferenceContext(), slice.getReferenceContext());
        Assert.assertEquals(container.alignmentStart, slice.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice.alignmentSpan);
    }

    @Test
    public static void singleRefSliceMultipleStateTest() {
        final Slice slice1 = new Slice(new ReferenceContext(5));
        slice1.alignmentStart = 10;
        slice1.alignmentSpan = 15;

        final Slice slice2 = new Slice(slice1.getReferenceContext());
        slice2.alignmentStart = 20;
        slice2.alignmentSpan = 20;

        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2));

        Assert.assertTrue(container.getReferenceContext().isMappedSingleRef());
        Assert.assertEquals(container.getReferenceContext().getType(), ReferenceContextType.SINGLE_REFERENCE_TYPE);
        Assert.assertEquals(container.getReferenceContext(), slice1.getReferenceContext());
        Assert.assertEquals(container.alignmentStart, slice1.alignmentStart);
        Assert.assertEquals(container.alignmentSpan, slice2.alignmentStart + slice2.alignmentSpan - slice1.alignmentStart);      // 20 + 20 - 10
    }

    @Test
    public static void multiRefSliceStateTest() {
        final Slice slice1 = new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT);
        final Slice slice2 = new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT);

        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2));

        Assert.assertTrue(container.getReferenceContext().isMultiRef());
        Assert.assertEquals(container.getReferenceContext().getType(), ReferenceContextType.MULTIPLE_REFERENCE_TYPE);
        Assert.assertEquals(container.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(container.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test
    public static void unmappedSliceStateTest() {
        final Slice slice1 = new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
        final Slice slice2 = new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);

        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2));

        Assert.assertTrue(container.getReferenceContext().isUnmappedUnplaced());
        Assert.assertEquals(container.getReferenceContext().getType(), ReferenceContextType.UNMAPPED_UNPLACED_TYPE);
        Assert.assertEquals(container.alignmentStart, Slice.NO_ALIGNMENT_START);
        Assert.assertEquals(container.alignmentSpan, Slice.NO_ALIGNMENT_SPAN);
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void differentReferencesStateTest() {
        final Slice one = new Slice(new ReferenceContext(5));
        final Slice another = new Slice(new ReferenceContext(2));

        Container.initializeFromSlices(Arrays.asList(one, another));
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void singleAndUnmappedStateTest() {
        final Slice single = new Slice(new ReferenceContext(5));
        final Slice unmapped = new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);

        Container.initializeFromSlices(Arrays.asList(single, unmapped));
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void multiAndSingleStateTest() {
        final Slice multi = new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT);
        final Slice single = new Slice(new ReferenceContext(5));

        Container.initializeFromSlices(Arrays.asList(multi, single));
    }

    @Test(expectedExceptions = CRAMException.class)
    public static void multiAndUnmappedStateTest() {
        final Slice multi = new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT);
        final Slice unmapped = new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);

        Container.initializeFromSlices(Arrays.asList(multi, unmapped));
    }
}
