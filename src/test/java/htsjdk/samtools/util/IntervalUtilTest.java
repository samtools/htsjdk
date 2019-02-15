package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IntervalUtilTest extends HtsjdkTest {

    final SAMFileHeader header = new SAMFileHeader(SAMSequenceDictionaryExtractor.extractDictionary(
            IOUtil.getPath("src/test/resources/htsjdk/samtools/intervallist/IntervalListchr123_empty.interval_list")));
    final IntervalList intervalList = new IntervalList(header);

    public IntervalUtilTest() throws IOException {}

    @BeforeClass
    public void initialize() throws IOException {

        intervalList.add(new Interval("1", 1, 2, true, "tiny_at_1"));
        intervalList.add(new Interval("1", 2, 3, true, "tiny_at_2"));
        intervalList.add(new Interval("1", 4, 5, true, "tiny_at_4"));
    }

    @DataProvider
    public Iterator<Object[]> testMergeDifferentlyData() {
        final List<Object[]> tests = new ArrayList<>();

        IntervalList result1 = new IntervalList(header);
        result1.add(new Interval("1", 1, 5, true, "tiny_at_1|tiny_at_2|tiny_at_4"));
        tests.add(new Object[]{true, true, true, result1});

        return tests.iterator();
    }

    @Test(dataProvider = "testMergeDifferentlyData")
    public void testMergeDifferently(final boolean mergeAbutting, final boolean concatNames, final boolean requireSameStrand, final IntervalList expectedResult) {
        IntervalUtil.IntervalCombiner combiner = new IntervalUtil.IntervalCombiner();

        combiner.setCombineAbutting(mergeAbutting).setConcatenateNames(concatNames).setEnforceSameStrand(requireSameStrand);
        Assert.assertEquals(expectedResult, combiner.combine(intervalList));

    }
}