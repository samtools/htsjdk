package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class DuplicateScoringStrategyTest extends HtsjdkTest {

    @DataProvider
    public Object [][] compareData() {
        return new Object[][]{
                {SAMFlag.READ_PAIRED.flag, 0, true, DuplicateScoringStrategy.ScoringStrategy.RANDOM, -1},
                {0, SAMFlag.READ_PAIRED.flag, true, DuplicateScoringStrategy.ScoringStrategy.RANDOM, 1},
        };
    }

    @Test(dataProvider = "compareData")
    public static void testCompare(final int samFlag1, final int samFlag2, final boolean assumeMateCigar, final DuplicateScoringStrategy.ScoringStrategy strategy, final int expected) {
        final SAMRecord rec1 = new SAMRecordSetBuilder().addFrag("test", 0, 1, false, false, "36M", null, 2);
        rec1.setFlags(samFlag1);
        final SAMRecord rec2 = new SAMRecordSetBuilder().addFrag("test", 0, 1, true, false, "36M", null, 3);
        rec2.setFlags(samFlag2);
        Assert.assertEquals(DuplicateScoringStrategy.compare(rec1, rec2, strategy, assumeMateCigar), expected);
    }
}