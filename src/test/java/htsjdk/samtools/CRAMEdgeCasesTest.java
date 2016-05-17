package htsjdk.samtools;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * A collection of CRAM test based on round trip comparison of SAMRecord before and after CRAM compression.
 */
public class CRAMEdgeCasesTest {

    @BeforeTest
    public void beforeTest() {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
    }

    @Test
    public void testUnsorted() throws IOException {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);
        builder.addFrag("1", 0, 2, false);
        builder.addFrag("1", 0, 1, false);
        final Collection<SAMRecord> records = builder.getRecords();

        testRecords(records, records.iterator().next().getReadBases());
    }

    // int test for CRAMException
    // testing for a contig found in the reads but not in the reference
    @Test(expectedExceptions = CRAMException.class)
    public void testContigNotFoundInRef() throws IOException {
        boolean sawException = false;
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/CRAMException/testContigNotInRef.cram");
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/CRAMException/testContigNotInRef.fa");
        final ReferenceSource refSource = new ReferenceSource(refFile);
        final CRAMIterator iterator = new CRAMIterator(new FileInputStream(CRAMFile), refSource, ValidationStringency.STRICT);
        while (iterator.hasNext()) {
            iterator.next();
        }
    }

    @Test
    public void testBizilionTags() throws IOException {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        builder.addFrag("1", 0, 1, false);
        SAMRecord record = builder.getRecords().iterator().next();
        for (int i = 0; i < 1000; i++) {
            char b1 = (char) ('A' + i / 26);
            char b2 = (char) ('A' + i % 26);
            String tag = new String(new char[]{b1, b2});
            if ("RG".equals(tag)) {
                continue;
            }
            record.setAttribute(tag, i);
        }

        record.setAlignmentStart(1);
        testSingleRecord(record, record.getReadBases());
    }

    @Test
    public void testNullsAndBeyondRef() throws IOException {
        testSingleRecord("A".getBytes(), "!".getBytes(), "A".getBytes());
        testSingleRecord("A".getBytes(), SAMRecord.NULL_QUALS, "A".getBytes());
        testSingleRecord(SAMRecord.NULL_SEQUENCE, SAMRecord.NULL_QUALS, "A".getBytes());
        testSingleRecord("AAA".getBytes(), "!!!".getBytes(), "A".getBytes());
    }

    private void testRecords(Collection<SAMRecord> records, byte[] ref) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InMemoryReferenceSequenceFile refFile = new InMemoryReferenceSequenceFile();
        refFile.add("chr1", ref);
        ReferenceSource source = new ReferenceSource(refFile);
        final SAMFileHeader header = records.iterator().next().getHeader();
        CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, source, header, "whatever");

        Iterator<SAMRecord> it = records.iterator();
        while (it.hasNext()) {
            SAMRecord record = it.next();
            cramFileWriter.addAlignment(record);
        }
        cramFileWriter.close();

        CRAMFileReader cramFileReader = new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), (SeekableStream) null, source, ValidationStringency.SILENT);
        final SAMRecordIterator iterator = cramFileReader.getIterator();
        Assert.assertTrue(iterator.hasNext());

        it = records.iterator();
        while (it.hasNext()) {
            SAMRecord record = it.next();
            SAMRecord s2 = iterator.next();
            Assert.assertNotNull(s2);
            Assert.assertEquals(record.getFlags(), s2.getFlags());
            Assert.assertEquals(record.getReadName(), s2.getReadName());
            Assert.assertEquals(record.getReferenceName(), s2.getReferenceName());
            Assert.assertEquals(record.getAlignmentStart(), s2.getAlignmentStart());
            Assert.assertEquals(record.getReadBases(), s2.getReadBases());
            Assert.assertEquals(record.getBaseQualities(), s2.getBaseQualities());
        }
        Assert.assertFalse(iterator.hasNext());
    }

    private void testSingleRecord(SAMRecord record, byte[] ref) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InMemoryReferenceSequenceFile refFile = new InMemoryReferenceSequenceFile();
        refFile.add("chr1", ref);
        ReferenceSource source = new ReferenceSource(refFile);
        CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, source, record.getHeader(), "whatever");
        cramFileWriter.addAlignment(record);
        cramFileWriter.close();

        CRAMFileReader cramFileReader = new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), (SeekableStream) null, source, ValidationStringency.SILENT);
        final SAMRecordIterator iterator = cramFileReader.getIterator();
        Assert.assertTrue(iterator.hasNext());
        SAMRecord s2 = iterator.next();
        Assert.assertNotNull(s2);
        Assert.assertFalse(iterator.hasNext());

        Assert.assertEquals(record.getFlags(), s2.getFlags());
        Assert.assertEquals(record.getReadName(), s2.getReadName());
        Assert.assertEquals(record.getReferenceName(), s2.getReferenceName());
        Assert.assertEquals(record.getAlignmentStart(), s2.getAlignmentStart());
        Assert.assertEquals(record.getReadBases(), s2.getReadBases());
        Assert.assertEquals(record.getBaseQualities(), s2.getBaseQualities());
    }

    private void testSingleRecord(byte[] bases, byte[] scores, byte[] ref) throws IOException {
        SAMFileHeader header = new SAMFileHeader();
        header.addReadGroup(new SAMReadGroupRecord("1"));
        header.addSequence(new SAMSequenceRecord("chr1", ref.length));
        SAMRecord s = new SAMRecord(header);
        s.setReadBases(bases);
        s.setBaseQualities(scores);
        s.setFlags(0);
        s.setAlignmentStart(1);
        s.setReferenceName("chr1");
        s.setReadName("1");
        if (bases == SAMRecord.NULL_SEQUENCE) {
            s.setCigarString("10M");
        } else {
            s.setCigarString(s.getReadLength() + "M");
        }

        testSingleRecord(s, ref);
    }
}
