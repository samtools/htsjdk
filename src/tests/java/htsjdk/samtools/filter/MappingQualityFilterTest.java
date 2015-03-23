package htsjdk.samtools.filter;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class MappingQualityFilterTest {
    private final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();

    @BeforeTest
    public void setUp() {
        // note the side effects...
        builder.addFrag("zeroMQ", 1, 1, false).setMappingQuality(0);
        builder.addFrag("lowMQ", 1, 1, false).setMappingQuality(10);
        builder.addFrag("highMQ", 1, 1, false).setMappingQuality(30);
    }

    @Test(dataProvider = "data")
    public void testMappingQualityFilter(final int minMappingQuality, final int expectedPassingRecords) {
        final MappingQualityFilter filter = new MappingQualityFilter(minMappingQuality);
        int actualPassingRecords = 0;
        for (final SAMRecord rec : builder) {
            if (!filter.filterOut(rec)) actualPassingRecords++;
        }
        Assert.assertEquals(actualPassingRecords, expectedPassingRecords);
    }

    @DataProvider(name = "data")
    private Object[][] testData() {
        return new Object[][]{
                {0, 3},
                {10, 2},
                {30, 1}
        };
    }
}
