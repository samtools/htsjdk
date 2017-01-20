package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;

/**
 * Created by vadim on 19/02/2016.
 */
public class LosslessRoundTripTest extends HtsjdkTest {
    @Test
    public void test_MD_NM() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add("1", "AAA".getBytes());
        ReferenceSource source = new ReferenceSource(rsf);

        SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.addSequence(new SAMSequenceRecord("1", 3));
        samFileHeader.addReadGroup(new SAMReadGroupRecord("some read group"));

        CRAMFileWriter w = new CRAMFileWriter(baos, source, samFileHeader, null);
        SAMRecord record = new SAMRecord(samFileHeader);
        record.setReadName("name");
        record.setAlignmentStart(1);
        record.setReferenceIndex(0);
        record.setCigarString("3M");
        record.setReadUnmappedFlag(false);
        record.setReadBases("AAC".getBytes());
        record.setBaseQualities("!!!".getBytes());

        record.setAttribute("RG", "some read group");
        // setting some bizzar values to provoke test failure if the values are auto-restored while reading CRAM:
        record.setAttribute("MD", "nonsense");
        record.setAttribute("NM", 123);
        w.addAlignment(record);
        w.close();

        byte[] cramBytes = baos.toByteArray();
        InputStream cramInputStream = new ByteArrayInputStream(cramBytes);
        CRAMFileReader reader = new CRAMFileReader(cramInputStream, (File) null, source, ValidationStringency.STRICT);
        final SAMRecordIterator iterator = reader.getIterator();
        Assert.assertTrue(iterator.hasNext());
        SAMRecord record2 = iterator.next();
        Assert.assertNotNull(record2);

        Assert.assertEquals(record2, record);
        reader.close();
    }
}
