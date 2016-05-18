package htsjdk.samtools;

import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
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
        File cramFile_21;
        File cramFile_30;

        public TestCase(File root, String name) {
            bamFile = new File(root, name + ".sam");
            refFile = new File(root, name.split("#")[0] + ".fa");
            cramFile_21 = new File(root, name + ".2.1.cram");
            cramFile_30 = new File(root, name + ".3.0.cram");
        }
    }

    @Test(dataProvider = "test1")
    public void test(String name) throws IOException {
        TestCase t = new TestCase(new File("src/test/resources/htsjdk/samtools/cram/"), name);

        ReferenceSource source = new ReferenceSource(t.refFile);
        SamReader reader = SamReaderFactory.make().validationStringency(ValidationStringency.SILENT).open(t.bamFile);

        final SAMRecordIterator samRecordIterator = reader.iterator();
        List<SAMRecord> samRecords = new ArrayList<SAMRecord>();
        while (samRecordIterator.hasNext())
            samRecords.add(samRecordIterator.next());
        SAMFileHeader samFileHeader = reader.getFileHeader();
        reader.close();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, source, samFileHeader, name);
        for (SAMRecord samRecord : samRecords) {
            cramFileWriter.addAlignment(samRecord);
        }
        cramFileWriter.close();


        CRAMFileReader cramFileReader = new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), (SeekableStream)null, source, ValidationStringency.SILENT);
        SAMRecordIterator cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertSameRecords(CramVersions.DEFAULT_CRAM_VERSION.major, samRecord, restored);
        }
        Assert.assertFalse(cramFileReaderIterator.hasNext());

        //v2.1 test
        cramFileReader = new CRAMFileReader(new FileInputStream(t.cramFile_21), (SeekableStream)null, source, ValidationStringency.SILENT);
        cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertSameRecords(CramVersions.CRAM_v2_1.major, samRecord, restored);
        }
        Assert.assertFalse(cramFileReaderIterator.hasNext());

        //v3.0 test
        cramFileReader = new CRAMFileReader(new FileInputStream(t.cramFile_30), (SeekableStream)null, source, ValidationStringency.SILENT);
        cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertSameRecords(CramVersions.CRAM_v3.major, samRecord, restored);
        }
        Assert.assertFalse(cramFileReaderIterator.hasNext());
    }

    private void assertSameRecords(int majorVersion, SAMRecord record1, SAMRecord record2) {
        Assert.assertEquals(record2.getFlags(), record1.getFlags());
        Assert.assertEquals(record2.getReadName(), record1.getReadName());
        Assert.assertEquals(record2.getReferenceName(), record1.getReferenceName());
        Assert.assertEquals(record2.getAlignmentStart(), record1.getAlignmentStart());

        /**
         * Known issue: CRAM v2.1 doesn't handle reads with missing bases correctly. This
         * causes '*' bases to arise when reading CRAM. Skipping the base comparison asserts.
         */
        if (record1.getReadBases() != SAMRecord.NULL_SEQUENCE || majorVersion >= CramVersions.CRAM_v3.major) {
            Assert.assertEquals(record2.getReadBases(), record1.getReadBases());
        }

        Assert.assertEquals(record2.getBaseQualities(), record1.getBaseQualities());
    }

}
