package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AbstractProgressLoggerTest extends HtsjdkTest {

    @Test
    public void padLeftPadsToTheGivenLength() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 10), "     hello");
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 6), " hello");
    }

    @Test
    public void padLeavesAStringOfExactlyTheGivenLengthUnchanged() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 5), "hello");
    }

    @Test
    public void padLeavesAStringLongerThanTheGivenLengthUnchanged() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", 4), "hello");
    }

    @Test
    public void padLeavesTheStringUnchangedForANegativeLength() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello", -1), "hello");
    }
}
