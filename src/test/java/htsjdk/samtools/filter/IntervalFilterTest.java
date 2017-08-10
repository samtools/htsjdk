package htsjdk.samtools.filter;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.util.CollectionUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

/**
 * Created by farjoun on 5/27/17.
 */
public class IntervalFilterTest extends HtsjdkTest {


    @DataProvider(name="testReadsData")
    public Iterator<Object[]> testReadsData() {

        final SAMFileHeader fileHeader;
        final IntervalList list;

        fileHeader = IntervalList.fromFile(new File("src/test/resources/htsjdk/samtools/intervallist/IntervalListchr123_empty.interval_list")).getHeader();
        fileHeader.setSortOrder(SAMFileHeader.SortOrder.unsorted);

        list = new IntervalList(fileHeader);

        list.add(new Interval("1", 50, 150));   //de-facto 1:50-150 1:301-500      2:1-150 2:250-270 2:290-400
        list.add(new Interval("1", 301, 500));
        list.add(new Interval("2", 1, 150));
        list.add(new Interval("2", 250, 270));
        list.add(new Interval("2", 300, 299)); // empty, but located.

        List<Object[]> tests = new ArrayList<>();

        SAMRecordSetBuilder builder = new SAMRecordSetBuilder();

        int readLength = 36;
        builder.setReadLength(readLength);

        int total = 0;
        int expected = 0;

        builder.addPair("abutting" + total, 0, 50 - readLength, 151); //both abutting
        total += 2;
        tests.add(new Object[]{list, fileHeader, CollectionUtil.makeCollection(builder.iterator()), expected, total});

        builder.addPair("intersecting" + total, 0, 50 - readLength + 1, 150); // both overlapping
        total += 2;
        expected += 2;
        tests.add(new Object[]{list, fileHeader, CollectionUtil.makeCollection(builder.iterator()), expected, total});

        builder.addPair("intersecting" + total, 0, 150, 200); // only the first
        total += 2;
        expected++;
        tests.add(new Object[]{list, fileHeader, CollectionUtil.makeCollection(builder.iterator()), expected, total});

        builder.addPair("intersecting" + total, 0, 1, 150); // only the second
        total += 2;
        expected++;
        tests.add(new Object[]{list, fileHeader, CollectionUtil.makeCollection(builder.iterator()), expected, total});

        builder.addFrag("intersecting_with_empty" + total, 1, 295, false); // intersects an empty interval
        total += 1;
        expected++;
        tests.add(new Object[]{list, fileHeader, CollectionUtil.makeCollection(builder.iterator()), expected, total});

        builder.addPair("clear" + total, 0, 200, 250); // not intersecting
        total += 2;
        tests.add(new Object[]{list, fileHeader, CollectionUtil.makeCollection(builder.iterator()), expected, total});

        return tests.iterator();
    }

    @Test(dataProvider = "testReadsData")
    public void testReads(final IntervalList list, final SAMFileHeader fileHeader, final Collection<SAMRecord> recordCollection,
                          final int expectedPassing, final int expectedTotal ) {
        IntervalFilter intervalFilter = new IntervalFilter(list.getIntervals(), fileHeader);
        FilteringSamIterator filteringSamIterator = new FilteringSamIterator(recordCollection.iterator(), intervalFilter);

        // check that the total number of passing reads is the expected number
        Assert.assertEquals(filteringSamIterator.stream()
                // check that the reads that pass have the word "intersecting" in their name
                .peek(s -> Assert.assertTrue(s.getReadName().contains("intersecting")))
                .count(), expectedPassing);

        //check that the total number of reads given in the Collection, is the expected number
        Assert.assertEquals(recordCollection.size(), expectedTotal);
    }
}
