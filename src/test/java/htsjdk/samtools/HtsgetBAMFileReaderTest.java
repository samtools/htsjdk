package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloseableIterator;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class HtsgetBAMFileReaderTest extends HtsjdkTest {
    public static final String HTSGET_ENDPOINT = "http://127.0.0.1:3000/reads/";
    public static final String LOCAL_PREFIX = "htsjdk_test.";

    private final static URI htsgetBAM = URI.create(HTSGET_ENDPOINT + LOCAL_PREFIX + "index_test.bam");

    private final static File bamFile = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");

    private final static int nofMappedReads = 9721;
    private final static int nofUnmappedReads = 279;
    private final static int noChrMReads = 23;
    private final static int noChrMReadsContained = 9;
    private final static int noChrMReadsOverlapped = 10;

    private static HtsgetBAMFileReader bamFileReaderHtsgetGET;
    private static HtsgetBAMFileReader bamFileReaderHtsgetPOST;
    private static HtsgetBAMFileReader bamFileReaderHtsgetAsync;

    @BeforeTest
    public void init() throws IOException {
        bamFileReaderHtsgetGET = new HtsgetBAMFileReader(htsgetBAM, true, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance(), false);
        bamFileReaderHtsgetGET.setUsingPOST(false);

        bamFileReaderHtsgetAsync = new HtsgetBAMFileReader(htsgetBAM, true, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance(), true);
        bamFileReaderHtsgetAsync.setUsingPOST(false);

        bamFileReaderHtsgetPOST = new HtsgetBAMFileReader(htsgetBAM, true, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance(), false);

        Assert.assertTrue(bamFileReaderHtsgetPOST.isUsingPOST());
        Assert.assertFalse(bamFileReaderHtsgetGET.isUsingPOST());
        Assert.assertFalse(bamFileReaderHtsgetAsync.isUsingPOST());
    }

    @AfterTest
    public void tearDown() {
        bamFileReaderHtsgetGET.close();
        bamFileReaderHtsgetPOST.close();
        bamFileReaderHtsgetAsync.close();
    }

    @DataProvider(name = "readerProvider")
    public Object[][] readerProvider() {
        return new Object[][]{
            {bamFileReaderHtsgetGET},
            {bamFileReaderHtsgetAsync},
            {bamFileReaderHtsgetPOST},
        };
    }

    @Test(dataProvider = "readerProvider")
    public static void testGetHeader(final HtsgetBAMFileReader htsgetReader) {
        final SAMFileHeader expectedHeader = SamReaderFactory.makeDefault().open(bamFile).getFileHeader();
        final SAMFileHeader actualHeader = htsgetReader.getFileHeader();
        Assert.assertEquals(actualHeader, expectedHeader);
    }

    @Test(dataProvider = "readerProvider")
    public static void testQueryMapped(final HtsgetBAMFileReader htsgetReader) throws IOException {
        try (final SamReader samReader = SamReaderFactory.makeDefault().open(bamFile);
             final SAMRecordIterator samRecordIterator = samReader.iterator()) {
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
                final String sam1 = samRecord.getSAMString();

                final CloseableIterator<SAMRecord> iterator = htsgetReader.queryAlignmentStart(
                    samRecord.getReferenceName(),
                    samRecord.getAlignmentStart());

                Assert.assertTrue(iterator.hasNext(), counter + ": " + sam1);
                final SAMRecord bamRecord = iterator.next();
                final String sam2 = bamRecord.getSAMString();
                Assert.assertEquals(samRecord.getReferenceName(), bamRecord.getReferenceName(), sam1 + sam2);

                // default 'overlap' is true, so test records intersect the query:
                Assert.assertTrue(bamRecord.overlaps(samRecord), sam1 + sam2);

                iterator.close();
            }
            Assert.assertEquals(counter, nofMappedReads);
        }
    }

    @Test(dataProvider = "readerProvider")
    public static void testQueryUnmapped(final HtsgetBAMFileReader htsgetReader) throws IOException {
        int counter = 0;
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.queryUnmapped();
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.queryUnmapped()) {
            Assert.assertTrue(htsgetIterator.hasNext());
            while (htsgetIterator.hasNext()) {
                Assert.assertTrue(csiIterator.hasNext());

                final SAMRecord r1 = htsgetIterator.next();
                final SAMRecord r2 = csiIterator.next();
                Assert.assertEquals(r1.getReadName(), r2.getReadName());
                Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

                counter++;
            }
            Assert.assertFalse(csiIterator.hasNext());
            Assert.assertEquals(counter, nofUnmappedReads);
        }
    }

    @Test(dataProvider = "readerProvider")
    public static void testQueryInterval(final HtsgetBAMFileReader htsgetReader) throws IOException {
        final QueryInterval[] query = new QueryInterval[]{new QueryInterval(0, 1519, 1520), new QueryInterval(1, 470535, 470536)};
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.query(query, false);
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.query(query, false)) {

            Assert.assertTrue(htsgetIterator.hasNext());
            Assert.assertTrue(csiIterator.hasNext());
            SAMRecord r1 = htsgetIterator.next();
            SAMRecord r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), "3968040");
            Assert.assertEquals(r2.getReadName(), "3968040");

            r1 = htsgetIterator.next();
            r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), "140419");
            Assert.assertEquals(r2.getReadName(), "140419");
        }
    }

    @Test(dataProvider = "readerProvider")
    public static void testQueryContained(final HtsgetBAMFileReader htsgetReader) throws IOException {
        int counter = 0;
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.query("chrM", 1500, -1, true);
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.query("chrM", 1500, -1, true)) {
            Assert.assertTrue(htsgetIterator.hasNext());
            while (htsgetIterator.hasNext()) {
                Assert.assertTrue(csiIterator.hasNext());

                final SAMRecord r1 = htsgetIterator.next();
                final SAMRecord r2 = csiIterator.next();
                Assert.assertEquals(r1.getReadName(), r2.getReadName());
                Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

                counter++;
            }
            Assert.assertFalse(csiIterator.hasNext());
            Assert.assertEquals(counter, noChrMReads);
        }

        counter = 0;
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.query("chrM", 1500, 10450, true);
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.query("chrM", 1500, 10450, true)) {
            Assert.assertTrue(htsgetIterator.hasNext());
            while (htsgetIterator.hasNext()) {
                Assert.assertTrue(csiIterator.hasNext());

                final SAMRecord r1 = htsgetIterator.next();
                final SAMRecord r2 = csiIterator.next();
                Assert.assertEquals(r1.getReadName(), r2.getReadName());
                Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

                counter++;
            }
            Assert.assertFalse(csiIterator.hasNext());
            Assert.assertEquals(counter, noChrMReadsContained);
        }
    }

    @Test(dataProvider = "readerProvider")
    public static void testQueryOverlapped(final HtsgetBAMFileReader htsgetReader) throws IOException {
        int counter = 0;
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.query("chrM", 1500, 10450, false);
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.query("chrM", 1500, 10450, false)) {
            Assert.assertTrue(htsgetIterator.hasNext());
            while (htsgetIterator.hasNext()) {
                Assert.assertTrue(csiIterator.hasNext());

                final SAMRecord r1 = htsgetIterator.next();
                final SAMRecord r2 = csiIterator.next();
                Assert.assertEquals(r1.getReadName(), r2.getReadName());
                Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

                counter++;
            }
            Assert.assertFalse(csiIterator.hasNext());
            Assert.assertEquals(counter, noChrMReadsOverlapped);
        }
    }

    @Test(dataProvider = "readerProvider")
    public static void testRemovesDuplicates(final HtsgetBAMFileReader htsgetReader) throws IOException {
        // TODO: temporary workaround as reference server does not properly merge regions and remove duplicates yet
        // See https://github.com/ga4gh/htsget-refserver/issues/27
        if (htsgetReader.isUsingPOST()) {
            return;
        }

        final QueryInterval[] intervals = new QueryInterval[]{
            new QueryInterval(0, 1519, 1688),
            new QueryInterval(0, 1690, 2985),
            new QueryInterval(0, 2987, 3034),
        };
        int counter = 0;
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.query(intervals, false);
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.query(intervals, false)) {
            Assert.assertTrue(htsgetIterator.hasNext());
            while (htsgetIterator.hasNext()) {
                Assert.assertTrue(csiIterator.hasNext());

                final SAMRecord r1 = htsgetIterator.next();
                final SAMRecord r2 = csiIterator.next();
                Assert.assertEquals(r1.getReadName(), r2.getReadName());
                Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

                counter++;
            }
            Assert.assertFalse(csiIterator.hasNext());
            Assert.assertEquals(counter, 3);
        }
    }

    @Test(dataProvider = "readerProvider")
    public static void testQueryAlignmentStartNone(final HtsgetBAMFileReader htsgetReader) throws IOException {
        // the first read starts from 1519
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.queryAlignmentStart("chrM", 1500);
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.queryAlignmentStart("chrM", 1500)) {
            Assert.assertFalse(htsgetIterator.hasNext());
            Assert.assertFalse(csiIterator.hasNext());
        }
    }

    @Test(dataProvider = "readerProvider")
    public static void testQueryAlignmentStartOne(final HtsgetBAMFileReader htsgetReader) throws IOException {
        // one read on chrM starts from 9060
        try (final SamReader fileReader = SamReaderFactory.makeDefault().open(bamFile);
             final CloseableIterator<SAMRecord> csiIterator = fileReader.queryAlignmentStart("chrM", 9060);
             final CloseableIterator<SAMRecord> htsgetIterator = htsgetReader.queryAlignmentStart("chrM", 9060)) {
            Assert.assertTrue(htsgetIterator.hasNext());
            Assert.assertTrue(csiIterator.hasNext());

            final SAMRecord r1 = htsgetIterator.next();
            final SAMRecord r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

            Assert.assertFalse(htsgetIterator.hasNext());
            Assert.assertFalse(csiIterator.hasNext());
        }
    }

    @Test
    public static void testEnableFileSource() {
        final SamReader reader = SamReaderFactory.makeDefault().open(new SamInputResource(new FileInputResource(bamFile)));
        bamFileReaderHtsgetGET.enableFileSource(reader, true);
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsgetGET.getIterator()) {
            htsgetIterator.forEachRemaining(record -> Assert.assertEquals(record.getFileSource().getReader(), reader));
        }
        bamFileReaderHtsgetGET.enableFileSource(reader, false);
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsgetGET.getIterator()) {
            htsgetIterator.forEachRemaining(record -> Assert.assertNull(record.getFileSource()));
        }
    }
}
