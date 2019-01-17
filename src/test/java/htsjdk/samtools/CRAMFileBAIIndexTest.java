package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.slice.MappedSliceMetadata;
import htsjdk.samtools.cram.structure.slice.SliceMetadata;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CoordMath;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;

/**
 * A collection of tests for CRAM BAI index write/read that use BAMFileIndexTest/index_test.bam file as the source of the test data.
 * The test will create a BAI index of the cram file before hand.
 * The scan* tests check that for every records in the BAM file the query returns the same records from the CRAM file.
 * Created by Vadim on 14/03/2015.
 */
public class CRAMFileBAIIndexTest extends HtsjdkTest {
    private final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private File cramFile;
    private File indexFile;
    private byte[] cramBytes;
    private byte[] baiBytes;
    private ReferenceSource source;
    private int nofUnmappedReads = 279 ;
    private int nofMappedReads = 9721;
    private int nofReads = 10000 ;
    private int nofReadsPerContainer = 1000 ;


    // Mixes testing queryAlignmentStart with each CRAMFileReaderConstructor
    // Separate into individual tests
    @Test
    public void testConstructors () throws IOException {
        CRAMFileReader reader = new CRAMFileReader(cramFile, indexFile, source, ValidationStringency.SILENT);
        CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1500);
        Assert.assertTrue(iterator.hasNext());
        SAMRecord record = iterator.next();

        Assert.assertEquals(record.getReferenceName(), "chrM");
        Assert.assertTrue(record.getAlignmentStart() >= 1500);
        reader.close();

        reader = new CRAMFileReader(new SeekableFileStream(cramFile), indexFile, source, ValidationStringency.SILENT);
        iterator = reader.queryAlignmentStart("chrM", 1500);
        Assert.assertTrue(iterator.hasNext());
        record = iterator.next();

        Assert.assertEquals(record.getReferenceName(), "chrM");
        Assert.assertTrue(record.getAlignmentStart() >= 1500);
        reader.close();

        reader = new CRAMFileReader(new SeekableFileStream(cramFile), new SeekableFileStream(indexFile), source, ValidationStringency.SILENT);
        iterator = reader.queryAlignmentStart("chrM", 1500);
        Assert.assertTrue(iterator.hasNext());
        record = iterator.next();

        Assert.assertEquals(record.getReferenceName(), "chrM");
        Assert.assertTrue(record.getAlignmentStart() >= 1500);
        reader.close();

        reader = new CRAMFileReader(new SeekableFileStream(cramFile), (File)null, source, ValidationStringency.SILENT);
        try {
            reader.queryAlignmentStart("chrM", 1500);
            Assert.fail("Expecting query to fail when there is no index");
        } catch (SAMException e) {
        }
        reader.close();

