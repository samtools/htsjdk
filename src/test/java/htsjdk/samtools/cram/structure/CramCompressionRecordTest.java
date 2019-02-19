package htsjdk.samtools.cram.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 28/09/2015.
 */
public class CramCompressionRecordTest extends HtsjdkTest {
    @Test
    public void test_getAlignmentEnd() {
        CramCompressionRecord r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.setSegmentUnmapped(true);
        Assert.assertEquals(r.getAlignmentEnd(), SAMRecord.NO_ALIGNMENT_START);

        r = new CramCompressionRecord();
        int readLength = 100;
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1);

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String softClip = "AAA";
        r.readFeatures.add(new SoftClip(1, softClip.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 - softClip.length());

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        int deletionLength = 5;
        r.readFeatures.add(new Deletion(1, deletionLength));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 + deletionLength);

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String insertion = "CCCCCCCCCC";
        r.readFeatures.add(new Insertion(1, insertion.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 - insertion.length());


        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        r.readFeatures.add(new InsertBase(1, (byte) 'A'));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 - 1);
    }

    @Test
    public void test_isPlaced() {
        final CramCompressionRecord r = new CramCompressionRecord();

        // it's only Placed if both of these are valid

        r.sequenceId = 5;
        r.alignmentStart = 10;
        Assert.assertTrue(r.isPlaced());

        r.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        Assert.assertFalse(r.isPlaced());

        r.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        Assert.assertFalse(r.isPlaced());

        r.sequenceId = 3;
        Assert.assertFalse(r.isPlaced());

        r.alignmentStart = 15;
        Assert.assertTrue(r.isPlaced());
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
