package htsjdk.samtools.cram.structure;

import com.google.common.collect.Lists;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.ref.ReferenceContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 28/09/2015.
 */
public class CramCompressionRecordTest extends HtsjdkTest {
    @Test
    public void test_getAlignmentSpanAndEnd() {
        CramCompressionRecord r = new CramCompressionRecord();
        int readLength = 100;
        r.alignmentStart = 5;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);

        int expectedSpan = r.readLength;
        assertSpanAndEnd(r, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 10;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String softClip = "AAA";
        r.readFeatures.add(new SoftClip(1, softClip.getBytes()));

        expectedSpan = r.readLength - softClip.length();
        assertSpanAndEnd(r, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 20;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        int deletionLength = 5;
        r.readFeatures.add(new Deletion(1, deletionLength));

        expectedSpan = r.readLength + deletionLength;
        assertSpanAndEnd(r, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 30;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String insertion = "CCCCCCCCCC";
        r.readFeatures.add(new Insertion(1, insertion.getBytes()));

        expectedSpan = r.readLength - insertion.length();
        assertSpanAndEnd(r, expectedSpan);

        r = new CramCompressionRecord();
        r.alignmentStart = 40;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        r.readFeatures.add(new InsertBase(1, (byte) 'A'));

        expectedSpan = r.readLength - 1;
        assertSpanAndEnd(r, expectedSpan);
    }

    private void assertSpanAndEnd(final CramCompressionRecord r,
                                  final int expectedSpan) {
        final int expectedEnd = expectedSpan + r.alignmentStart - 1;
        Assert.assertEquals(r.getAlignmentSpan(), expectedSpan);
        Assert.assertEquals(r.getAlignmentEnd(), expectedEnd);
    }

    // https://github.com/samtools/htsjdk/issues/1301

    // demonstrate the bug: alignmentEnd and alignmentSpan are set once only
    // and do not update to reflect the state of the record

    @Test
    public void demonstrateBug1301() {
        final CramCompressionRecord r = new CramCompressionRecord();
        final int alignmentStart = 5;
        final int readLength = 100;
        r.alignmentStart = alignmentStart;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        Assert.assertEquals(r.getAlignmentEnd(), readLength + alignmentStart - 1);
        Assert.assertEquals(r.getAlignmentSpan(), readLength);

        // matches original values, not the new ones

        r.alignmentStart++;
        Assert.assertEquals(r.getAlignmentEnd(), readLength + alignmentStart - 1);
        Assert.assertEquals(r.getAlignmentSpan(), readLength);
        Assert.assertNotEquals(r.getAlignmentEnd(), readLength + r.alignmentStart - 1);

        r.readLength++;
        Assert.assertEquals(r.getAlignmentEnd(), readLength + alignmentStart - 1);
        Assert.assertEquals(r.getAlignmentSpan(), readLength);
        Assert.assertNotEquals(r.getAlignmentSpan(), r.readLength);
    }

    @DataProvider(name = "placedTests")
    private Object[][] placedTests() {
        final List<Object[]> retval = new ArrayList<>();

        final int validSeqId = 0;
        final int[] sequenceIds = new int[]{ SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, validSeqId };
        final int validAlignmentStart = 1;
        final int[] starts = new int[]{ SAMRecord.NO_ALIGNMENT_START, validAlignmentStart };
        final boolean[] mappeds = new boolean[] { true, false };

        for (final int sequenceId : sequenceIds) {
            for (final int start : starts) {
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

                    if (start == SAMRecord.NO_ALIGNMENT_START) {
                        placementExpectation = false;
                    }

                    retval.add(new Object[]{sequenceId, start, mapped, placementExpectation});
                }

            }
        }

        return retval.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "placedTests")
    public void test_isPlaced(final int sequenceId,
                              final int alignmentStart,
                              final boolean mapped,
                              final boolean placementExpectation) {
        final CramCompressionRecord r = new CramCompressionRecord();
        r.sequenceId = sequenceId;
        r.alignmentStart = alignmentStart;
        r.setSegmentUnmapped(!mapped);
        Assert.assertEquals(r.isPlaced(), placementExpectation);
    }

    @Test(dataProvider = "placedTests")
    public void test_placementForAlignmentSpanAndEnd(final int sequenceId,
                                                     final int alignmentStart,
                                                     final boolean mapped,
                                                     final boolean placementExpectation) {
        final CramCompressionRecord r = new CramCompressionRecord();
        r.sequenceId = sequenceId;
        r.alignmentStart = alignmentStart;
        r.setSegmentUnmapped(!mapped);
        final int readLength = 100;
        r.readLength = readLength;

        if (placementExpectation) {
            Assert.assertEquals(r.getAlignmentSpan(), readLength);
            Assert.assertEquals(r.getAlignmentEnd(), alignmentStart + readLength - 1);
        }
        else {
            Assert.assertEquals(r.getAlignmentSpan(), Slice.NO_ALIGNMENT_SPAN);
            Assert.assertEquals(r.getAlignmentEnd(), Slice.NO_ALIGNMENT_END);
        }
    }

    @Test
    public void testEqualsAndHashCodeAreConsistent() {
        final List<CramCompressionRecord> records = new ArrayList<>();

        final List<ReadFeature> features = new ArrayList<>();
        String softClip = "AAA";
        features.add(new SoftClip(1, softClip.getBytes()));
        String insertion = "CCCCCCCCCC";
        features.add(new Insertion(1, insertion.getBytes()));

        for (int alignmentStart : new int[] {0, 1}) {
            for (int readLength : new int[] {100, 101}) {
                for (int flags : new int[] {0, 0x4}) {
                    for (List<ReadFeature> readFeatures : Lists.<List<ReadFeature>>newArrayList(null, new ArrayList<>(), features)) {
                        for (String readName : new String[] {null, "", "r"}) {
                            for (byte[] readBases : new byte[][]{null, new byte[]{(byte) 'A', (byte) 'C'}}) {
                                for (byte[] qualityScores : new byte[][]{null, new byte[]{(byte) 1, (byte) 2}}) {
                                    final CramCompressionRecord r = new CramCompressionRecord();
                                    r.alignmentStart = alignmentStart;
                                    r.readLength = readLength;
                                    r.flags = flags;
                                    r.readFeatures = readFeatures;
                                    r.readName = readName;
                                    r.readBases = readBases;
                                    r.qualityScores = qualityScores;
                                    records.add(r);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (CramCompressionRecord r1 : records) {
            for (CramCompressionRecord r2 : records) {
                if (r1.equals(r2)) {
                    Assert.assertEquals(r1.hashCode(), r2.hashCode(), String.format("Comparing %s and %s", r1, r2));
                }
            }
        }
    }
}
