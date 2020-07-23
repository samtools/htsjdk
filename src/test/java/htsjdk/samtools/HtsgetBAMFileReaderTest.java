package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CoordMath;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class HtsgetBAMFileReaderTest extends HtsjdkTest {
    private static final String HTSGET_ENDPOINT = "http://127.0.0.1:3000/reads/";
    private static final String LOCAL_PREFIX = "htsjdk_test.";

    private final static URI htsgetBAM = URI.create(HTSGET_ENDPOINT + LOCAL_PREFIX + "index_test.bam");

    private final static File bamFile = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private final static File csiFileIndex = new File(bamFile.getPath() + ".csi");

    private final static int nofMappedReads = 9721;
    private final static int nofUnmappedReads = 279;
    private final static int noChrMReads = 23;
    private final static int noChrMReadsContained = 9;
    private final static int noChrMReadsOverlapped = 10;

    private static HtsgetBAMFileReader bamFileReaderHtsget;
    private static BAMFileReader bamFileReaderCSI;

    @BeforeTest
    public void init() throws IOException {
        bamFileReaderHtsget = new HtsgetBAMFileReader(htsgetBAM, true, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance(), false);
        bamFileReaderCSI = new BAMFileReader(bamFile, csiFileIndex, true, false, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance());
    }

    @Test
    public static void testQueryMapped() throws IOException {
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

                final CloseableIterator<SAMRecord> iterator = bamFileReaderHtsget.queryAlignmentStart(
                    samRecord.getReferenceName(),
                    samRecord.getAlignmentStart());

                Assert.assertTrue(iterator.hasNext(), counter + ": " + sam1);
                final SAMRecord bamRecord = iterator.next();
                final String sam2 = bamRecord.getSAMString();
                Assert.assertEquals(samRecord.getReferenceName(), bamRecord.getReferenceName(), sam1 + sam2);

                // default 'overlap' is true, so test records intersect the query:
                Assert.assertTrue(CoordMath.overlaps(
                    bamRecord.getAlignmentStart(),
                    bamRecord.getAlignmentEnd(),
                    samRecord.getAlignmentStart(),
                    samRecord.getAlignmentEnd()),
                    sam1 + sam2);

                iterator.close();
            }
            Assert.assertEquals(counter, nofMappedReads);
        }
    }

    @Test
    public static void testQueryUnmapped() {
        int counter = 0;
        final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.queryUnmapped();
        final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.queryUnmapped();

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

        htsgetIterator.close();
        csiIterator.close();
    }

    @Test
    public static void testQueryInterval() {
        final QueryInterval[] query = new QueryInterval[]{new QueryInterval(0, 1519, 1520), new QueryInterval(1, 470535, 470536)};
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.query(query, false);
             final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query(query, false)) {

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

    @Test
    public static void testQueryContained() {
        int counter = 0;
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.query("chrM", 1500, -1, true);
             final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query("chrM", 1500, -1, true)) {
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
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.query("chrM", 1500, 10450, true);
             final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query("chrM", 1500, 10450, true)) {
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

    @Test
    public static void testQueryOverlapped() {
        int counter = 0;
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.query("chrM", 1500, 10450, false);
             final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query("chrM", 1500, 10450, false)) {
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

    @Test
    public static void testRemovesDuplicates() {
        final QueryInterval[] intervals = new QueryInterval[]{
            new QueryInterval(0, 1519, 1688),
            new QueryInterval(0, 1690, 2985),
            new QueryInterval(0, 2987, 3034),
        };
        int counter = 0;
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.query(intervals, false);
             final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query(intervals, false)) {
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

    @Test
    public static void testQueryAlignmentStartNone() {
        // the first read starts from 1519
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.queryAlignmentStart("chrM", 1500);
             final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.queryAlignmentStart("chrM", 1500)) {
            Assert.assertFalse(htsgetIterator.hasNext());
            Assert.assertFalse(csiIterator.hasNext());
        }
    }

    @Test
    public static void testQueryAlignmentStartOne() {
        // one read on chrM starts from 9060
        try (final CloseableIterator<SAMRecord> htsgetIterator = bamFileReaderHtsget.queryAlignmentStart("chrM", 9060);
             final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.queryAlignmentStart("chrM", 9060)) {
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
}
