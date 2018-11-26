package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CoordMath;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class BAMFileReaderTest extends HtsjdkTest {
    private final static File bamFile = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private final static File baiFileIndex = new File(bamFile.getPath() + ".bai");
    private final static File csiFileIndex = new File(bamFile.getPath() + ".csi");
    private final static File iiiFileIndex = new File(bamFile.getPath() + ".iii");
    private final static int nofMappedReads = 9721;
    private final static int nofUnmappedReads = 279 ;
    private final static int noChrMReads = 23;
    private final static int noChrMReadsContained = 9;
    private final static int noChrMReadsOverlapped = 10;

    private static BAMFileReader bamFileReaderBAI;
    private static BAMFileReader bamFileReaderCSI;
    private static BAMFileReader bamFileReaderWrong;
    private static BAMFileReader bamFileReaderNull;

    @BeforeTest
    public void init() throws IOException {
        bamFileReaderBAI = new BAMFileReader(bamFile, baiFileIndex, true, false, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance());
        bamFileReaderCSI = new BAMFileReader(bamFile, csiFileIndex, true, false, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance());
        bamFileReaderWrong = new BAMFileReader(bamFile, iiiFileIndex, true, false, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance());
        bamFileReaderNull = new BAMFileReader(bamFile, null, true, false, ValidationStringency.DEFAULT_STRINGENCY, DefaultSAMRecordFactory.getInstance());
    }

    @Test
    public static void testGetIndexTypeOK() {
        BAMIndexMetaData.printIndexStats(bamFile);
        Assert.assertTrue(bamFileReaderBAI.getIndexType().equals(SamIndexes.BAI));
        Assert.assertTrue(bamFileReaderCSI.getIndexType().equals(SamIndexes.CSI));
    }

    @Test (expectedExceptions = SAMFormatException.class)
    public static void testGetIndexTypeException() {
        Assert.assertTrue(bamFileReaderWrong.getIndexType().equals(SamIndexes.BAI));
    }

    @Test
    public static void testGetIndexTypeDefault() {
        Assert.assertTrue(bamFileReaderNull.getIndexType().equals(SamIndexes.BAI));
    }

    @Test
    public static void testGetIndexOK() {
        Assert.assertTrue(bamFileReaderBAI.hasIndex());
        Assert.assertTrue(bamFileReaderCSI.hasIndex());
        Assert.assertTrue(bamFileReaderNull.hasIndex());
        Assert.assertNotNull(bamFileReaderBAI.getIndex());
        Assert.assertNotNull(bamFileReaderCSI.getIndex());
        Assert.assertNotNull(bamFileReaderNull.getIndex());
    }

    @Test (expectedExceptions = SAMFormatException.class)
    public static void testGetIndexException() {
        Assert.assertNotNull(bamFileReaderWrong.getIndex());
    }

    @Test
    public static void testQueryMapped() throws IOException {
        try (SamReader samReader = SamReaderFactory.makeDefault().open(bamFile);
             SAMRecordIterator samRecordIterator = samReader.iterator())
        {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);

            int counter = 0;
            while (samRecordIterator.hasNext()) {
                SAMRecord samRecord = samRecordIterator.next();
                if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    break;
                }
                if (counter++ % 100 > 1) { // test only 1st and 2nd in every 100 to speed the test up:
                    continue;
                }
                String sam1 = samRecord.getSAMString();

                CloseableIterator<SAMRecord> iterator = bamFileReaderBAI.queryAlignmentStart(
                        samRecord.getReferenceName(),
                        samRecord.getAlignmentStart());

                Assert.assertTrue(iterator.hasNext(), counter + ": " + sam1);
                SAMRecord bamRecord = iterator.next();
                String sam2 = bamRecord.getSAMString();
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

        // test the reader with the CSI index
        try (SamReader samReader = SamReaderFactory.makeDefault().open(bamFile);
             SAMRecordIterator samRecordIterator = samReader.iterator())
        {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);

            int counter = 0;
            while (samRecordIterator.hasNext()) {
                SAMRecord samRecord = samRecordIterator.next();
                if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    break;
                }
                if (counter++ % 100 > 1) { // test only 1st and 2nd in every 100 to speed the test up:
                    continue;
                }
                String sam1 = samRecord.getSAMString();

                CloseableIterator<SAMRecord> iterator = bamFileReaderCSI.queryAlignmentStart(
                        samRecord.getReferenceName(),
                        samRecord.getAlignmentStart());

                Assert.assertTrue(iterator.hasNext(), counter + ": " + sam1);
                SAMRecord bamRecord = iterator.next();
                String sam2 = bamRecord.getSAMString();
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
        CloseableIterator<SAMRecord> baiIterator = bamFileReaderBAI.queryUnmapped();
        CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.queryUnmapped();

        Assert.assertTrue(baiIterator.hasNext());
        while (baiIterator.hasNext()) {
            Assert.assertTrue(csiIterator.hasNext());

            SAMRecord r1 = baiIterator.next();
            SAMRecord r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

            counter++;
        }
        Assert.assertFalse(csiIterator.hasNext());
        Assert.assertEquals(counter, nofUnmappedReads);

        baiIterator.close();
        csiIterator.close();
    }

    @Test
    public static void testQueryInterval() {
        QueryInterval[] query = new QueryInterval[]{new QueryInterval(0, 1519, 1520), new QueryInterval(1, 470535, 470536)};
        final CloseableIterator<SAMRecord> baiIterator = bamFileReaderBAI.query(query, false);
        final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query(query, false);

        Assert.assertTrue(baiIterator.hasNext());
        Assert.assertTrue(csiIterator.hasNext());
        SAMRecord r1 = baiIterator.next();
        SAMRecord r2 = csiIterator.next();
        Assert.assertEquals(r1.getReadName(), "3968040");
        Assert.assertEquals(r2.getReadName(), "3968040");

        r1 = baiIterator.next();
        r2 = csiIterator.next();
        Assert.assertEquals(r1.getReadName(), "140419");
        Assert.assertEquals(r2.getReadName(), "140419");

        baiIterator.close();
        csiIterator.close();
    }

    @Test
    public static void testQueryContained() {
        int counter = 0;
        CloseableIterator<SAMRecord> baiIterator = bamFileReaderBAI.query("chrM", 1500, -1, true);
        CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query("chrM", 1500, -1, true);

        Assert.assertTrue(baiIterator.hasNext());
        while (baiIterator.hasNext()) {
            Assert.assertTrue(csiIterator.hasNext());

            SAMRecord r1 = baiIterator.next();
            SAMRecord r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

            counter++;
        }
        Assert.assertFalse(csiIterator.hasNext());
        Assert.assertEquals(counter, noChrMReads);

        baiIterator.close();
        csiIterator.close();

        counter = 0;
        baiIterator = bamFileReaderBAI.query("chrM", 1500, 10450, true);
        csiIterator = bamFileReaderCSI.query("chrM", 1500, 10450, true);

        Assert.assertTrue(baiIterator.hasNext());
        while (baiIterator.hasNext()) {
            Assert.assertTrue(csiIterator.hasNext());

            SAMRecord r1 = baiIterator.next();
            SAMRecord r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

            counter++;
        }
        Assert.assertFalse(csiIterator.hasNext());
        Assert.assertEquals(counter, noChrMReadsContained);

        baiIterator.close();
        csiIterator.close();
    }

    @Test
    public static void testQueryOverlapped() {
        int counter = 0;
        CloseableIterator<SAMRecord> baiIterator = bamFileReaderBAI.query("chrM", 1500, 10450, false);
        CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.query("chrM", 1500, 10450, false);

        Assert.assertTrue(baiIterator.hasNext());
        while (baiIterator.hasNext()) {
            Assert.assertTrue(csiIterator.hasNext());

            SAMRecord r1 = baiIterator.next();
            SAMRecord r2 = csiIterator.next();
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

            counter++;
        }
        Assert.assertFalse(csiIterator.hasNext());
        Assert.assertEquals(counter, noChrMReadsOverlapped);

        baiIterator.close();
        csiIterator.close();
    }

    @Test
    public static void testQueryAlignmentStartNone() throws IOException {
        // the first read starts from 1519
        final CloseableIterator<SAMRecord> baiIterator = bamFileReaderBAI.queryAlignmentStart("chrM", 1500);
        final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.queryAlignmentStart("chrM", 1500);

        Assert.assertFalse(baiIterator.hasNext());
        Assert.assertFalse(csiIterator.hasNext());

        baiIterator.close();
        csiIterator.close();
    }

    @Test
    public static void testQueryAlignmentStartOne() throws IOException {
        // one read on chrM starts from 9060
        final CloseableIterator<SAMRecord> baiIterator = bamFileReaderBAI.queryAlignmentStart("chrM", 9060);
        final CloseableIterator<SAMRecord> csiIterator = bamFileReaderCSI.queryAlignmentStart("chrM", 9060);

        Assert.assertTrue(baiIterator.hasNext());
        Assert.assertTrue(csiIterator.hasNext());

        SAMRecord r1 = baiIterator.next();
        SAMRecord r2 = csiIterator.next();
        Assert.assertEquals(r1.getReadName(), r2.getReadName());
        Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());


        Assert.assertFalse(baiIterator.hasNext());
        Assert.assertFalse(csiIterator.hasNext());

        baiIterator.close();
        csiIterator.close();
    }

    @Test
    public static void testFindVirtualOffsetOfFirstRecord() throws IOException {
        Assert.assertEquals(BAMFileReader.findVirtualOffsetOfFirstRecord(bamFile), 8384);
    }

}
