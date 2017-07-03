package htsjdk.samtools;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by vadim on 29/06/2017.
 */
public class BAMReadBasesTest {

    /**
     * A test to check that BAM changes read bases according with {@link SequenceUtil#toBamReadBases}.
     */
    @Test
    public void testBAMReadBases() throws IOException {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", SequenceUtil.IUPAC_CODES_STRING.length()));
        header.addReadGroup(new SAMReadGroupRecord("rg1"));

        final SAMRecord originalSAMRecord = new SAMRecord(header);
        originalSAMRecord.setReadName("test");
        originalSAMRecord.setReferenceIndex(0);
        originalSAMRecord.setAlignmentStart(1);
        originalSAMRecord.setReadBases(SequenceUtil.IUPAC_CODES_STRING.getBytes());
        originalSAMRecord.setCigarString(originalSAMRecord.getReadLength() + "M");
        originalSAMRecord.setBaseQualities(SAMRecord.NULL_QUALS);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final BAMFileWriter writer = new BAMFileWriter(baos, null);
        writer.setHeader(header);
        writer.addAlignment(originalSAMRecord);
        writer.close();

        final BAMFileReader reader = new BAMFileReader(new ByteArrayInputStream(baos.toByteArray()), null, true, false, ValidationStringency.SILENT, new DefaultSAMRecordFactory());
        final CloseableIterator<SAMRecord> iterator = reader.getIterator();
        iterator.hasNext();
        final SAMRecord recordFromBAM = iterator.next();

        Assert.assertNotEquals(recordFromBAM.getReadBases(), originalSAMRecord.getReadBases());
        Assert.assertEquals(recordFromBAM.getReadBases(), SequenceUtil.toBamReadBases(originalSAMRecord.getReadBases()));
    }

}
