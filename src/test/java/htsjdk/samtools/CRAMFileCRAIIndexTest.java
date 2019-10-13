package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.BAIEntry;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.cram.structure.CompressorCache;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CoordMath;
import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Iterator;
import java.util.List;

/**
 * Companion tests for the ones in CRAMFileBAIIndexTest, but run against a .bai
 * that has been converted from a .crai.
 *
 * A collection of tests for CRAM CRAI index write/read that use BAMFileIndexTest/index_test.bam
 * file as the source of the test data. The scan* tests check that for every records in the
 * CRAM file the query returns the same records from the CRAM file.
 */
@Test(singleThreaded = true)
public class CRAMFileCRAIIndexTest extends HtsjdkTest {
    private final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");

    private final int nofReads = 10000 ;
    private final int nofReadsPerContainer = 1000 ;
    private final int nofUnmappedReads = 279 ;
    private final int nofMappedReads = 9721;

    private File tmpCramFile;
    private File tmpCraiFile;
    private byte[] cramBytes;
    private byte[] craiBytes;
    private ReferenceSource source;

    @Test
    public void testFileFileConstructor() throws IOException {
        try (final CRAMFileReader reader = new CRAMFileReader(
                tmpCramFile,
                tmpCraiFile,
                source,
                ValidationStringency.STRICT);
            final CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1519)) {

            Assert.assertTrue(iterator.hasNext());
            final SAMRecord record = iterator.next();
            Assert.assertEquals(record.getReferenceName(), "chrM");
            Assert.assertEquals(record.getAlignmentStart(), 1519);
        }
    }

    @Test
    public void testStreamFileConstructor() throws IOException {
        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(tmpCramFile),
                tmpCraiFile,
                source,
                ValidationStringency.STRICT);
             final CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1519)) {
            Assert.assertTrue(iterator.hasNext());
            final SAMRecord record = iterator.next();
            Assert.assertEquals(record.getReferenceName(), "chrM");
            Assert.assertEquals(record.getAlignmentStart(), 1519);
        }
    }

    @Test
    public void testStreamStreamConstructor() throws IOException {
        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(tmpCramFile),
                new SeekableFileStream(tmpCraiFile),
                source,
                ValidationStringency.STRICT);
            final CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1519)) {
            Assert.assertTrue(iterator.hasNext());
            SAMRecord record = iterator.next();

            Assert.assertEquals(record.getReferenceName(), "chrM");
            Assert.assertEquals(record.getAlignmentStart(), 1519);
        }
    }

    @Test(expectedExceptions = SAMException.class)
    public void testFileFileConstructorNoIndex () throws IOException {
        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(tmpCramFile),
                (File) null,
                source,
                ValidationStringency.STRICT)) {
            reader.queryAlignmentStart("chrM", 1519);
        }
    }

    @Test(expectedExceptions = SAMException.class)
    public void testStreamStreamConstructorNoIndex() throws IOException {
        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(tmpCramFile),
                (SeekableFileStream) null,
                source,
                ValidationStringency.STRICT)) {
            reader.queryAlignmentStart("chrM", 1519);
        }
    }

    @Test
    public void testMappedReads() throws IOException {

        try (final SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
             final SAMRecordIterator samRecordIterator = samReader.iterator()) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            try (final CRAMFileReader cramReader = new CRAMFileReader(
                    new ByteArraySeekableStream(cramBytes),
                    new ByteArraySeekableStream(craiBytes),
                    source,
                    ValidationStringency.STRICT)) {
                int counter = 0;
                while (samRecordIterator.hasNext()) {
                    final SAMRecord samRecord = samRecordIterator.next();
                    if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                        break;
                    }
                    if (counter++ % 100 > 1) { // test only 1st and 2nd in every 100 to speed the test up:
                        continue;
                    }
                    final String sam1 = samRecord.getSAMString();

                    final CloseableIterator<SAMRecord> iterator = cramReader.queryAlignmentStart(
                            samRecord.getReferenceName(),
                            samRecord.getAlignmentStart());

                    Assert.assertTrue(iterator.hasNext(), counter + ": " + sam1);
                    final SAMRecord cramRecord = iterator.next();
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
                Assert.assertEquals(counter, nofMappedReads);
            }
        }
    }

    @Test
    public void testQueryUnmapped() throws IOException {
        try (final SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
             final SAMRecordIterator unmappedSamIterator = samReader.queryUnmapped();
             final CRAMFileReader reader = new CRAMFileReader(
                    new ByteArraySeekableStream(cramBytes),
                    new ByteArraySeekableStream(craiBytes),
                    source,
                    ValidationStringency.STRICT);
             final CloseableIterator<SAMRecord> unmappedCramIterator = reader.queryUnmapped()) {
                int counter = 0;
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
        }
    }

    @Test
    public void testIteratorConstructor() throws IOException {
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/auxf#values.3.0.cram");
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/auxf.fa");
        ReferenceSource refSource = new ReferenceSource(refFile);

        long[] boundaries = new long[] {0, (CRAMFile.length() - 1) << 16};
        try (final CRAMIterator iterator = new CRAMIterator(
                new SeekableFileStream(CRAMFile),
                refSource,
                ValidationStringency.STRICT,
                null,
                boundaries)) {
            long count = getIteratorCount(iterator);
            Assert.assertEquals(count, 2);
        }
    }

    @Test
    public void testIteratorWholeFileSpan() throws IOException {
        try (CRAMFileReader reader = new CRAMFileReader(
                new ByteArraySeekableStream(cramBytes),
                new ByteArraySeekableStream(craiBytes),
                source,
                ValidationStringency.STRICT)) {
            final SAMFileSpan allContainers = reader.getFilePointerSpanningReads();
            try (final CloseableIterator<SAMRecord> iterator = reader.getIterator(allContainers)) {
                Assert.assertTrue(iterator.hasNext());
                long count = getIteratorCount(iterator);
                Assert.assertEquals(count, nofReads);
            }
        }
    }

    @Test
    public void testIteratorSecondContainerSpan() throws IOException {
        try (final CramContainerIterator it = new CramContainerIterator(new ByteArrayInputStream(cramBytes))) {
            Assert.assertTrue(it.hasNext());
            Assert.assertNotNull(it.next());
            Assert.assertTrue(it.hasNext());
            final Container secondContainer = it.next();
            Assert.assertNotNull(secondContainer);

            final List<CRAIEntry> craiEntries = secondContainer.getCRAIEntries(new CompressorCache());
            Assert.assertEquals(craiEntries.size(), 2);
            final CRAIEntry secondContainerCRAIEntry =  craiEntries.get(0);

            try (final CRAMFileReader cramFileReader = new CRAMFileReader(
                    new ByteArraySeekableStream(cramBytes),
                    new ByteArraySeekableStream(craiBytes),
                    source,
                    ValidationStringency.STRICT)) {
                final BAMIndex index = cramFileReader.getIndex();
                final SAMFileSpan spanOfSecondContainer = index.getSpanOverlapping(
                        secondContainerCRAIEntry.getSequenceId(),
                        secondContainerCRAIEntry.getAlignmentStart(),
                        secondContainerCRAIEntry.getAlignmentStart() + secondContainerCRAIEntry.getAlignmentSpan());
                Assert.assertNotNull(spanOfSecondContainer);
                Assert.assertFalse(spanOfSecondContainer.isEmpty());
                Assert.assertTrue(spanOfSecondContainer instanceof BAMFileSpan);

                try (final CloseableIterator<SAMRecord> iterator = cramFileReader.getIterator(spanOfSecondContainer)) {
                    Assert.assertTrue(iterator.hasNext());
                    int counter = 0;
                    boolean matchFound = false;
                    while (iterator.hasNext()) {
                        final SAMRecord record = iterator.next();
                        if (record.getReferenceIndex() == secondContainerCRAIEntry.getSequenceId()) {
                            boolean overlaps = CoordMath.overlaps(
                                    record.getAlignmentStart(),
                                    record.getAlignmentEnd(),
                                    secondContainerCRAIEntry.getAlignmentStart(),
                                    secondContainerCRAIEntry.getAlignmentStart() + secondContainerCRAIEntry.getAlignmentSpan());
                            if (overlaps) {
                                matchFound = true;
                            }
                        }
                        counter++;
                    }
                    Assert.assertTrue(matchFound);
                    Assert.assertTrue(counter <= new CRAMEncodingStrategy().getReadsPerSlice());
                }
            }
        }
    }

    @Test
    public void testQueryInterval() throws IOException {
        try (CRAMFileReader reader = new CRAMFileReader(
                new ByteArraySeekableStream(cramBytes),
                new ByteArraySeekableStream(craiBytes),
                source,
                ValidationStringency.STRICT)) {
            final QueryInterval[] query = new QueryInterval[] {
                    new QueryInterval(0, 1519, 1520),
                    new QueryInterval(1, 470535, 470536)
            };
            try (final CloseableIterator<SAMRecord> iterator = reader.query(query, false)) {
                Assert.assertTrue(iterator.hasNext());

                SAMRecord r1 = iterator.next();
                Assert.assertEquals(r1.getReadName(), "3968040");

                Assert.assertTrue(iterator.hasNext());
                SAMRecord r2 = iterator.next();
                Assert.assertEquals(r2.getReadName(), "140419");

                Assert.assertFalse(iterator.hasNext());
            }
        }
    }

    @Test
    public void testQueryIntervalWithFilePointers() throws IOException {
        try (final CRAMFileReader reader = new CRAMFileReader(
                new ByteArraySeekableStream(cramBytes),
                new ByteArraySeekableStream(craiBytes),
                source,
                ValidationStringency.STRICT)) {
            final QueryInterval[] query = new QueryInterval[] {
                    new QueryInterval(0, 1519, 1520),
                    new QueryInterval(1, 470535, 470536)
            };
            final BAMFileSpan fileSpan = BAMFileReader.getFileSpan(query, reader.getIndex());
            final CloseableIterator<SAMRecord> iterator = reader.createIndexIterator(query, false, fileSpan.toCoordinateArray());

            Assert.assertTrue(iterator.hasNext());
            SAMRecord r1 = iterator.next();
            Assert.assertEquals(r1.getReadName(), "3968040");

            Assert.assertTrue(iterator.hasNext());
            SAMRecord r2 = iterator.next();
            Assert.assertEquals(r2.getReadName(), "140419");

            Assert.assertFalse(iterator.hasNext());
            iterator.close();
        }
    }

    @BeforeTest
    public void prepare() throws IOException {
        tmpCramFile = File.createTempFile(BAM_FILE.getName(), ".cram") ;
        tmpCramFile.deleteOnExit();
        tmpCraiFile = new File (tmpCramFile.getAbsolutePath() + ".crai");
        tmpCraiFile.deleteOnExit();

        source = new ReferenceSource(
                new FakeReferenceSequenceFile(
                        SamReaderFactory.makeDefault().getFileHeader(BAM_FILE).getSequenceDictionary().getSequences()
                )
        );
        cramBytes = cramBytesFromBAMFile(BAM_FILE, source);

        try (final FileOutputStream fios = new FileOutputStream(tmpCraiFile)) {
            try (final FileOutputStream fos = new FileOutputStream(tmpCramFile)) {
                fos.write(cramBytes);
            }
            CRAMCRAIIndexer.writeIndex(new SeekableFileStream(tmpCramFile), fios);
        }
        craiBytes = bytesFromFile(tmpCraiFile);
    }

    //TODO: these are duplicated in CRAMFileCRAIIndexTest
    private static byte[] bytesFromFile(final File file) throws IOException {
        try (final FileInputStream fis = new FileInputStream(file)) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtil.copyStream(fis, baos);
            return baos.toByteArray();
        }
    }

    private byte[] cramBytesFromBAMFile(final File bamFile, final ReferenceSource source) throws IOException {
        try (final SamReader samReader = SamReaderFactory.makeDefault().open(bamFile);
             final SAMRecordIterator samIterator = samReader.iterator();
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (final CRAMFileWriter cramWriter = new CRAMFileWriter(
                    // to reduce granularity call setRecordsPerSlice
                    // in order to set reads/slice to a small number, we must so the same for minimumSingleReferenceSliceSize
                    new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(nofReadsPerContainer).setReadsPerSlice(nofReadsPerContainer),
                    baos,
                    null,
                    true,
                    source,
                    samReader.getFileHeader(),
                    bamFile.getName())) {
                while (samIterator.hasNext()) {
                    SAMRecord record = samIterator.next();
                    cramWriter.addAlignment(record);
                }
            }
            return baos.toByteArray();
        }
    }

    private long getIteratorCount(Iterator<SAMRecord> it) {
        long count = 0;
        while (it.hasNext()) {
            count++;
            it.next();
        }
        return count;
    }
}
