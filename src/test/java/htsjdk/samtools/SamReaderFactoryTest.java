package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableHTTPStream;
import htsjdk.samtools.util.Iterables;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.StopWatch;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SamReaderFactoryTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    private static final Log LOG = Log.getInstance(SamReaderFactoryTest.class);

    @Test(dataProvider = "variousFormatReaderTestCases")
    public void variousFormatReaderTest(final String inputFile) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);
        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        for (final SAMRecord ignored : reader) {
        }
        reader.close();
    }

    private int countRecordsInQueryInterval(final SamReader reader, final QueryInterval query) {
        final SAMRecordIterator iter = reader.queryOverlapping(new QueryInterval[] { query });
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        iter.close();
        return count;
    }

    // See https://github.com/samtools/htsjdk/issues/76
    @Test(dataProvider = "queryIntervalIssue76TestCases")
    public void queryIntervalIssue76(final String sequenceName, final int start, final int end, final int expectedCount) throws IOException {
        final File input = new File(TEST_DATA_DIR, "issue76.bam");
        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        final QueryInterval interval = new QueryInterval(reader.getFileHeader().getSequence(sequenceName).getSequenceIndex(), start, end);
        Assert.assertEquals(countRecordsInQueryInterval(reader, interval), expectedCount);
        reader.close();
    }

    @DataProvider(name = "queryIntervalIssue76TestCases")
    public Object[][] queryIntervalIssue76TestCases() {
        return new Object[][]{
                {"1", 11966, 11966, 2},
                {"1", 11966, 11967, 2},
                {"1", 11967, 11967, 1}
        };
    }

    @DataProvider(name = "variousFormatReaderTestCases")
    public Object[][] variousFormatReaderTestCases() {
        return new Object[][]{
                {"block_compressed.sam.gz"},
                {"uncompressed.sam"},
                {"compressed.sam.gz"},
                {"compressed.bam"},
        };
    }

    // Tests for the SAMRecordFactory usage
    class SAMRecordFactoryTester extends DefaultSAMRecordFactory {
        int samRecordsCreated;
        int bamRecordsCreated;

        public SAMRecord createSAMRecord(final SAMFileHeader header) {
            ++samRecordsCreated;
            return super.createSAMRecord(header);
        }

        public BAMRecord createBAMRecord(final SAMFileHeader header, final int referenceSequenceIndex, final int alignmentStart, final short readNameLength, final short mappingQuality, final int indexingBin, final int cigarLen, final int flags, final int readLen, final int mateReferenceSequenceIndex, final int mateAlignmentStart, final int insertSize, final byte[] variableLengthBlock) {
            ++bamRecordsCreated;
            return super.createBAMRecord(header, referenceSequenceIndex, alignmentStart, readNameLength, mappingQuality, indexingBin, cigarLen, flags, readLen, mateReferenceSequenceIndex, mateAlignmentStart, insertSize, variableLengthBlock);
        }
    }

    @Test(dataProvider = "variousFormatReaderTestCases")
    public void samRecordFactoryTest(final String inputFile) throws IOException {
        final File input = new File(TEST_DATA_DIR, inputFile);

        final SAMRecordFactoryTester recordFactory = new SAMRecordFactoryTester();
        final SamReaderFactory readerFactory = SamReaderFactory.makeDefault().samRecordFactory(recordFactory);
        final SamReader reader = readerFactory.open(input);

        int i = 0;
        for (final SAMRecord ignored : reader) {
            ++i;
        }
        reader.close();

        Assert.assertTrue(i > 0);
        if (inputFile.endsWith(".sam") || inputFile.endsWith(".sam.gz")) Assert.assertEquals(recordFactory.samRecordsCreated, i);
        else if (inputFile.endsWith(".bam")) Assert.assertEquals(recordFactory.bamRecordsCreated, i);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void samRecordFactoryNullHeaderBAMTest() {
        final SAMRecordFactory recordFactory = new DefaultSAMRecordFactory();
        recordFactory.createBAMRecord(
                null, // null header
                0,
                0,
                (short) 0,
                (short) 0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null);
    }


    /**
     * Unit tests for asserting all permutations of data and index sources read the same records and header.
     */
    final File localBam = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    final File localBamIndex = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.bai");

    final URL bamUrl, bamIndexUrl;

    {
        try {
            bamUrl = new URL("http://www.broadinstitute.org/~picard/testdata/index_test.bam");
            bamIndexUrl = new URL("http://www.broadinstitute.org/~picard/testdata/index_test.bam.bai");
        } catch (final MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @DataProvider
    public Object[][] composeAllPermutationsOfSamInputResource() {
        final List<SamInputResource> sources = new ArrayList<SamInputResource>();
        for (final InputResource.Type dataType : InputResource.Type.values()) {
            if (dataType.equals(InputResource.Type.SRA_ACCESSION))
                continue;

            sources.add(new SamInputResource(composeInputResourceForType(dataType, false)));
            for (final InputResource.Type indexType : InputResource.Type.values()) {
                if (indexType.equals(InputResource.Type.SRA_ACCESSION))
                    continue;

                sources.add(new SamInputResource(
                        composeInputResourceForType(dataType, false),
                        composeInputResourceForType(indexType, true)
                ));
            }
        }
        final Object[][] data = new Object[sources.size()][];
        for (final SamInputResource source : sources) {
            data[sources.indexOf(source)] = new Object[]{source};
        }

        return data;
    }

    private InputResource composeInputResourceForType(final InputResource.Type type, final boolean forIndex) {
        final File f = forIndex ? localBamIndex : localBam;
        final URL url = forIndex ? bamIndexUrl : bamUrl;
        switch (type) {
            case FILE:
                return new FileInputResource(f);
            case URL:
                return new UrlInputResource(url);
            case SEEKABLE_STREAM:
                return new SeekableStreamInputResource(new SeekableHTTPStream(url));
            case INPUT_STREAM:
                try {
                    return new InputStreamInputResource(new FileInputStream(f));
                } catch (final FileNotFoundException e) {
                    throw new RuntimeIOException(e);
                }
            default:
                throw new IllegalStateException();
        }
    }

    final Set<SAMFileHeader> observedHeaders = new HashSet<SAMFileHeader>();
    final Set<List<SAMRecord>> observedRecordOrdering = new HashSet<List<SAMRecord>>();

    @Test(dataProvider = "composeAllPermutationsOfSamInputResource")
    public void exhaustInputResourcePermutation(final SamInputResource resource) throws IOException {
        final SamReader reader = SamReaderFactory.makeDefault().open(resource);
        LOG.info(String.format("Reading from %s ...", resource));
        final List<SAMRecord> slurped = Iterables.slurp(reader);
        final SAMFileHeader fileHeader = reader.getFileHeader();
        reader.hasIndex();
        reader.indexing().hasBrowseableIndex();
        reader.close();
        
        /* Ensure all tests have read the same records in the same order or, if this is the first test, set it as the template. */
        observedHeaders.add(fileHeader);
        observedRecordOrdering.add(slurped);
        Assert.assertEquals(observedHeaders.size(), 1, "read different headers than other testcases");
        Assert.assertEquals(observedRecordOrdering.size(), 1, "read different records than other testcases");
    }


    final Set<List<SAMRecord>> observedRecordOrdering1 = new HashSet<List<SAMRecord>>();
    final Set<List<SAMRecord>> observedRecordOrdering3 = new HashSet<List<SAMRecord>>();
    final Set<List<SAMRecord>> observedRecordOrdering20 = new HashSet<List<SAMRecord>>();

    @Test(dataProvider = "composeAllPermutationsOfSamInputResource")
    public void queryInputResourcePermutation(final SamInputResource resource) throws IOException {
        final SamReader reader = SamReaderFactory.makeDefault().open(resource);
        LOG.info(String.format("Query from %s ...", resource));
        if (reader.hasIndex()) {
            final StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            final SAMRecordIterator q1 = reader.query("chr1", 500000, 100000000, true);
            observedRecordOrdering1.add(Iterables.slurp(q1));
            q1.close();
            final SAMRecordIterator q20 = reader.query("chr20", 1, 1000000, true);
            observedRecordOrdering20.add(Iterables.slurp(q20));
            q20.close();
            final SAMRecordIterator q3 = reader.query("chr3", 1, 10000000, true);
            observedRecordOrdering3.add(Iterables.slurp(q3));
            q3.close();
            stopWatch.stop();
            LOG.info(String.format("Finished queries in %sms", stopWatch.getElapsedTime()));

            Assert.assertEquals(observedRecordOrdering1.size(), 1, "read different records for chromosome 1");
            Assert.assertEquals(observedRecordOrdering20.size(), 1, "read different records for chromosome 20");
            Assert.assertEquals(observedRecordOrdering3.size(), 1, "read different records for chromosome 3");
        } else if (resource.indexMaybe() != null) {
            LOG.warn("Resource has an index source, but is not indexed: " + resource);
        } else {
            LOG.info("Skipping query operation: no index.");
        }
        reader.close();
    }
    
    @Test
    public void customReaderFactoryTest() throws IOException {
        try {
          CustomReaderFactory.setInstance(new CustomReaderFactory(
              "https://www.googleapis.com/genomics/v1beta/reads/," +
              "htsjdk.samtools.SamReaderFactoryTest$TestReaderFactory"));
          final SamReader reader = SamReaderFactory.makeDefault().open(
              SamInputResource.of(
              "https://www.googleapis.com/genomics/v1beta/reads/?uncompressed.sam"));
          int i = 0;
          for (@SuppressWarnings("unused") final SAMRecord ignored : reader) {
              ++i;
          }
          reader.close();
  
          Assert.assertTrue(i > 0);
        } finally {
          CustomReaderFactory.resetToDefaultInstance();
        }
    }
    
    public static class TestReaderFactory implements CustomReaderFactory.ICustomReaderFactory {
      @Override
      public SamReader open(URL url) {
        final File file = new File(TEST_DATA_DIR, url.getQuery());
        LOG.info("Opening customr reader for " + file.toString());
        return SamReaderFactory.makeDefault().open(file);
      }
    }
    
    @Test
    public void inputResourceFromStringTest() throws IOException {
      Assert.assertEquals(SamInputResource.of("http://test.url").data().type(),
          InputResource.Type.URL);
      Assert.assertEquals(SamInputResource.of("https://test.url").data().type(),
          InputResource.Type.URL);
      Assert.assertEquals(SamInputResource.of("ftp://test.url").data().type(),
          InputResource.Type.URL);
      Assert.assertEquals(SamInputResource.of("/a/b/c").data().type(),
          InputResource.Type.FILE);
    }
}
