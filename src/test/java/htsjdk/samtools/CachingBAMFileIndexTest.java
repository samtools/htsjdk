package htsjdk.samtools;


import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CachingBAMFileIndexTest extends HtsjdkTest {

    /**
     * test case to reproduce https://github.com/samtools/htsjdk/issues/1127
     * this tests for a bug that was hidden on human data pre-hg38 due to a weird interaction between WeakHashMap Integer interning
     */
    @Test
    public void testCachingBamFileIndextest() throws IOException {
        try(final CachingBAMFileIndex index = getIndexWith200Contigs()) {
            Assert.assertNotNull(index.getQueryResults(1));
            System.gc();
            //contig 1 is never garbage collected because Integer(1) is interned by the jvm and never garbage collectable
            Assert.assertNotNull(index.getQueryResults(1));

            Assert.assertNotNull(index.getQueryResults(128));
            System.gc();
            //contig 128 is garbage collected and hits the bug because 128+ is not interned
            Assert.assertNotNull(index.getQueryResults(128));
        }
    }

    private static CachingBAMFileIndex getIndexWith200Contigs() throws IOException {
        List<SAMSequenceRecord> contigs = IntStream.range(1, 200)
                .mapToObj(i -> new SAMSequenceRecord(String.valueOf(i), 1000))
                .collect(Collectors.toList());
        final SAMSequenceDictionary dict = new SAMSequenceDictionary(contigs);
        final SAMFileHeader header = new SAMFileHeader(dict);
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);

        final SAMFileWriterFactory writerFactory = new SAMFileWriterFactory().setCreateIndex(true);
        final File outBam = File.createTempFile("tmp", ".bam");
        try(final SAMFileWriter writer = writerFactory.makeWriter(header, true, outBam, null)){
            IntStream.range(1,200).mapToObj(i -> {
                final SAMRecord record = new SAMRecord(header);
                record.setReadName("name" + i);
                record.setReferenceName(String.valueOf(i));
                record.setReadUnmappedFlag(false);
                record.setAlignmentStart(1);
                record.setCigarString("20M");
                return record;
            }).forEach(writer::addAlignment);
        }

        final File indexFile = new File(outBam.getParent(), IOUtil.basename(outBam) + FileExtensions.BAM_INDEX);
        indexFile.deleteOnExit();
        outBam.deleteOnExit();
        return new CachingBAMFileIndex(indexFile, dict);
    }

    @Test
    public void testCacheHitsAndMissesTheExpectedNumberOfTimes() throws IOException {
        try(final CachingBAMFileIndex index = getIndexWith200Contigs()) {
            index.getQueryResults(1);
            assertCacheStats(index, 0, 1);

            index.getQueryResults(1);
            assertCacheStats(index, 1, 1);

            index.getQueryResults(150);
            assertCacheStats(index, 1, 2);

            index.getQueryResults(150);
            assertCacheStats(index,2,2);

            index.getQueryResults(150);
            assertCacheStats(index,3,2);

            index.getQueryResults(1);
            assertCacheStats(index, 3, 3);

            index.getQueryResults(1);
            assertCacheStats(index, 4, 3);

            index.getQueryResults(1000);
            assertCacheStats(index,4,4);
        }
    }

    @Test
    public void testNullResultIsCached() throws IOException {
        try(final CachingBAMFileIndex index = getIndexWith200Contigs()) {
            BAMIndexContent queryResults = index.getQueryResults(1000);
            Assert.assertNull(queryResults);
            assertCacheStats(index, 0, 1);

            queryResults = index.getQueryResults(1000);
            Assert.assertNull(queryResults);
            assertCacheStats(index, 1, 1);

            queryResults = index.getQueryResults(1);
            Assert.assertNotNull(queryResults);
            assertCacheStats(index, 1, 2);

            queryResults = index.getQueryResults(1000);
            Assert.assertNull(queryResults);
            assertCacheStats(index, 1, 3);

            queryResults = index.getQueryResults(1000);
            Assert.assertNull(queryResults);
            assertCacheStats(index, 2, 3);
        }
    }

    private static void assertCacheStats(CachingBAMFileIndex index, int hits, int misses) {
        Assert.assertEquals(index.getCacheHits(), hits, "cache hits didn't match expected");
        Assert.assertEquals(index.getCacheMisses(), misses, "cache misses didn't match expected");
    }

}