package htsjdk.samtools.filter;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Created by farjoun on 5/27/17.
 */
public class IntervalFilterTest extends HtsjdkTest {

    private final SAMFileHeader fileHeader;
    private final IntervalList list;

    public IntervalFilterTest() {
        fileHeader = IntervalList.fromFile(new File("src/test/resources/htsjdk/samtools/intervallist/IntervalListchr123_empty.interval_list")).getHeader();
        fileHeader.setSortOrder(SAMFileHeader.SortOrder.unsorted);

        list = new IntervalList(fileHeader);

        list.add(new Interval("1", 50, 150));   //de-facto 1:50-150 1:301-500      2:1-150 2:250-270 2:290-400
        list.add(new Interval("1", 301, 500));
        list.add(new Interval("2", 1, 150));
        list.add(new Interval("2", 250, 270));
        list.add(new Interval("2", 300, 299)); // empty, but located.

    }

    @Test
    public void testReads() {
        SAMRecordSetBuilder builder = new SAMRecordSetBuilder();

        int readLength = 36;
        builder.setReadLength(readLength);

        int i = 0;
        int expected = 0;
        builder.addPair("abutting" + i++, 0, 50 - readLength, 151); //both abutting
        builder.addPair("intersecting" + i++, 0, 50 - readLength + 1, 150); // both overlapping
        expected += 2;

        builder.addPair("intersecting" + i++, 0, 150, 200); // only the first
        expected++;

        builder.addPair("intersecting" + i++, 0, 1, 150); // only the second
        expected++;

        builder.addFrag("intersecting_with_empty" + i++, 1, 295, false); // intersects an empty interval
        expected++;

        builder.addPair("clear" + i++, 0, 200, 250);

        IntervalFilter intervalFilter = new IntervalFilter(list.getIntervals(), fileHeader);
        FilteringSamIterator filteringSamIterator = new FilteringSamIterator(builder.iterator(), intervalFilter);

        Assert.assertEquals(filteringSamIterator.stream()
                .peek(s -> Assert.assertTrue(s.getReadName().contains("intersecting")))
                .count(), expected);

        Assert.assertEquals(builder.iterator().stream().count(), i * 2 - 1);
    }

}