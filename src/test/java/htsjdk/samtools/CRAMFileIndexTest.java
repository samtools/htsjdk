package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.*;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;

/**
 * Tests for CRAM BAI/CRAI index write/read which uses BAMFileIndexTest/index_test.bam file as the source of the test data.
 * The tests will create BAI and CRAI indices of the CRAM file beforehand.
 *
 * Partially consolidates duplicate code from {@link CRAMFileBAIIndexTest} and {@link CRAMFileCRAIIndexTest}
 */
public class CRAMFileIndexTest extends HtsjdkTest {
    private final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");

    private final int nofReads = 10000 ;
    private final int nofReadsPerContainer = 1000 ;
    private final int nofUnmappedReads = 279 ;
    private final int nofMappedReads = 9721;

    private ReferenceSource source;
    private byte[] cramBytes;
    private File tmpCramFile;

    private File tmpBaiFile;
    private byte[] baiBytes;

    private File tmpCraiFile;
    private byte[] craiBytes;


    @BeforeTest
    public void prepare() throws IOException {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
        source = new ReferenceSource(new FakeReferenceSequenceFile(
                SamReaderFactory.makeDefault().getFileHeader(BAM_FILE).getSequenceDictionary().getSequences()));

        cramBytes = cramFromBAM(BAM_FILE, source);

        tmpCramFile = File.createTempFile(BAM_FILE.getName(), ".cram") ;
        tmpCramFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tmpCramFile);
        fos.write(cramBytes);
        fos.close();

        tmpBaiFile = new File (tmpCramFile.getAbsolutePath() + ".bai");
        tmpBaiFile.deleteOnExit();
        CRAMBAIIndexer.createIndex(new SeekableFileStream(tmpCramFile), tmpBaiFile, null, ValidationStringency.STRICT);
        baiBytes = readFile(tmpBaiFile);

        tmpCraiFile = new File (tmpCramFile.getAbsolutePath() + ".crai");
        tmpCraiFile.deleteOnExit();
        FileOutputStream fios = new FileOutputStream(tmpCraiFile);
        CRAMCRAIIndexer.writeIndex(new SeekableFileStream(tmpCramFile), fios);
        craiBytes = readFile(tmpCraiFile);
    }

    @DataProvider(name = "intervalQueryTestCases")
    private Object[][] intervalQueryTestCases() throws IOException {
        final QueryInterval[] query = new QueryInterval[]{
                new QueryInterval(0, 1519, 1520),
                new QueryInterval(1, 470535, 470536)
        };

        return new Object[][] {
                { getQueryIterator(baiBytes, query, false) },
                { getQueryIterator(baiBytes, query, true) },
                { getQueryIterator(craiBytes, query, false) },
                { getQueryIterator(craiBytes, query, true) },
        };
    }

    @Test(dataProvider = "intervalQueryTestCases")
    public void testQueryInterval(final CloseableIterator<SAMRecord> iterator) {
        Assert.assertTrue(iterator.hasNext());
        SAMRecord r1 = iterator.next();
        Assert.assertEquals(r1.getReadName(), "3968040");

        Assert.assertTrue(iterator.hasNext());
        SAMRecord r2 = iterator.next();
        Assert.assertEquals(r2.getReadName(), "140419");

        Assert.assertFalse(iterator.hasNext());
        iterator.close();
    }

    // TODO: try-with-resources causes this to fail
    private CloseableIterator<SAMRecord> getQueryIterator(final byte[] indexBytes,
                                                          final QueryInterval[] query,
                                                          final boolean withFilePointers) throws IOException {
        final InputStream fileStream = new ByteArraySeekableStream(cramBytes);
        final SeekableStream indexStream = new ByteArraySeekableStream(indexBytes);
        final CRAMFileReader reader = new CRAMFileReader(fileStream, indexStream, source, ValidationStringency.SILENT);

        if (withFilePointers) {
            final BAMFileSpan fileSpan = BAMFileReader.getFileSpan(query, reader.getIndex());
            return reader.createIndexIterator(query, false, fileSpan.toCoordinateArray());
        } else {
            return reader.query(query, false);
        }
    }

    @Test
    public void testMappedReadsBAI() throws IOException {
        // TODO: why SILENT?
        testMappedReads(baiBytes, ValidationStringency.SILENT);
    }

    @Test
    public void testMappedReadsCRAI() throws IOException {
        testMappedReads(craiBytes, ValidationStringency.STRICT);
    }

    private void testMappedReads(final byte[] indexBytes, final ValidationStringency vs) throws IOException {

        try (final SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
             final SAMRecordIterator samRecordIterator = samReader.iterator();
             final CRAMFileReader reader = new CRAMFileReader(
                     new ByteArraySeekableStream(cramBytes),
                     new ByteArraySeekableStream(indexBytes),
                     source,
                     vs)) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);

            int counter = 0;
            while (samRecordIterator.hasNext()) {
                final SAMRecord samRecord = samRecordIterator.next();
                if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    break;
                }
                if (counter++ % 100 > 1) { // test only 1st and 2nd in every 100 to speed the test up:
                    continue;
                }

                assertMatchingAlignment(reader, samRecord, counter);
            }
            Assert.assertEquals(counter, nofMappedReads);
        }
    }

    // TODO: try-with-resources causes this to fail
    private void assertMatchingAlignment(final CRAMFileReader reader,
                                         final SAMRecord samRecord,
                                         final int counter) {
        final CloseableIterator<SAMRecord> cramIterator = reader.queryAlignmentStart(
                samRecord.getReferenceName(),
                samRecord.getAlignmentStart());

        final String sam1 = samRecord.getSAMString();
        Assert.assertTrue(cramIterator.hasNext(), counter + ": " + sam1);
        final SAMRecord cramRecord = cramIterator.next();
        final String sam2 = cramRecord.getSAMString();
        Assert.assertEquals(samRecord.getReferenceName(), cramRecord.getReferenceName(), sam1 + sam2);

        // default 'overlap' is true, so test records intersect the query:
        Assert.assertTrue(CoordMath.overlaps(
                cramRecord.getAlignmentStart(),
                cramRecord.getAlignmentEnd(),
                samRecord.getAlignmentStart(),
                samRecord.getAlignmentEnd()),
                sam1 + sam2);
    }

    // TODO: testing CRAI unmapped fails when I move it here.  why?

    private static byte[] readFile(final File file) throws IOException {
        try (final FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            IOUtil.copyStream(fis, baos);
            return baos.toByteArray();
        }
    }

    private byte[] cramFromBAM(final File bamFile, final ReferenceSource source) throws IOException {
        // to reduce granularity let's use this hacky approach:
        int previousDefaultPerSlice = CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE;
        CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE = nofReadsPerContainer;

        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final SamReader reader = SamReaderFactory.makeDefault().open(bamFile);
             final SAMRecordIterator iterator = reader.iterator();
             final CRAMFileWriter writer = new CRAMFileWriter(baos, source, reader.getFileHeader(), bamFile.getName())) {

            while (iterator.hasNext()) {
                SAMRecord record = iterator.next();
                writer.addAlignment(record);
            }
            return baos.toByteArray();
        } finally {
            // failing to reset this can cause unrelated tests to fail if this test fails
            CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE = previousDefaultPerSlice;
        }
    }
}
