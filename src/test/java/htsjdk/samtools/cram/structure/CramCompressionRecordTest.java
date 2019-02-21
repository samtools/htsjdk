package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 28/09/2015.
 */
public class CramCompressionRecordTest extends HtsjdkTest {
    @DataProvider(name = "deltaEncodingTrueFalse")
    private Object[][] tf() {
        return new Object[][] { {true}, {false}};
    }

    @Test(dataProvider = "deltaEncodingTrueFalse")
    public void test_getAlignmentSpanAndEnd(final boolean usePositionDeltaEncoding) {
        CramCompressionRecord r = new CramCompressionRecord();
        int readLength = 100;
        r.alignmentStart = 5;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);

        int expectedSpan = r.readLength;
        assertSpanAndEnd(r, usePositionDeltaEncoding, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 10;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String softClip = "AAA";
        r.readFeatures.add(new SoftClip(1, softClip.getBytes()));

        expectedSpan = r.readLength - softClip.length();
        assertSpanAndEnd(r, usePositionDeltaEncoding, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 20;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        int deletionLength = 5;
        r.readFeatures.add(new Deletion(1, deletionLength));

        expectedSpan = r.readLength + deletionLength;
        assertSpanAndEnd(r, usePositionDeltaEncoding, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 30;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String insertion = "CCCCCCCCCC";
        r.readFeatures.add(new Insertion(1, insertion.getBytes()));

        expectedSpan = r.readLength - insertion.length();
        assertSpanAndEnd(r, usePositionDeltaEncoding, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 40;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        r.readFeatures.add(new InsertBase(1, (byte) 'A'));

        expectedSpan = r.readLength - 1;
        assertSpanAndEnd(r, usePositionDeltaEncoding, expectedSpan);
    }

    private void assertSpanAndEnd(final CramCompressionRecord r,
                                  final boolean usePositionDeltaEncoding,
                                  final int expectedSpan) {
        final int expectedEnd = expectedSpan + r.alignmentStart - 1;
        Assert.assertEquals(r.getAlignmentSpan(usePositionDeltaEncoding), expectedSpan);
        Assert.assertEquals(r.getAlignmentEnd(usePositionDeltaEncoding), expectedEnd);
    }

    // https://github.com/samtools/htsjdk/issues/1301

    // demonstrate the bug: alignmentEnd and alignmentSpan are set once only
    // and do not update to reflect the state of the record

    @Test(dataProvider = "deltaEncodingTrueFalse")
    public void demonstrateBug1301(final boolean usePositionDeltaEncoding) {
        final CramCompressionRecord r = new CramCompressionRecord();
        final int alignmentStart = 5;
        final int readLength = 100;
        r.alignmentStart = alignmentStart;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        Assert.assertEquals(r.getAlignmentEnd(usePositionDeltaEncoding), readLength + alignmentStart - 1);
        Assert.assertEquals(r.getAlignmentSpan(usePositionDeltaEncoding), readLength);

        // matches original values, not the new ones

        r.alignmentStart++;
        Assert.assertEquals(r.getAlignmentEnd(usePositionDeltaEncoding), readLength + alignmentStart - 1);
        Assert.assertEquals(r.getAlignmentSpan(usePositionDeltaEncoding), readLength);
        Assert.assertNotEquals(r.getAlignmentEnd(usePositionDeltaEncoding), readLength + r.alignmentStart - 1);

        r.readLength++;
        Assert.assertEquals(r.getAlignmentEnd(usePositionDeltaEncoding), readLength + alignmentStart - 1);
        Assert.assertEquals(r.getAlignmentSpan(usePositionDeltaEncoding), readLength);
        Assert.assertNotEquals(r.getAlignmentSpan(usePositionDeltaEncoding), r.readLength);
    }

    @DataProvider(name = "placedTests")
    private Object[][] placedTests() {
        final List<Object[]> retval = new ArrayList<>();

        final int validSeqId = 0;
        final int[] sequenceIds = new int[]{ SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, validSeqId };
        final int validAlignmentStart = 1;
        final int[] starts = new int[]{ SAMRecord.NO_ALIGNMENT_START, validAlignmentStart };
        final boolean[] deltas = new boolean[] { true, false };
        final boolean[] mappeds = new boolean[] { true, false };

        for (final int sequenceId : sequenceIds) {
            for (final int start : starts) {
                for (final boolean delta : deltas) {
                    for (final boolean mapped : mappeds) {

                        // logically, unplaced reads should never be mapped.
                        // when isPlaced() sees an unplaced-mapped read, it returns false and emits a log warning.
                        // it does not affect expectations here.

                        boolean placementExpectation = true;

                        // we also expect that read sequenceIds and alignmentStart are both valid or both invalid.
                        // however: we do handle the edge case where only one of the pair is valid
                        // by marking it as unplaced.

                        if (sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                            placementExpectation = false;
                        }

                        if (!delta && start == SAMRecord.NO_ALIGNMENT_START) {
                            placementExpectation = false;
                        }

                        retval.add(new Object[]{sequenceId, start, mapped, delta, placementExpectation});
                    }
                }
            }
        }

        return retval.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "placedTests")
    public void test_isPlaced(final int sequenceId,
                              final int alignmentStart,
                              final boolean mapped,
                              final boolean usePositionDeltaEncoding,
                              final boolean placementExpectation) {
        final CramCompressionRecord r = new CramCompressionRecord();
        r.sequenceId = sequenceId;
        r.alignmentStart = alignmentStart;
        r.setSegmentUnmapped(!mapped);
        Assert.assertEquals(r.isPlaced(usePositionDeltaEncoding), placementExpectation);
    }

    @Test(dataProvider = "placedTests")
    public void test_placementForAlignmentSpanAndEnd(final int sequenceId,
                                                     final int alignmentStart,
                                                     final boolean mapped,
                                                     final boolean usePositionDeltaEncoding,
                                                     final boolean placementExpectation) {
        final CramCompressionRecord r = new CramCompressionRecord();
        r.sequenceId = sequenceId;
        r.alignmentStart = alignmentStart;
        r.setSegmentUnmapped(!mapped);
        final int readLength = 100;
        r.readLength = readLength;

        if (placementExpectation) {
            Assert.assertEquals(r.getAlignmentSpan(usePositionDeltaEncoding), readLength);
            Assert.assertEquals(r.getAlignmentEnd(usePositionDeltaEncoding), alignmentStart + readLength - 1);
        }
        else {
            Assert.assertEquals(r.getAlignmentSpan(usePositionDeltaEncoding), Slice.NO_ALIGNMENT_SPAN);
            Assert.assertEquals(r.getAlignmentEnd(usePositionDeltaEncoding), Slice.NO_ALIGNMENT_END);
        }
    }
}
