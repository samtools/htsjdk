package htsjdk.samtools;

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 28/04/2015.
 */
public class CRAMComplianceTest {

    @DataProvider(name = "test1")
    public Object[][] createData1() {
        return new Object[][]{
                {"auxf#values"},
                {"c1#bounds"},
                {"c1#clip"},
                {"c1#noseq"},
                {"c1#pad1"},
                {"c1#pad2"},
                {"c1#pad3"},
                {"c1#unknown"},
                {"ce#1"},
                {"ce#2"},
                {"ce#5b"},
                {"ce#5"},
                {"ce#large_seq"},
                {"ce#supp"},
                {"ce#tag_depadded"},
                {"ce#tag_padded"},
                {"ce#unmap1"},
                {"ce#unmap2"},
                {"ce#unmap"},
                {"xx#blank"},
                {"xx#large_aux2"},
                {"xx#large_aux"},
                {"xx#minimal"},
                {"xx#pair"},
                {"xx#rg"},
                {"xx#triplet"},
                {"xx#unsorted"},
        };
    }


    @BeforeTest
    public void beforeTest() {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
    }

    private static class TestCase {
        File bamFile;
        File refFile;
        File embedCramFile;
        File norefCramFile;
        File refCramFile;

        public TestCase(File root, String name) {
            bamFile = new File(root, name + ".sam");
            refFile = new File(root, name.split("#")[0] + ".fa");
            embedCramFile = new File(root, name + ".embed.cram");
            norefCramFile = new File(root, name + ".noref.cram");
            refCramFile = new File(root, name + ".ref.cram");
        }
    }

    @Test(dataProvider = "test1")
    public void test(String name) throws IOException {
        TestCase t = new TestCase(new File("testdata/htsjdk/samtools/cram/"), name);

        ReferenceSource source = null;
        if (t.refFile.exists())
            source = new ReferenceSource(t.refFile);

        SamReaderFactory.setDefaultValidationStringency(ValidationStringency.SILENT);

        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(t.bamFile);

        final SAMRecordIterator samRecordIterator = reader.iterator();
        List<SAMRecord> samRecords = new ArrayList<SAMRecord>();
        while (samRecordIterator.hasNext())
            samRecords.add(samRecordIterator.next());
        SAMFileHeader samFileHeader = reader.getFileHeader();
        reader.close();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, source, samFileHeader, name);
        for (SAMRecord samRecord : samRecords)
            cramFileWriter.addAlignment(samRecord);
        cramFileWriter.close();


        CRAMFileReader cramFileReader = new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), null, source, ValidationStringency.SILENT);
        SAMRecordIterator cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertSameRecords(samRecord, restored);
        }
        Assert.assertFalse(cramFileReaderIterator.hasNext());

        if (t.embedCramFile.exists()) {
            cramFileReader = new CRAMFileReader(new FileInputStream(t.embedCramFile), null, source, ValidationStringency.SILENT);
            cramFileReaderIterator = cramFileReader.getIterator();
            for (SAMRecord samRecord : samRecords) {
                Assert.assertTrue(cramFileReaderIterator.hasNext());
                SAMRecord restored = cramFileReaderIterator.next();
                Assert.assertNotNull(restored);
                assertSameRecords(samRecord, restored);
            }
            Assert.assertFalse(cramFileReaderIterator.hasNext());
        }

        if (t.norefCramFile.exists()) {
            cramFileReader = new CRAMFileReader(new FileInputStream(t.norefCramFile), null, source, ValidationStringency.SILENT);
            cramFileReaderIterator = cramFileReader.getIterator();
            for (SAMRecord samRecord : samRecords) {
                Assert.assertTrue(cramFileReaderIterator.hasNext());
                SAMRecord restored = cramFileReaderIterator.next();
                Assert.assertNotNull(restored);
                assertSameRecords(samRecord, restored);
            }
            Assert.assertFalse(cramFileReaderIterator.hasNext());
        }

        if (t.refCramFile.exists()) {
            cramFileReader = new CRAMFileReader(new FileInputStream(t.refCramFile), null, source, ValidationStringency.SILENT);
            cramFileReaderIterator = cramFileReader.getIterator();
            for (SAMRecord samRecord : samRecords) {
                Assert.assertTrue(cramFileReaderIterator.hasNext());
                SAMRecord restored = cramFileReaderIterator.next();
                Assert.assertNotNull(restored);
                assertSameRecords(samRecord, restored);
            }
            Assert.assertFalse(cramFileReaderIterator.hasNext());
        }
    }

    private void assertSameRecords(SAMRecord record1, SAMRecord record2) {
//        System.out.print(record1.getSAMString());
//        System.out.print(record2.getSAMString());
        System.out.println();
        Assert.assertEquals(record2.getFlags(), record1.getFlags());
        Assert.assertEquals(record2.getReadName(), record1.getReadName());
        Assert.assertEquals(record2.getReferenceName(), record1.getReferenceName());
        Assert.assertEquals(record2.getAlignmentStart(), record1.getAlignmentStart());
        Assert.assertEquals(record2.getReadBases(), record1.getReadBases());
        Assert.assertEquals(record2.getBaseQualities(), record1.getBaseQualities());
    }

}