        reader = new CRAMFileReader(new SeekableFileStream(cramFile), (SeekableFileStream)null, source, ValidationStringency.SILENT);
        try {
            reader.queryAlignmentStart("chrM", 1500);
            Assert.fail("Expecting query to fail when there is no index");
        } catch (SAMException e) {
        }
        reader.close();
    }

    // this test is the same as the ones above in testConstructors
    @Test
    public void test_chrM_1500_location() throws IOException {
        CRAMFileReader reader = new CRAMFileReader(cramFile, indexFile, source);
        reader.setValidationStringency(ValidationStringency.SILENT);
        CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1500);
        Assert.assertTrue(iterator.hasNext());
        SAMRecord record = iterator.next();

        Assert.assertEquals(record.getReferenceName(), "chrM");
        Assert.assertTrue(record.getAlignmentStart() >= 1500);
    }

    @Test
    public void scanMappedReads() throws IOException {
        SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
        SAMRecordIterator samRecordIterator = samReader.iterator();
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes), new ByteArraySeekableStream(baiBytes), source, ValidationStringency.SILENT);
        reader.setValidationStringency(ValidationStringency.SILENT);

        int counter = 0;
        while (samRecordIterator.hasNext()) {
            SAMRecord samRecord = samRecordIterator.next();
            if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) break;
            // test only 1st and 2nd in every 100 to speed the test up:
            if (counter++ %100 > 1) continue;
            String s1 = samRecord.getSAMString();

            CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart(samRecord.getReferenceName(), samRecord.getAlignmentStart());
            Assert.assertTrue(iterator.hasNext(), counter + ": " + s1);
            SAMRecord cramRecord = iterator.next();

            String s2 = cramRecord.getSAMString();

            Assert.assertEquals(samRecord.getReferenceName(), cramRecord.getReferenceName(), s1 + s2);
            // default 'overlap' is true, so test records intersect the query:
            Assert.assertTrue(CoordMath.overlaps(cramRecord.getAlignmentStart(), cramRecord.getAlignmentEnd(), samRecord.getAlignmentStart(), samRecord.getAlignmentEnd()), s1 + s2);
        }
        samRecordIterator.close();
        reader.close();

        Assert.assertEquals(counter, nofMappedReads);
    }

    @Test
    public void testNoStringencyConstructor() throws IOException {
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/auxf#values.3.0.cram");
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/auxf.fa");
        ReferenceSource refSource = new ReferenceSource(refFile);

        long start = 0;
        long end = CRAMFile.length();
        long[] boundaries = new long[] {start << 16, (end - 1) << 16};
        final CRAMIterator iterator = new CRAMIterator(new SeekableFileStream(CRAMFile), refSource, boundaries);
        long count = 0;
        while (iterator.hasNext()) {
            count++;
            iterator.next();
        }
        Assert.assertEquals(count, 2);
    }

    @Test
    public void testIteratorFromFileSpan_WholeFile() throws IOException {
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
        Assert.assertEquals(counter, nofReads);
    }

    @Test
    public void testIteratorFromFileSpan_SecondContainer() throws IOException {
        CramContainerIterator it = new CramContainerIterator(new ByteArrayInputStream(cramBytes));
        it.hasNext();
        it.next();
        it.hasNext();
        Container secondContainer = it.next();
        Assert.assertNotNull(secondContainer);
        final Map<Integer, SliceMetadata> references = secondContainer.getSliceMetadata(ValidationStringency.STRICT);
        it.close();
        int refId = new TreeSet<>(references.keySet()).iterator().next();
        final MappedSliceMetadata sliceMetadata = (MappedSliceMetadata) references.get(refId);

        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes), new ByteArraySeekableStream(baiBytes), source, ValidationStringency.SILENT);
        reader.setValidationStringency(ValidationStringency.SILENT);

        final BAMIndex index = reader.getIndex();
        final SAMFileSpan spanOfSecondContainer = index.getSpanOverlapping(refId, sliceMetadata.getAlignmentStart(), sliceMetadata.getAlignmentStart()+ sliceMetadata.getAlignmentSpan());
        Assert.assertNotNull(spanOfSecondContainer);
        Assert.assertFalse(spanOfSecondContainer.isEmpty());
        Assert.assertTrue(spanOfSecondContainer instanceof BAMFileSpan);

        final CloseableIterator<SAMRecord> iterator = reader.getIterator(spanOfSecondContainer);
        Assert.assertTrue(iterator.hasNext());
        int counter = 0;
        boolean matchFound = false;
        while (iterator.hasNext()) {
            final SAMRecord record = iterator.next();
            if (record.getReferenceIndex().intValue() == refId) {
                boolean overlaps = CoordMath.overlaps(record.getAlignmentStart(), record.getAlignmentEnd(), sliceMetadata.getAlignmentStart(), sliceMetadata.getAlignmentStart()+ sliceMetadata.getAlignmentSpan());
                if (overlaps) matchFound = true;
            }
            counter++;
        }
        Assert.assertTrue(matchFound);
        Assert.assertTrue(counter <= CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE);
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
        }
        Assert.assertFalse(unmappedCramIterator.hasNext());
        Assert.assertEquals(counter, nofUnmappedReads);

        reader.close();
    }

    @BeforeTest
    public void prepare() throws IOException {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
        source = new ReferenceSource(new FakeReferenceSequenceFile(SamReaderFactory.makeDefault().getFileHeader(BAM_FILE).getSequenceDictionary().getSequences()));
        cramBytes = cramFromBAM(BAM_FILE, source);
        cramFile = File.createTempFile(BAM_FILE.getName(), ".cram") ;
        cramFile.deleteOnExit();
        indexFile = new File (cramFile.getAbsolutePath() + ".bai");
        indexFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(cramFile);
        fos.write(cramBytes);
        fos.close();

        CRAMBAIIndexer.createIndex(new SeekableFileStream(cramFile), indexFile, null, ValidationStringency.STRICT);
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
        // to reduce granularity let's use this hacky approach:
        int previousValue = CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE ;
        CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE = nofReadsPerContainer;
        try {
            CRAMFileWriter writer = new CRAMFileWriter(baos, source, reader.getFileHeader(), bamFile.getName());
            while (iterator.hasNext()) {
                SAMRecord record = iterator.next();
                writer.addAlignment(record);
            }
            writer.close();
        }
        finally {
            // failing to reset this can cause unrelated tests to fail if this test fails
            CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE = previousValue;
        }
        return baos.toByteArray();
    }


}
