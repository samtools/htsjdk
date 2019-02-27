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
        Assert.assertEquals(refContext0.getType(), ReferenceContextType.SINGLE_REFERENCE);
        Assert.assertEquals(refContext0.getSequenceId(), 0);
        Assert.assertEquals(refContext0.getSerializableId(), 0);

        final ReferenceContext refContext1 = new ReferenceContext(1);
        Assert.assertTrue(refContext1.isMappedSingleRef());
        Assert.assertFalse(refContext1.isUnmappedUnplaced());
        Assert.assertFalse(refContext1.isMultiRef());
        Assert.assertEquals(refContext1.getType(), ReferenceContextType.SINGLE_REFERENCE);
        Assert.assertEquals(refContext1.getSequenceId(), 1);
        Assert.assertEquals(refContext1.getSerializableId(), 1);

        final ReferenceContext unmapped = new ReferenceContext(ReferenceContext.REF_ID_UNMAPPED);
        final ReferenceContext unmappedStatic = ReferenceContext.UNMAPPED;
        Assert.assertEquals(unmappedStatic, unmapped);

        Assert.assertFalse(unmapped.isMappedSingleRef());
        Assert.assertTrue(unmapped.isUnmappedUnplaced());
        Assert.assertFalse(unmapped.isMultiRef());
        Assert.assertEquals(unmapped.getType(), ReferenceContextType.UNMAPPED_UNPLACED);
        Assert.assertEquals(unmapped.getSerializableId(), ReferenceContext.REF_ID_UNMAPPED);

        final ReferenceContext multi = new ReferenceContext(ReferenceContext.REF_ID_MULTIPLE);
        final ReferenceContext multiStatic = ReferenceContext.MULTIPLE;
        Assert.assertEquals(multiStatic, multi);

        Assert.assertFalse(multi.isMappedSingleRef());
        Assert.assertFalse(multi.isUnmappedUnplaced());
        Assert.assertTrue(multi.isMultiRef());
        Assert.assertEquals(multi.getType(), ReferenceContextType.MULTI_REFERENCE);
        Assert.assertEquals(multi.getSerializableId(), ReferenceContext.REF_ID_MULTIPLE);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testInvalidSetSequenceId() {
        new ReferenceContext(-3);
    }

    @DataProvider(name = "sentinels")
    private static Object[][] invalidGet() {
        return new Object[][] {
                {-2},
                {-1}
        };
    }

    @Test(dataProvider = "sentinels", expectedExceptions = CRAMException.class)
    public void testInvalidGetSequenceId(final int seqId) {
        final ReferenceContext context = new ReferenceContext(seqId);
        // works
        Assert.assertEquals(context.getSerializableId(), seqId);
        // throws
        context.getSequenceId();
    }
}
