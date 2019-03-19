package htsjdk.samtools.cram.ref;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ReferenceContextTest extends HtsjdkTest {

    // for test convenience

    private static final int UNPLACED_REF_ID = ReferenceContext.UNMAPPED_UNPLACED_CONTEXT.getSerializableId();
    private static final int MULTI_REF_ID = ReferenceContext.MULTIPLE_REFERENCE_CONTEXT.getSerializableId();
    private static final int UNINIT_REF_ID = -3; // can't call getSerializableId() on it

    // test constructors and basic identity methods

    @DataProvider(name = "validRefContexts")
    private static Object[][] refContexts() {
        return new Object[][] {
                //context, expectedType, isSingle, isUnplaced, isMultiple, isUninit, serializableID
                {
                        new ReferenceContext(0),
                        ReferenceContextType.SINGLE_REFERENCE_TYPE,
                        true, false, false, false, 0
                },
                {
                        new ReferenceContext(1),
                        ReferenceContextType.SINGLE_REFERENCE_TYPE,
                        true, false, false, false, 1
                },
                {
                        new ReferenceContext(UNPLACED_REF_ID),
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        false, true, false, false, UNPLACED_REF_ID
                },
                {
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        ReferenceContextType.UNMAPPED_UNPLACED_TYPE,
                        false, true, false, false, UNPLACED_REF_ID
                },
                {
                        new ReferenceContext(MULTI_REF_ID),
                        ReferenceContextType.MULTIPLE_REFERENCE_TYPE,
                        false, false, true, false, MULTI_REF_ID
                },
                {
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                        ReferenceContextType.MULTIPLE_REFERENCE_TYPE,
                        false, false, true, false, MULTI_REF_ID
                },
                {
                        new ReferenceContext(UNINIT_REF_ID),
                        ReferenceContextType.UNINITIALIZED,
                        false, false, false, true, UNINIT_REF_ID
                },
                {
                        ReferenceContext.UNINITIALIZED_CONTEXT,
                        ReferenceContextType.UNINITIALIZED,
                        false, false, false, true, UNINIT_REF_ID
                },
        };
    }

    @Test(dataProvider = "validRefContexts")
    public void referenceContextTest(
            final ReferenceContext refContext,
            final ReferenceContextType expectedType,
            final boolean expectedSingle,
            final boolean expectedUnplaced,
            final boolean expectedMulti,
            final boolean expectedUninit,
            final int expectedID) {
        Assert.assertEquals(refContext.getType(), expectedType);
        Assert.assertEquals(refContext.isMappedSingleRef(), expectedSingle);
        Assert.assertEquals(refContext.isUnmappedUnplaced(), expectedUnplaced);
        Assert.assertEquals(refContext.isMultipleReference(), expectedMulti);
        Assert.assertEquals(refContext.isUninitialized(), expectedUninit);

        // can't serialize an uninitialized RefContext
        if (!expectedUninit) {
            Assert.assertEquals(refContext.getSerializableId(), expectedID);
        }
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testInvalidSetSequenceId() {
        new ReferenceContext(-4);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testInvalidGetSequenceId() {
        ReferenceContext.UNINITIALIZED_CONTEXT.getSerializableId();
    }

    @DataProvider(name = "serializableSentinels")
    private static Object[][] serializableSentinels() {
        return new Object[][] {
                {MULTI_REF_ID},
                {UNPLACED_REF_ID}
        };
    }

    @Test(dataProvider = "serializableSentinels")
    public void testSentinelsGetSerializableId(final int seqId) {
        final ReferenceContext context = new ReferenceContext(seqId);
        Assert.assertEquals(context.getSerializableId(), seqId);
    }

    @Test(dataProvider = "serializableSentinels", expectedExceptions = CRAMException.class)
    public void testSentinelsGetSequenceId(final int seqId) {
        final ReferenceContext context = new ReferenceContext(seqId);
        context.getSequenceId();
    }
}
