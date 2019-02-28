package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ReferenceContextTest extends HtsjdkTest {

    // test constructors and basic identity methods
    
    @Test
    public void basicTest() {
        final ReferenceContext refContext0 = new ReferenceContext(0);
        Assert.assertTrue(refContext0.isMappedSingleRef());
        Assert.assertFalse(refContext0.isUnmappedUnplaced());
        Assert.assertFalse(refContext0.isMultiRef());
        Assert.assertEquals(refContext0.getType(), ReferenceContextType.SINGLE_REFERENCE_TYPE);
        Assert.assertEquals(refContext0.getSequenceId(), 0);
        Assert.assertEquals(refContext0.getSerializableId(), 0);

        final ReferenceContext refContext1 = new ReferenceContext(1);
        Assert.assertTrue(refContext1.isMappedSingleRef());
        Assert.assertFalse(refContext1.isUnmappedUnplaced());
        Assert.assertFalse(refContext1.isMultiRef());
        Assert.assertEquals(refContext1.getType(), ReferenceContextType.SINGLE_REFERENCE_TYPE);
        Assert.assertEquals(refContext1.getSequenceId(), 1);
        Assert.assertEquals(refContext1.getSerializableId(), 1);

        final ReferenceContext unmapped = new ReferenceContext(ReferenceContext.UNMAPPED_UNPLACED_ID);
        final ReferenceContext unmappedStatic = ReferenceContext.UNMAPPED_UNPLACED_CONTEXT;
        Assert.assertEquals(unmappedStatic, unmapped);

        Assert.assertFalse(unmapped.isMappedSingleRef());
        Assert.assertTrue(unmapped.isUnmappedUnplaced());
        Assert.assertFalse(unmapped.isMultiRef());
        Assert.assertEquals(unmapped.getType(), ReferenceContextType.UNMAPPED_UNPLACED_TYPE);
        Assert.assertEquals(unmapped.getSerializableId(), ReferenceContext.UNMAPPED_UNPLACED_ID);

        final ReferenceContext multi = new ReferenceContext(ReferenceContext.MULTIPLE_REFERENCE_ID);
        final ReferenceContext multiStatic = ReferenceContext.MULTIPLE_REFERENCE_CONTEXT;
        Assert.assertEquals(multiStatic, multi);

        Assert.assertFalse(multi.isMappedSingleRef());
        Assert.assertFalse(multi.isUnmappedUnplaced());
        Assert.assertTrue(multi.isMultiRef());
        Assert.assertEquals(multi.getType(), ReferenceContextType.MULTIPLE_REFERENCE_TYPE);
        Assert.assertEquals(multi.getSerializableId(), ReferenceContext.MULTIPLE_REFERENCE_ID);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testInvalidSetSequenceId() {
        new ReferenceContext(-3);
    }

    @DataProvider(name = "sentinels")
    private static Object[][] sentinels() {
        return new Object[][] {
                {ReferenceContext.MULTIPLE_REFERENCE_ID},
                {ReferenceContext.UNMAPPED_UNPLACED_ID}
        };
    }

    @Test(dataProvider = "sentinels")
    public void testSentinelsGetSerializableId(final int seqId) {
        final ReferenceContext context = new ReferenceContext(seqId);
        Assert.assertEquals(context.getSerializableId(), seqId);
    }

    @Test(dataProvider = "sentinels", expectedExceptions = CRAMException.class)
    public void testSentinelsGetSequenceId(final int seqId) {
        final ReferenceContext context = new ReferenceContext(seqId);
        context.getSequenceId();
    }
}
