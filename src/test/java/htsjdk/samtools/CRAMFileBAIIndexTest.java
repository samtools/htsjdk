package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.BAIEntry;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * A collection of tests for CRAM BAI index write/read that use BAMFileIndexTest/index_test.bam file as the
 * source of the test data. The test will create a BAI index of the cram file before hand. The scan* tests
 * check that for every records in the BAM file the query returns the same records from the CRAM file.
 */
public class CRAMFileBAIIndexTest extends HtsjdkTest {
    private static final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private static final int NUMBER_OF_UNMAPPED_READS = 279;
    private static final int NUMBER_OF_MAPPED_READS = 9721;
    private static final int NUMBER_OF_READS = 10000;

    private final static String TEST_QUERY_ALIGNMENT_CONTIG = "chrM";
    private final static int TEST_QUERY_ALIGNMENT_START = 1519;

    // Established by prepare method/@BeforeTest
    // Make a fake reference derived from the sequence dictionary in our input
    //TODO: Note that these tests are REALLY slow because the reference sequences in the dictionary
    // in the test file are large (250 mega-bases), and upper-casing them each time they're retrieved is slowwwwww
    private static final ReferenceSource referenceSource = new ReferenceSource(
            new FakeReferenceSequenceFile(
                    SamReaderFactory.makeDefault().getFileHeader(BAM_FILE).getSequenceDictionary().getSequences()));

