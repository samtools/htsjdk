package htsjdk.beta.codecs.reads.bam;

import htsjdk.HtsjdkTest;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.registry.HtsReadsCodecs;
import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamFiles;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

/**
 * Test BAM file index queries.
 *
 * Test and code hijacked from BAMFileIndexTest.
 */
public class HtsBAMCodecQueryTest extends HtsjdkTest {
    private final IOPath TEST_BAM = new HtsPath("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private final IOPath TEST_BAI = new HtsPath(SamFiles.findIndex(TEST_BAM.toPath().toFile()).toString());
    private final boolean mVerbose = false;

    @Test
    public void testSpecificQueries()
            throws Exception {
        assertEquals(runQueryTest(TEST_BAM, "chrM", 10400, 10600, HtsQueryRule.CONTAINED), 1);
        assertEquals(runQueryTest(TEST_BAM, "chrM", 10400, 10600, HtsQueryRule.OVERLAPPING), 2);
    }

    @Test(groups = {"slow"})
    public void testRandomQueries() throws Exception {
        runRandomTest(TEST_BAM, 1000, new Random(TestUtil.RANDOM_SEED));
    }

    @Test
    public void testWholeChromosomes() {
        checkChromosome("chrM", 23);
        checkChromosome("chr1", 885);
        checkChromosome("chr2", 837);
    }

//    @Test
//    public void testQueryAlignmentStart() {
//        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsCodecRegistry.getReadsDecoder(TEST_BAM)) {
//            Iterator<SAMRecord> it = bamDecoder.queryStart("chr1", 202160268);
//            Assert.assertEquals(countElements(it), 2);
//            it = bamDecoder.queryStart("chr1", 201595153);
//            Assert.assertEquals(countElements(it), 1);
//            // There are records that overlap this position, but none that start here
//            it = bamDecoder.queryStart("chrM", 10400);
//            Assert.assertEquals(countElements(it), 0);
//            // One past the last chr1 record
//            it = bamDecoder.queryStart("chr1", 246817509);
//            Assert.assertEquals(countElements(it), 0);
//        }
//    }

    @DataProvider(name = "queryIntervalsData")
    public Object[][] queryIntervalsData(){
        return new Object[][] {
                {HtsQueryRule.CONTAINED, 1},
                {HtsQueryRule.OVERLAPPING, 2}
        };
    }

    @Test(dataProvider = "queryIntervalsData")
    public void testQueryIntervals(final HtsQueryRule queryRule, final int expected) {
        final Bundle readsBundle =
                BundleBuilder.start()
                        .addPrimary(new IOPathResource(TEST_BAM, BundleResourceType.READS))
                        .add(new IOPathResource(TEST_BAI, BundleResourceType.READS_INDEX))
                        .getBundle();
        try (final HtsDecoder bamDecoder = HtsReadsCodecs.getReadsDecoder(readsBundle, new ReadsDecoderOptions())) {
            final Iterator<SAMRecord> it = bamDecoder.query("chr1", 202661637, 202661812, queryRule);
            Assert.assertEquals(countElements(it), expected);
        }
    }

//    /**
//     * Compare the results of a multi-interval query versus the union of the results from each interval done
//     * separately.
//     */
//    @Test(dataProvider = "testMultiIntervalQueryDataProvider")
//    public void testMultiIntervalQuery(final boolean contained) {
//        final List<String> referenceNames = getReferenceNames(TEST_BAM);
//
//        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsCodecRegistry.getReadsDecoder(TEST_BAM)) {
//            final QueryInterval[] intervals = generateRandomIntervals(referenceNames.size(), 1000, new Random(TestUtil.RANDOM_SEED));
//            final Set<SAMRecord> multiIntervalRecords = new HashSet<>();
//            final Set<SAMRecord> singleIntervalRecords = new HashSet<>();
//            for (final QueryInterval interval : intervals) {
//                consumeAll(singleIntervalRecords,
//                        bamDecoder.query(referenceNames.get(interval.referenceIndex),
//                                interval.start,
//                                interval.end,
//                                contained));
//            }
//
//            final QueryInterval[] optimizedIntervals = QueryInterval.optimizeIntervals(intervals);
//            consumeAll(multiIntervalRecords, bamDecoder.query(queryIntervalArrayToHtsIntervalList(optimizedIntervals), contained));
//            final Iterator<SAMRecord> singleIntervalRecordIterator = singleIntervalRecords.iterator();
//            boolean failed = false;
//            while (singleIntervalRecordIterator.hasNext()) {
//                final SAMRecord record = singleIntervalRecordIterator.next();
//                if (!multiIntervalRecords.remove(record)) {
//                    System.out.println("SingleIntervalQuery found " + record + " but MultiIntervalQuery did not");
//                    failed = true;
//                }
//            }
//            for (final SAMRecord record : multiIntervalRecords) {
//                System.out.println("MultiIntervalQuery found " + record + " but SingleIntervalQuery did not");
//                failed = true;
//            }
//            Assert.assertFalse(failed);
//        }
//    }

    @DataProvider(name = "testMultiIntervalQueryDataProvider")
    private Object[][] testMultiIntervalQueryDataProvider() {
        return new Object[][]{{true}, {false}};
    }

    private <E> void consumeAll(final Collection<E> collection, final Iterator<E> iterator) {
        while (iterator.hasNext()) {
            collection.add(iterator.next());
        }
    }

    private long countElements(final Iterator<SAMRecord> it) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED),
                false).count();
    }

    private void checkChromosome(final String name, final int expectedCount) {
        int count = runQueryTest(TEST_BAM, name, 0, 0, HtsQueryRule.CONTAINED);
        assertEquals(count, expectedCount);
        count = runQueryTest(TEST_BAM, name, 0, 0, HtsQueryRule.OVERLAPPING);
        assertEquals(count, expectedCount);
    }

    private void runRandomTest(final IOPath bamFile, final int count, final Random generator) {
        final List<String> referenceNames = getReferenceNames(bamFile);
        final QueryInterval[] intervals = generateRandomIntervals(referenceNames.size(), count, generator);
        for (final QueryInterval interval : intervals) {
            final String refName = referenceNames.get(interval.referenceIndex);
            final int startPos = interval.start;
            final int endPos = interval.end;
            System.out.println("Testing query " + refName + ":" + startPos + "-" + endPos + " ...");
            try {
                runQueryTest(bamFile, refName, startPos, endPos, HtsQueryRule.CONTAINED);
                runQueryTest(bamFile, refName, startPos, endPos, HtsQueryRule.OVERLAPPING);
            } catch (final Throwable exc) {
                String message = "Query test failed: " + refName + ":" + startPos + "-" + endPos;
                message += ": " + exc.getMessage();
                throw new RuntimeException(message, exc);
            }
        }
    }

    private QueryInterval[] generateRandomIntervals(final int numReferences, final int count, final Random generator) {
        final QueryInterval[] intervals = new QueryInterval[count];
        final int maxCoordinate = 10000000;
        for (int i = 0; i < count; i++) {
            final int referenceIndex = generator.nextInt(numReferences);
            final int coord1 = generator.nextInt(maxCoordinate + 1);
            final int coord2 = generator.nextInt(maxCoordinate + 1);
            final int startPos = Math.min(coord1, coord2);
            final int endPos = Math.max(coord1, coord2);
            intervals[i] = new QueryInterval(referenceIndex, startPos, endPos);
        }

        return intervals;
    }

    private static List<String> getReferenceNames(final IOPath bamFile) {
        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsReadsCodecs.getReadsDecoder(bamFile)) {
            final List<String> result = new ArrayList<>();
            final List<SAMSequenceRecord> seqRecords = bamDecoder.getHeader().getSequenceDictionary().getSequences();
            for (final SAMSequenceRecord seqRecord : seqRecords) {
                if (seqRecord.getSequenceName() != null) {
                    result.add(seqRecord.getSequenceName());
                }
            }
            return result;
        }
    }

    private int runQueryTest(final IOPath bamFile, final String sequence, final int startPos, final int endPos, final HtsQueryRule queryRule) {
        verbose("Testing query " + sequence + ":" + startPos + "-" + endPos + " ...");

        final ReadsBundle readsBundleWithIndex = ReadsBundle.resolveIndex(bamFile);
        try (final BAMDecoder bamDecoder = (BAMDecoder) HtsReadsCodecs.getReadsDecoder(readsBundleWithIndex);
             final BAMDecoder bamDecoder2 = (BAMDecoder) HtsReadsCodecs.getReadsDecoder(readsBundleWithIndex)) {
            final Iterator<SAMRecord> iter1 = bamDecoder.query(sequence, startPos, endPos, queryRule);
            final Iterator<SAMRecord> iter2 = bamDecoder2.iterator();
            // Compare ordered iterators.
            // Confirm that iter1 is a subset of iter2 that properly filters.
            SAMRecord record1 = null;
            SAMRecord record2 = null;
            int count1 = 0;
            int count2 = 0;
            int beforeCount = 0;
            int afterCount = 0;
            while (true) {
                if (record1 == null && iter1.hasNext()) {
                    record1 = iter1.next();
                    count1++;
                }
                if (record2 == null && iter2.hasNext()) {
                    record2 = iter2.next();
                    count2++;
                }
                if (record1 == null && record2 == null) {
                    break;
                }
                if (record1 == null) {
                    checkPassesFilter(false, record2, sequence, startPos, endPos, queryRule);
                    record2 = null;
                    afterCount++;
                    continue;
                }
                assertNotNull(record2);
                final int ordering = compareCoordinates(record1, record2);
                if (ordering > 0) {
                    checkPassesFilter(false, record2, sequence, startPos, endPos, queryRule);
                    record2 = null;
                    beforeCount++;
                    continue;
                }
                assertTrue(ordering == 0);
                checkPassesFilter(true, record1, sequence, startPos, endPos, queryRule);
                checkPassesFilter(true, record2, sequence, startPos, endPos, queryRule);
                assertEquals(record1.getReadName(), record2.getReadName());
                assertEquals(record1.getReadString(), record2.getReadString());
                record1 = null;
                record2 = null;
            }
            verbose("Checked " + count1 + " records against " + count2 + " records.");
            verbose("Found " + (count2 - beforeCount - afterCount) + " records matching.");
            verbose("Found " + beforeCount + " records before.");
            verbose("Found " + afterCount + " records after.");
            return count1;
        }
    }

    private static void checkPassesFilter(final boolean expected, final SAMRecord record, final String sequence, final int startPos, final int endPos, final HtsQueryRule queryRule) {
        final boolean passes = passesFilter(record, sequence, startPos, endPos, queryRule);
        if (passes != expected) {
            System.out.println("Error: Record erroneously " +
                    (passes ? "passed" : "failed") +
                    " filter.");
            System.out.println(" Record: " + record.getSAMString());
            System.out.println(" Filter: " + sequence + ":" +
                    startPos + "-" + endPos +
                    " (" + (queryRule == HtsQueryRule.CONTAINED ? "contained" : "overlapping") + ")");
            assertEquals(passes, expected);
        }
    }

    private static boolean passesFilter(final SAMRecord record, final String sequence, final int startPos, final int endPos, final HtsQueryRule queryRule) {
        if (record == null) {
            return false;
        }
        if (!safeEquals(record.getReferenceName(), sequence)) {
            return false;
        }
        final int alignmentStart = record.getAlignmentStart();
        int alignmentEnd = record.getAlignmentEnd();
        if (alignmentStart <= 0) {
            assertTrue(record.getReadUnmappedFlag());
            return false;
        }
        if (alignmentEnd <= 0) {
            // For indexing-only records, treat as single base alignment.
            assertTrue(record.getReadUnmappedFlag());
            alignmentEnd = alignmentStart;
        }
        if (queryRule == HtsQueryRule.CONTAINED) {
            if (startPos != 0 && alignmentStart < startPos) {
                return false;
            }
            if (endPos != 0 && alignmentEnd > endPos) {
                return false;
            }
        } else {
            if (startPos != 0 && alignmentEnd < startPos) {
                return false;
            }
            if (endPos != 0 && alignmentStart > endPos) {
                return false;
            }
        }
        return true;
    }

    private static int compareCoordinates(final SAMRecord record1, final SAMRecord record2) {
        final int seqIndex1 = record1.getReferenceIndex();
        final int seqIndex2 = record2.getReferenceIndex();
        if (seqIndex1 == -1) {
            return ((seqIndex2 == -1) ? 0 : -1);
        } else if (seqIndex2 == -1) {
            return 1;
        }
        int result = seqIndex1 - seqIndex2;
        if (result != 0) {
            return result;
        }
        result = record1.getAlignmentStart() - record2.getAlignmentStart();
        return result;
    }

    private static boolean safeEquals(final Object o1, final Object o2) {
        if (o1 == o2) {
            return true;
        } else if (o1 == null || o2 == null) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }

    private void verbose(final String text) {
        if (mVerbose) {
            System.out.println("# " + text);
        }
    }

}
