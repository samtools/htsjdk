package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.Deletion;
import htsjdk.samtools.cram.encoding.readfeatures.InsertBase;
import htsjdk.samtools.cram.encoding.readfeatures.Insertion;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * Created by vadim on 28/09/2015.
 */
public class CramCompressionRecordTest {
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
        r.readFeatures = new ArrayList<ReadFeature>();
        String softClip = "AAA";
        r.readFeatures.add(new SoftClip(1, softClip.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 - softClip.length());

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<ReadFeature>();
        int deletionLength = 5;
        r.readFeatures.add(new Deletion(1, deletionLength));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 + deletionLength);

        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<ReadFeature>();
        String insertion = "CCCCCCCCCC";
        r.readFeatures.add(new Insertion(1, insertion.getBytes()));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 - insertion.length());


        r = new CramCompressionRecord();
        r.alignmentStart = 1;
        r.readLength = readLength;
        r.setSegmentUnmapped(false);
        r.readFeatures = new ArrayList<ReadFeature>();
        r.readFeatures.add(new InsertBase(1, (byte) 'A'));
        Assert.assertEquals(r.getAlignmentEnd(), r.readLength + r.alignmentStart - 1 - 1);
    }
}
