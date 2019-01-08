package htsjdk.samtools.filter;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.util.CollectionUtil;
import htsjdk.samtools.util.Interval;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class IntervalKeepPairFilterTest extends HtsjdkTest {
    private static final int READ_LENGTH = 151;
    private final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();

    @BeforeTest
    public void setUp() {
        builder.setReadLength(READ_LENGTH);
        // Will be kept when an interval overlaps chromosome 1 in the first 151
        // bases.
        builder.addPair("mapped_pair_chr1", 0, 1, 151);
        // Will be kept when an interval overlaps chromsome 2 in the first 151
        // bases.
        builder.addPair("mapped_pair_chr2", 1, 1, 151);
        // The first read should pass and second should not, but both will
        // be kept in first test.
        builder.addPair("one_of_pair", 0, 1, 1000);
        // The second read is unmapped, but both should be kept in an
        // interval test where the interval includes chromosome four, where
        // read one will overlap.
        builder.addPair("second_mate_unmapped", 3, -1, 1, 1000, false, true,
                "151M", null, false, false, false, false, -1);
        // The first read is unmapped but both should be kept in an
        // interval test where the interval includes chromosome four, where
        // read two will overlap.
        builder.addPair("first_mate_unmapped", -1, 3, 1000, 1, true, false,
                null, "151M", false, false, false, false, -1);
        // This pair will overlap any interval that includes chromosome 1:1000
        builder.addPair("prove_one_of_pair", 0, 1000, 1000);
        // These reads are unmapped and will not map to any intervals, so they
        // are never kept. This is tested below.
        builder.addPair("both_unmapped", -1, -1, 1, 1, true, true, null, null,
                false, false, false, false, -1);
        // Secondary alignments are never kept by the interval filter.
        builder.addFrag("mapped_pair_chr1", 0, 1, false, false, "151M", null, -1, true, false);
        // Supplementary alignment are never kept by the interval filter.
        builder.addFrag("mapped_pair_chr1", 0, 1, false, false, "151M", null, -1, false, true);
        // Single ended read should never be kept by the interval filter.
        builder.addFrag("single_ended", 0, 1, false);
    }

    @Test(dataProvider = "testData")
    public void testIntervalPairFilter(final List<Interval> intervals, final long expectedPassingRecords) {
        final IntervalKeepPairFilter filter = new IntervalKeepPairFilter(intervals);

        long actualPassingRecords = builder.getRecords().stream()
                .filter(rec -> !filter.filterOut(rec))
                .count();

        Assert.assertEquals(actualPassingRecords, expectedPassingRecords);
    }

    @Test
    public void testUnmappedPair() {
        final List<Interval> intervalList = new ArrayList<>();

        final Interval interval1 = new Interval("chr1", 1, 999);
        final Interval interval2 = new Interval("chr3", 1, 2);
        final Interval interval3 = new Interval("chr2", 1, 2);
        final Interval interval4 = new Interval("chr4", 1, 2);

        intervalList.addAll(CollectionUtil.makeList(interval1, interval2, interval3, interval4));

        final IntervalKeepPairFilter filter = new IntervalKeepPairFilter(intervalList);

        boolean unmappedPassed = builder.getRecords().stream()
                .filter(rec -> !filter.filterOut(rec))
                .anyMatch(rec -> rec.getReadName().equals("both_unmapped"));

        Assert.assertFalse(unmappedPassed);
    }

    @Test
    public void testNotPrimaryReads() {
        final List<Interval> intervalList = new ArrayList<>();
        final Interval interval1 = new Interval("chr1", 1, 999);
        intervalList.add(interval1);

        final IntervalKeepPairFilter filter = new IntervalKeepPairFilter(intervalList);

        boolean notPrimary = builder.getRecords().stream()
                .filter(rec -> !filter.filterOut(rec))
                .anyMatch(rec -> rec.isSecondaryAlignment() || rec.getSupplementaryAlignmentFlag());

        Assert.assertFalse(notPrimary);
    }

    @Test
    public void testSingleEndedReads() {
        final List<Interval> intervalList = new ArrayList<>();
        final Interval interval1 = new Interval("chr1", 1, 999);
        intervalList.add(interval1);

        final IntervalKeepPairFilter filter = new IntervalKeepPairFilter(intervalList);

        boolean singleEnded = builder.getRecords().stream()
                .filter(rec -> !filter.filterOut(rec))
                .anyMatch(rec -> rec.getReadName().equals("single_ended"));

        Assert.assertFalse(singleEnded);
    }

    @DataProvider(name = "testData")
    private Object[][] testData() {
        Interval interval = new Interval("chr1", 1, 999);
        final List<Interval> intervalList_twoPair = new ArrayList<>();
        intervalList_twoPair.add(interval);

        interval = new Interval("chr3", 1, 2);
        final List<Interval> intervalList_noMatch = new ArrayList<>();
        intervalList_noMatch.add(interval);

        interval = new Interval("chr2", 1, 2);
        final List<Interval> intervalList_onePair = new ArrayList<>();
        intervalList_onePair.add(interval);

        interval = new Interval("chr4", 1, 2);
        final List<Interval> intervalList_unmapped = new ArrayList<>();
        intervalList_unmapped.add(interval);

        return new Object[][]{
                {intervalList_twoPair, 4},
                {intervalList_noMatch, 0},
                {intervalList_onePair, 2},
                {intervalList_unmapped, 4}
        };
    }
}
