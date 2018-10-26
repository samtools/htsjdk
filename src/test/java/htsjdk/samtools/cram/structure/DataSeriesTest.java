package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

public class DataSeriesTest extends HtsjdkTest {
    @Test()
    public void twoCharsPositiveTest() {
        for (final DataSeries series : DataSeries.values()) {
            final String twoChars = series.name().substring(0, 2);
            Assert.assertEquals(series.getCanonicalName(), twoChars);
            Assert.assertEquals(DataSeries.byCanonicalName(twoChars), series);
        }
    }

    @DataProvider(name = "twoCharsNegative")
    public static Object[][] twoCharsNegative() {
        return new Object[][] {
                {"AA"},
                {"ZZ"},
                {"no good"},
                {""},
                {"BBBBBBBB"}
        };
    }

    @Test(dataProvider = "twoCharsNegative", expectedExceptions = CRAMException.class)
    public void twoCharsNegativeTest(final String badKey) {
        DataSeries.byCanonicalName(badKey);
    }

    @Test
    public void externalBlockContentIdTest() {
        // requirements: unique positive integers

        final Set<Integer> ids = new HashSet<>(DataSeries.values().length);
        for (DataSeries ds : DataSeries.values()) {
            final Integer id = ds.getExternalBlockContentId();
            Assert.assertTrue(id > 0);
            ids.add(id);
        }

        Assert.assertEquals(ids.size(), DataSeries.values().length);
    }
}
