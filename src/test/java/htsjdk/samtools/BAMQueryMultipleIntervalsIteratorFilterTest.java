package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Random;

public class BAMQueryMultipleIntervalsIteratorFilterTest {

    private final byte[] BASES = {'A', 'C', 'G', 'T'};
    private final Random random = new Random();

    @DataProvider(name="compareIntervalToRecord")
    public Object[][] compareIntervalToRecord() {
        return new Object[][] {
                { new QueryInterval(0, 20, 20), 0, 10, 5, BAMIteratorFilter.IntervalComparison.AFTER },
                { new QueryInterval(0, 20, 22), 0, 10, 5, BAMIteratorFilter.IntervalComparison.AFTER },
                { new QueryInterval(1, 10, 22), 0, 10, 5, BAMIteratorFilter.IntervalComparison.AFTER },
                { new QueryInterval(1, 0, 22), 0, 10, 5, BAMIteratorFilter.IntervalComparison.AFTER },
                { new QueryInterval(1, -1, 22), 0, 10, 5, BAMIteratorFilter.IntervalComparison.AFTER },
                { new QueryInterval(1, 1, 22), 0, 10, 5, BAMIteratorFilter.IntervalComparison.AFTER },

                { new QueryInterval(0, 0, 4), 0, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 0, 5), 0, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 0, -1), 1, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 1, 0), 1, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 1, -1), 1, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 0, 0), 1, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 9, 9), 0, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 1, 4), 0, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },
                { new QueryInterval(0, 1, 4), 1, 10, 5, BAMIteratorFilter.IntervalComparison.BEFORE },

                { new QueryInterval(0, 0, 0), 0, 10, 5, BAMIteratorFilter.IntervalComparison.CONTAINED },
                { new QueryInterval(0, 1, -1), 0, 1, 100, BAMIteratorFilter.IntervalComparison.CONTAINED },
                { new QueryInterval(0, 1, 0), 0, 1, 100, BAMIteratorFilter.IntervalComparison.CONTAINED },
                { new QueryInterval(0, 1, 0), 0, 10, 5, BAMIteratorFilter.IntervalComparison.CONTAINED },
                { new QueryInterval(0, 1, -1), 0, 10, 5, BAMIteratorFilter.IntervalComparison.CONTAINED },
                { new QueryInterval(0, 10, 15), 0, 10, 5, BAMIteratorFilter.IntervalComparison.CONTAINED },
                { new QueryInterval(0, 10, 0), 0, 10, 5, BAMIteratorFilter.IntervalComparison.CONTAINED },

                { new QueryInterval(0, 10, 11), 0, 10, 5, BAMIteratorFilter.IntervalComparison.OVERLAPPING },
                { new QueryInterval(0, 1, 10), 0, 9, 5, BAMIteratorFilter.IntervalComparison.OVERLAPPING },
                { new QueryInterval(0, 0, 10), 0, 9, 5, BAMIteratorFilter.IntervalComparison.OVERLAPPING },
                { new QueryInterval(0, 1, 10), 0, 9, 5, BAMIteratorFilter.IntervalComparison.OVERLAPPING },
                { new QueryInterval(0, 1, 5), 0, 5, 10, BAMIteratorFilter.IntervalComparison.OVERLAPPING },
        };
    }

    @Test(dataProvider = "compareIntervalToRecord")
    public void testCompareIntervalToRecord(
            final QueryInterval query,
            final int refIndex,
            final int start,
            final int length,
            final BAMIteratorFilter.IntervalComparison expectedState)
    {
        SAMRecord samRec = getSAMRecord(refIndex, start, length);
        Assert.assertEquals(BAMQueryMultipleIntervalsIteratorFilter.compareIntervalToRecord(query, samRec), expectedState);
    }

    @DataProvider(name="compareToFilter")
    public Object[][] compareToFilter() {
        return new Object[][] {
                { new QueryInterval[] { new QueryInterval(0, 10, 11), new QueryInterval(1, 1, 10) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(0, 10, 11), new QueryInterval(1, 5, 10) },
                        1, 1, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(0, 20, 20), new QueryInterval(0, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(0, 20, 22), new QueryInterval(0, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(1, 10, 22), new QueryInterval(0, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(1, 0, 22), new QueryInterval(0, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(1, -1, 22), new QueryInterval(0, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(0, 10, 5), new QueryInterval(1, 1, 22) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION },
                { new QueryInterval[] { new QueryInterval(0, 1, -1), new QueryInterval(1, 5, 10) },
                        1, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION},

                { new QueryInterval[] { new QueryInterval(0, 1, 4), new QueryInterval(0, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.STOP_ITERATION },
                { new QueryInterval[] { new QueryInterval(0, 0, 5), new QueryInterval(0, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.STOP_ITERATION },
                { new QueryInterval[] { new QueryInterval(0, 0, 5), new QueryInterval(0, 5, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.STOP_ITERATION},
                { new QueryInterval[] { new QueryInterval(0, 0, 5), new QueryInterval(1, 5, 5) },
                        1, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.STOP_ITERATION},

                { new QueryInterval[] { new QueryInterval(1, 10, 5), new QueryInterval(1, 10, 10) },
                        1, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.CONTINUE_ITERATION},
                { new QueryInterval[] { new QueryInterval(1, 10, 5), new QueryInterval(1, 10, 10) },
                        1, 10, 5, false, BAMIteratorFilter.FilteringIteratorState.MATCHES_FILTER},

                { new QueryInterval[] { new QueryInterval(0, 0, -1), new QueryInterval(1, 10, 5) },
                        0, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.MATCHES_FILTER },
                { new QueryInterval[] { new QueryInterval(0, 1, -1), new QueryInterval(1, 5, -1) },
                        1, 10, 5, true, BAMIteratorFilter.FilteringIteratorState.MATCHES_FILTER},
        };
    }

    @Test(dataProvider = "compareToFilter")
    public void testCompareToFilter(
            final QueryInterval[] query,
            final int refIndex,
            final int start,
            final int length,
            final boolean contained,
            final BAMIteratorFilter.FilteringIteratorState expectedState)
    {
        SAMRecord samRec = getSAMRecord(refIndex, start, length);
        BAMQueryMultipleIntervalsIteratorFilter it = new BAMQueryMultipleIntervalsIteratorFilter(query, contained);
        Assert.assertEquals(it.compareToFilter(samRec), expectedState);
    }

    /**
     * Fills in bases for the given record to length.
     */
    private SAMRecord getSAMRecord(final int refIndex, final int start, final int length) {
        final byte[] bases = new byte[length];

        SAMFileHeader samHeader = new SAMFileHeader();
        samHeader.setSequenceDictionary(
            new SAMSequenceDictionary(
                Arrays.asList(
                    new SAMSequenceRecord("chr1", 200),
                    new SAMSequenceRecord("chr2", 200))
            )
        );
        SAMRecord samRec = new SAMRecord(samHeader);
        for (int i = 0; i < length; ++i) {
            bases[i] = BASES[random.nextInt(BASES.length)];
        }
        samRec.setReadBases(bases);
        samRec.setReferenceIndex(refIndex);
        samRec.setAlignmentStart(start);
        samRec.setCigarString(length + "M");

        return samRec;
    }

}
