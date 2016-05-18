package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.structure.CramHeader;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by vadim on 25/08/2015.
 */
public class CramIOTest {
    @Test
    public void testCheckHeaderAndEOF_v2() throws IOException {
        final String id = "testid";

        final CramHeader cramHeader = new CramHeader(CramVersions.CRAM_v2_1, id, new SAMFileHeader());
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(file);
        CramIO.writeCramHeader(cramHeader, fos);
        CramIO.issueEOF(cramHeader.getVersion(), fos);
        fos.close();

        Assert.assertTrue(CramIO.checkHeaderAndEOF(file));
        file.delete();
    }

    @Test
    public void testCheckHeaderAndEOF_v3() throws IOException {
        final String id = "testid";

        final CramHeader cramHeader = new CramHeader(CramVersions.CRAM_v3, id, new SAMFileHeader());
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(file);
        CramIO.writeCramHeader(cramHeader, fos);
        CramIO.issueEOF(cramHeader.getVersion(), fos);
        fos.close();

        Assert.assertTrue(CramIO.checkHeaderAndEOF(file));
        file.delete();
    }

    @Test
    public void testReplaceCramHeader() throws IOException {
        final String id = "testid";

        final CramHeader cramHeader = new CramHeader(CramVersions.CRAM_v3, id, new SAMFileHeader());
        Assert.assertTrue(cramHeader.getSamFileHeader().getSequenceDictionary().isEmpty());
        final File file = File.createTempFile("test", ".cram");
        file.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(file);
        CramIO.writeCramHeader(cramHeader, fos);
        CramIO.issueEOF(cramHeader.getVersion(), fos);
        fos.close();
        final long length = file.length();

        final SAMFileHeader samFileHeader = new SAMFileHeader();
        final SAMSequenceRecord sequenceRecord = new SAMSequenceRecord("1", 123);
        samFileHeader.addSequence(sequenceRecord);
        final String id2 = "testid2";
        final CramHeader cramHeader2 = new CramHeader(CramVersions.CRAM_v3, id2, samFileHeader);
        final boolean replaced = CramIO.replaceCramHeader(file, cramHeader2);
        Assert.assertTrue(replaced);
        Assert.assertEquals(file.length(), length);
        Assert.assertTrue(CramIO.checkHeaderAndEOF(file));

        final CramHeader cramHeader3 = CramIO.readCramHeader(new FileInputStream(file));
        Assert.assertEquals(cramHeader3.getVersion(), CramVersions.CRAM_v3);
        Assert.assertFalse(cramHeader3.getSamFileHeader().getSequenceDictionary().isEmpty());
        Assert.assertNotNull(cramHeader3.getSamFileHeader().getSequenceDictionary().getSequence(0));
        Assert.assertEquals(cramHeader3.getSamFileHeader().getSequence(sequenceRecord.getSequenceName()).getSequenceLength(), sequenceRecord.getSequenceLength());
        file.delete();
    }
}
