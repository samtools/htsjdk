package htsjdk.samtools.filter;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class InsertSizeFilterTest {
    private static final int READ_LENGTH = 20;
    private final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();

    @BeforeTest
    public void setUp() {
        builder.setReadLength(READ_LENGTH);
        builder.addFrag("mapped_unpaired", 1, 1, false);
        builder.addUnmappedPair("unmapped_paired"); // insert size = 0
        builder.addPair("mapped_paired_short", 1, 1, 31); // insert size = 50
        builder.addPair("mapped_paired_long", 1, 1, 81); // insert size = 100
        builder.addPair("mapped_paired_long_flipped", 1, 81, 1); // insert size = 100
    }

    @Test(dataProvider = "data")
    public void testInsertSizeFilter(final int minInsertSize, final int maxInsertSize, final int expectedPassingRecords) {
        final InsertSizeFilter filter = new InsertSizeFilter(minInsertSize, maxInsertSize);
        int actualPassingRecords = 0;
        for (final SAMRecord rec : builder) {
            if (!filter.filterOut(rec)) actualPassingRecords++;
        }
        Assert.assertEquals(actualPassingRecords, expectedPassingRecords);
    }

    @DataProvider(name = "data")
    private Object[][] testData() {
        return new Object[][]{
                {0, 0, 2},
                {50, 50, 2},
                {50, 100, 6},
                {0, Integer.MAX_VALUE, 8}
        };
    }
}
