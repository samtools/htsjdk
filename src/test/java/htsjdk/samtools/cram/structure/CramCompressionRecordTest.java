package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * Created by vadim on 28/09/2015.
 */
public class CramCompressionRecordTest extends HtsjdkTest {
    @DataProvider(name = "tf")
    private Object[][] tf() {
        return new Object[][] { {true}, {false}};
    }

    @Test(dataProvider = "tf")
    public void test_getAlignmentEnd(final boolean APDelta) {
        CramCompressionRecord r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.setSegmentUnmapped(true);
        Assert.assertEquals(r.getAlignmentEnd(APDelta), SAMRecord.NO_ALIGNMENT_START);

        r = new CramCompressionRecord();
        int readLength = 100;
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1);

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String softClip = "AAA";
        r.readFeatures.add(new SoftClip(1, softClip.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 - softClip.length());

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        int deletionLength = 5;
        r.readFeatures.add(new Deletion(1, deletionLength));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 + deletionLength);

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String insertion = "CCCCCCCCCC";
        r.readFeatures.add(new Insertion(1, insertion.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 - insertion.length());
        
        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        r.readFeatures.add(new InsertBase(1, (byte) 'A'));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 - 1);
    }

    @Test
    public void test_getAlignmentEndtrue() {
        final boolean APDelta = true;

        CramCompressionRecord r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.setSegmentUnmapped(true);
        Assert.assertEquals(r.getAlignmentEnd(APDelta), SAMRecord.NO_ALIGNMENT_START);

        r = new CramCompressionRecord();
        int readLength = 100;
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1);

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String softClip = "AAA";
        r.readFeatures.add(new SoftClip(1, softClip.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 - softClip.length());

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        int deletionLength = 5;
        r.readFeatures.add(new Deletion(1, deletionLength));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 + deletionLength);

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        String insertion = "CCCCCCCCCC";
        r.readFeatures.add(new Insertion(1, insertion.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 - insertion.length());


        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<>();
        r.readFeatures.add(new InsertBase(1, (byte) 'A'));
        Assert.assertEquals(r.getAlignmentEnd(APDelta), r.readLength + r.alignmentStart - 1 - 1);
    }


    @DataProvider(name = "placedTests")
    private Object[][] placedTests() {
        return new Object[][] {
                // APDelta = false.  Must have a valid Start as well as a Reference
                {false, false, false, false},
                // APDelta = true.  Must have a valid Reference.  Invalid Start is OK because we use the Delta instead
                {true, false, false, true}
        };
    }

    @Test(dataProvider = "placedTests")
    public void test_isPlaced(final boolean APDelta,
                              final boolean noRefExpectation,
                              final boolean bothExpectation,
                              final boolean noStartExpectation) {
        final CramCompressionRecord r = new CramCompressionRecord();

        // it's only Placed if both of these are valid

        r.sequenceId = 5;
        r.alignmentStart = 10;
        Assert.assertTrue(r.isPlaced(APDelta));

        r.sequenceId = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        Assert.assertEquals(r.isPlaced(APDelta), noRefExpectation);

        r.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
        Assert.assertEquals(r.isPlaced(APDelta), bothExpectation);

        r.sequenceId = 3;
        Assert.assertEquals(r.isPlaced(APDelta), noStartExpectation);

        r.alignmentStart = 15;
        Assert.assertTrue(r.isPlaced(APDelta));
    }
}