    @DataProvider(name="filesWithContainerAndSlicePartitioningVariations")
    public Object[][] getFilesWithContainerAndSlicePartitioningVariations() throws IOException {
        return new Object[][] {
                // in order to set reads/slice to a small number, we must do the same for minimumSingleReferenceSliceSize
                //{ getCRAMFileForBAMFile(BAM_FILE, referenceSource, new CRAMEncodingStrategy()) },
                { getCRAMFileForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(100)) },
                { getCRAMFileForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(150).setReadsPerSlice(150)) },
                { getCRAMFileForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(200).setReadsPerSlice(200)) },
                { getCRAMFileForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(300).setReadsPerSlice(300)) },
        };
    }

    @DataProvider(name="bytesWithContainerAndSlicePartitioningVariations")
    public Object[][] getBytesWithContainerAndSlicePartitioningVariations() throws IOException {
        return new Object[][] {
                // in order to set reads/slice to a small number, we must do the same for minimumSingleReferenceSliceSize
                //{ getCRAMBytesForBAMFile(BAM_FILE, referenceSource, new CRAMEncodingStrategy()) },
                { getCRAMBytesForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(100).setReadsPerSlice(100)) },
                { getCRAMBytesForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(150).setReadsPerSlice(150)) },
                { getCRAMBytesForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(200).setReadsPerSlice(200)) },
                { getCRAMBytesForBAMFile(BAM_FILE, referenceSource,
                        new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(300).setReadsPerSlice(300)) },
        };
    }

    // Mixes testing queryAlignmentStart with each CRAMFileReaderConstructor
    // Separate into individual tests
    @Test(dataProvider = "filesWithContainerAndSlicePartitioningVariations")
    public void testConstructors(final File cramFile) throws IOException {
        final File baiFile = getBAIFileForCRAMFile(cramFile);
        try (final CRAMFileReader reader = new CRAMFileReader(
                cramFile,
                baiFile,
                referenceSource,
                ValidationStringency.SILENT)) {
            try (final CloseableIterator<SAMRecord> iterator =
                         reader.queryAlignmentStart(TEST_QUERY_ALIGNMENT_CONTIG, TEST_QUERY_ALIGNMENT_START)) {
                Assert.assertTrue(iterator.hasNext());
                final SAMRecord record = iterator.next();
                Assert.assertEquals(record.getReferenceName(), TEST_QUERY_ALIGNMENT_CONTIG);
                Assert.assertEquals(record.getAlignmentStart(), TEST_QUERY_ALIGNMENT_START);
            }
        }

        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(cramFile),
                baiFile,
                referenceSource,
                ValidationStringency.SILENT)) {
            try (final CloseableIterator<SAMRecord> iterator =
                         reader.queryAlignmentStart(TEST_QUERY_ALIGNMENT_CONTIG, TEST_QUERY_ALIGNMENT_START)) {
                Assert.assertTrue(iterator.hasNext());
                final SAMRecord record = iterator.next();
                Assert.assertEquals(record.getReferenceName(), TEST_QUERY_ALIGNMENT_CONTIG);
                Assert.assertEquals(record.getAlignmentStart(), TEST_QUERY_ALIGNMENT_START);
            }
        }

        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(cramFile),
                new SeekableFileStream(baiFile),
                referenceSource, ValidationStringency.SILENT)) {
            try (final CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart(
                    TEST_QUERY_ALIGNMENT_CONTIG,
                    TEST_QUERY_ALIGNMENT_START)) {
                Assert.assertTrue(iterator.hasNext());
                final SAMRecord record = iterator.next();
                Assert.assertEquals(record.getReferenceName(), TEST_QUERY_ALIGNMENT_CONTIG);
                Assert.assertEquals(record.getAlignmentStart(), TEST_QUERY_ALIGNMENT_START);
            }
        }

        try (final CRAMFileReader reader = new CRAMFileReader(
                new SeekableFileStream(cramFile),
                (File)null,
                referenceSource,
                ValidationStringency.SILENT)) {
            Assert.expectThrows(SAMException.class,
                    () ->reader.queryAlignmentStart(TEST_QUERY_ALIGNMENT_CONTIG, TEST_QUERY_ALIGNMENT_START));
        }

        try (final CRAMFileReader reader  = new CRAMFileReader(
                new SeekableFileStream(cramFile),
                (SeekableFileStream)null,
                referenceSource,
                ValidationStringency.SILENT)) {
            Assert.expectThrows(SAMException.class,
                    () -> reader.queryAlignmentStart(TEST_QUERY_ALIGNMENT_CONTIG, TEST_QUERY_ALIGNMENT_START));
        }
    }

    @Test(dataProvider = "bytesWithContainerAndSlicePartitioningVariations")
    public void testCompareMappedReadsQueryAlignmentStart(final byte[] cramBytes) throws IOException {
        try (final SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE)) {
            final SAMRecordIterator samRecordIterator = samReader.iterator();
            try (final CRAMFileReader cramFileReader = new CRAMFileReader(
                    new ByteArraySeekableStream(cramBytes),
                    new ByteArraySeekableStream(getBAIBytesForCRAMBytes(cramBytes)),
                    referenceSource,
                    ValidationStringency.SILENT)) {
                int counter = 0;
                while (samRecordIterator.hasNext()) {
                    SAMRecord samRecord = samRecordIterator.next();
                    if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                        break;
                    }
                    // test only 1st and 2nd in every 100 to speed the test up:
                    if (counter++ %100 > 1) {
                        continue;
                    }
                    final String s1 = samRecord.getSAMString();

                    //TODO: closing this iterator after each query causes the test to fail after the first
                    // iteration the underlying reader stream is closed as well
                    CloseableIterator<SAMRecord> cramIterator =
                                 cramFileReader.queryAlignmentStart(
                                         samRecord.getReferenceName(),
                                         samRecord.getAlignmentStart());
                    Assert.assertTrue(cramIterator.hasNext(), counter + ": " + s1);
                    final SAMRecord cramRecord = cramIterator.next();
                    final String s2 = cramRecord.getSAMString();
                    Assert.assertEquals(samRecord.getReferenceName(), cramRecord.getReferenceName(), s1 + s2);
                    // default 'overlap' is true, so test records intersect the query:
                    Assert.assertTrue(
                            CoordMath.overlaps(
                                    cramRecord.getAlignmentStart(),
                                    cramRecord.getAlignmentEnd(),
                                    samRecord.getAlignmentStart(),
                                    samRecord.getAlignmentEnd()), s1 + s2);
                }
                Assert.assertEquals(counter, NUMBER_OF_MAPPED_READS);
            }
        }
    }

    @Test(dataProvider = "bytesWithContainerAndSlicePartitioningVariations")
    public void testIteratorFromEntireFileSpans(final byte[] cramBytes) throws IOException {
        try (final CRAMFileReader cramFileReader = new CRAMFileReader(
                new ByteArraySeekableStream(cramBytes),
                new ByteArraySeekableStream(getBAIBytesForCRAMBytes(cramBytes)),
                referenceSource, ValidationStringency.SILENT)) {
            final SAMFileSpan allContainerFileSpans = cramFileReader.getFilePointerSpanningReads();
            try (final CloseableIterator<SAMRecord> cramIterator = cramFileReader.getIterator(allContainerFileSpans)) {
                int counter = 0;
                while (cramIterator.hasNext()) {
                    cramIterator.next();
                    counter++;
                }
                Assert.assertEquals(counter, NUMBER_OF_READS);
            }
        }
    }

    @Test
    public void testIteratorFromSecondContainerFileSpan() throws IOException {
        final byte[] cramBytes = getCRAMBytesForBAMFile(BAM_FILE, referenceSource, new CRAMEncodingStrategy().setReadsPerSlice(1000));

        try (final CramContainerIterator it = new CramContainerIterator(new ByteArrayInputStream(cramBytes))) {
            Assert.assertTrue(it.hasNext());
            Assert.assertNotNull(it.next());
            Assert.assertTrue(it.hasNext());
            final Container secondContainer = it.next();
            Assert.assertNotNull(secondContainer);
            final List<BAIEntry> baiEntries = secondContainer.getBAIEntries(new CompressorCache());
            Assert.assertEquals(baiEntries.size(), 2);
            final BAIEntry baiEntry = baiEntries.get(0);

            try (CRAMFileReader reader = new CRAMFileReader(
                    new ByteArraySeekableStream(cramBytes),
                    new ByteArraySeekableStream(getBAIBytesForCRAMBytes(cramBytes)),
                    referenceSource,
                    ValidationStringency.SILENT)) {

                final BAMIndex index = reader.getIndex();
                final SAMFileSpan spanOfSecondContainer = index.getSpanOverlapping(
                        baiEntry.getReferenceContext().getReferenceContextID(),
                        baiEntry.getAlignmentStart(),
                        baiEntry.getAlignmentStart() + baiEntry.getAlignmentSpan());
                Assert.assertNotNull(spanOfSecondContainer);
                Assert.assertFalse(spanOfSecondContainer.isEmpty());
                Assert.assertTrue(spanOfSecondContainer instanceof BAMFileSpan);

                try (final CloseableIterator<SAMRecord> iterator = reader.getIterator(spanOfSecondContainer)) {
                    Assert.assertTrue(iterator.hasNext());
                    int counter = 0;
                    boolean matchFound = false;
                    while (iterator.hasNext()) {
                        final SAMRecord record = iterator.next();
                        if (record.getReferenceIndex() == baiEntry.getReferenceContext().getReferenceContextID()) {
                            final boolean overlaps = CoordMath.overlaps(
                                    record.getAlignmentStart(),
                                    record.getAlignmentEnd(),
                                    baiEntry.getAlignmentStart(),
                                    baiEntry.getAlignmentStart() + baiEntry.getAlignmentSpan());
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

    @Test(dataProvider = "bytesWithContainerAndSlicePartitioningVariations")
    public void testQueryInterval(final byte[] cramBytes) throws IOException {
        final QueryInterval[] query = new QueryInterval[]{
                new QueryInterval(0, TEST_QUERY_ALIGNMENT_START, TEST_QUERY_ALIGNMENT_START + 1),
                new QueryInterval(1, 470535, 470536)};
        try (final CRAMFileReader reader = new CRAMFileReader(
                new ByteArraySeekableStream(cramBytes),
                new ByteArraySeekableStream(getBAIBytesForCRAMBytes(cramBytes)),
                referenceSource, ValidationStringency.SILENT);
             final CloseableIterator<SAMRecord> iterator = reader.query(query, false)) {
                Assert.assertTrue(iterator.hasNext());
                final SAMRecord r1 = iterator.next();
                Assert.assertEquals(r1.getReadName(), "3968040");
                Assert.assertTrue(iterator.hasNext());
                final SAMRecord r2 = iterator.next();
                Assert.assertEquals(r2.getReadName(), "140419");
                Assert.assertFalse(iterator.hasNext());
        }
    }

    @Test(dataProvider = "bytesWithContainerAndSlicePartitioningVariations")
    public void testCompareUnmappedReads(final byte[] cramBytes) throws IOException {
        try (final SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
             final CRAMFileReader reader = new CRAMFileReader(
                new ByteArraySeekableStream(cramBytes),
                new ByteArraySeekableStream(getBAIBytesForCRAMBytes(cramBytes)),
                     referenceSource, ValidationStringency.SILENT)) {
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
            Assert.assertEquals(counter, NUMBER_OF_UNMAPPED_READS);
        }
    }

    private static byte[] getBAIBytesForCRAMBytes(final byte[] cramBytes) throws IOException {
        final File indexFile = Files.createTempFile("cramBytes", ".bai").toFile();
        indexFile.deleteOnExit();
        CRAMBAIIndexer.createIndex(new ByteArraySeekableStream(cramBytes), indexFile, null, ValidationStringency.STRICT);
        return bytesFromFile(indexFile);
    }

    private static File getCRAMFileForBAMFile(
            final File bamFile,
            final CRAMReferenceSource referenceSource,
            final CRAMEncodingStrategy cramEncodingStrategy) throws IOException {
        final byte [] cramBytes = getCRAMBytesForBAMFile(bamFile, referenceSource, cramEncodingStrategy);
        final File cramFile = File.createTempFile(bamFile.getName(), ".cram") ;
        cramFile.deleteOnExit();
        try (final FileOutputStream fos = new FileOutputStream(cramFile)) {
            fos.write(cramBytes);
        }
        return cramFile;
    }

    private static File getBAIFileForCRAMFile(final File cramFile) throws IOException {
        final File baiIndexFile = new File(cramFile.getAbsolutePath() + ".bai");
        baiIndexFile.deleteOnExit();
        // TODO: its silly to use a stream constructor when we have a cram file...
        CRAMBAIIndexer.createIndex(new SeekableFileStream(cramFile), baiIndexFile, null, ValidationStringency.STRICT);
        return baiIndexFile;
    }

    //TODO: these are duplicated in CRAMFileCRAIIndexTest
    private static byte[] bytesFromFile(final File file) throws IOException {
        try (final FileInputStream fis = new FileInputStream(file)) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtil.copyStream(fis, baos);
            return baos.toByteArray();
        }
    }

    private static byte[] getCRAMBytesForBAMFile(
            final File bamFile,
            final CRAMReferenceSource source,
            final CRAMEncodingStrategy encodingStrategy) throws IOException {
        try (final SamReader samReader = SamReaderFactory.makeDefault().open(bamFile);
             final SAMRecordIterator samIterator = samReader.iterator();
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (final CRAMFileWriter cramWriter = new CRAMFileWriter(
                    encodingStrategy,
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

}
