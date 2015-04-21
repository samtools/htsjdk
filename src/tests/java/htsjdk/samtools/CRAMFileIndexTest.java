package htsjdk.samtools;

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A collection of tests for CRAM index write/read that use BAMFileIndexTest/index_test.bam file as the source of the test data.
 * The test will create a BAI index of the cram file before hand.
 * The scan* tests check that for every records in the BAM file the query returns the same records from the CRAM file.
 * Created by Vadim on 14/03/2015.
 */
public class CRAMFileIndexTest {
    private final File BAM_FILE = new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private final File cramFile = new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.cram");
    private final File indexFile = new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.cram.bai");
    private byte[] cramBytes;
    private byte[] baiBytes;
    private ReferenceSource source;

    @Test
    public void test_chrmM_1500() throws IOException {
        CRAMFileReader reader = new CRAMFileReader(cramFile, indexFile, source);
        reader.setValidationStringency(ValidationStringency.SILENT);
        CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1500);
        Assert.assertTrue(iterator.hasNext());
        SAMRecord record = iterator.next();

        Assert.assertEquals(record.getReferenceName(), "chrM");
        Assert.assertTrue(record.getAlignmentStart() >= 1500);
    }

    @Test
    public void scanAllMappedReads() throws IOException {
        SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
        SAMRecordIterator samRecordIterator = samReader.iterator();
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes), new ByteArraySeekableStream(baiBytes), source, ValidationStringency.SILENT);
        reader.setValidationStringency(ValidationStringency.SILENT);

        int counter = 0;
        long ms = System.currentTimeMillis();
        while (samRecordIterator.hasNext()) {
            SAMRecord samRecord = samRecordIterator.next();
            if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) break;
            String s1 = samRecord.getSAMString();

            CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart(samRecord.getReferenceName(), samRecord.getAlignmentStart());
            Assert.assertTrue(iterator.hasNext(), counter + ": " + s1);
            SAMRecord cramRecord = iterator.next();

            String s2 = cramRecord.getSAMString();

            Assert.assertEquals(samRecord.getReferenceName(), cramRecord.getReferenceName(), s1 + "\n" + s2);
            Assert.assertEquals(samRecord.getAlignmentStart(), cramRecord.getAlignmentStart(), s1 + "\n" + s2);

            counter++;
            if ((System.currentTimeMillis() - ms) > 10000) {
                System.out.println(counter);
                ms = System.currentTimeMillis();
            }
        }
        samRecordIterator.close();
        reader.close();

        Assert.assertEquals(counter, 9721);
    }

    @Test
    public void testIteratorFromFileSpan() throws IOException {
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes), new ByteArraySeekableStream(baiBytes), source, ValidationStringency.SILENT);
        reader.setValidationStringency(ValidationStringency.SILENT);

        final SAMFileSpan allContainers = reader.getFilePointerSpanningReads();
        final CloseableIterator<SAMRecord> iterator = reader.getIterator(allContainers);
        Assert.assertTrue(iterator.hasNext());
        int counter = 0;
        while (iterator.hasNext()) {
            iterator.next();
            counter++;
        }
        Assert.assertEquals(counter, 10000);
    }

    @Test
    public void testQueryInterval() throws IOException {
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes), new ByteArraySeekableStream(baiBytes), source, ValidationStringency.SILENT);
        QueryInterval[] query = new QueryInterval[]{new QueryInterval(0, 1519, 1520), new QueryInterval(1, 470535, 470536)};
        final CloseableIterator<SAMRecord> iterator = reader.query(query, false);
        Assert.assertTrue(iterator.hasNext());
        SAMRecord r1 = iterator.next();
        Assert.assertEquals(r1.getReadName(), "3968040");

        Assert.assertTrue(iterator.hasNext());
        SAMRecord r2 = iterator.next();
        Assert.assertEquals(r2.getReadName(), "140419");

        Assert.assertFalse(iterator.hasNext());
        iterator.close();
        reader.close();
    }

    @Test
    public void scanAllUnmappedReads() throws IOException {
        SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes), new ByteArraySeekableStream(baiBytes), source, ValidationStringency.SILENT);
        reader.setValidationStringency(ValidationStringency.SILENT);
        long ms = System.currentTimeMillis();
        int counter = 0;

        SAMRecordIterator unmappedSamIterator = samReader.queryUnmapped();
        CloseableIterator<SAMRecord> unmappedCramIterator = reader.queryUnmapped();
        while (unmappedSamIterator.hasNext()) {
            Assert.assertTrue(unmappedCramIterator.hasNext());
            SAMRecord r1 = unmappedSamIterator.next();
            SAMRecord r2 = unmappedCramIterator.next();
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

            counter++;
            if ((System.currentTimeMillis() - ms) > 10000) {
                System.out.println(counter);
                ms = System.currentTimeMillis();
            }
        }
        Assert.assertFalse(unmappedCramIterator.hasNext());
        Assert.assertEquals(counter, 279);

        reader.close();
    }

    @BeforeTest
    public void prepare() throws IOException {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
        source = new ReferenceSource(new FakeReferenceSequenceFile(SamReaderFactory.makeDefault().getFileHeader(BAM_FILE).getSequenceDictionary().getSequences()));
        cramBytes = cramFromBAM(BAM_FILE, source);
        FileOutputStream fos = new FileOutputStream(cramFile);
        fos.write(cramBytes);
        fos.close();
//                readFile(cramFile);


        CRAMIndexer.createIndex(new SeekableFileStream(cramFile), indexFile, null);
        baiBytes = readFile(indexFile);
    }

    private static byte[] readFile(File file) throws FileNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtil.copyStream(fis, baos);
        return baos.toByteArray();
    }

    private byte[] cramFromBAM(File bamFile, ReferenceSource source) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final SamReader reader = SamReaderFactory.makeDefault().open(bamFile);
        final SAMRecordIterator iterator = reader.iterator();
        CRAMFileWriter writer = new CRAMFileWriter(baos, source, reader.getFileHeader(), bamFile.getName());
        while (iterator.hasNext()) {
            SAMRecord record = iterator.next();
            writer.addAlignment(record);
        }
        writer.close();
        return baos.toByteArray();
    }
}
