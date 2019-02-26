package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AbstractProgressLoggerTest extends HtsjdkTest {

    @Test
    public void testPad() {
        Assert.assertEquals(AbstractProgressLogger.pad("hello",10),"     hello");
        Assert.assertEquals(AbstractProgressLogger.pad("hello",6)," hello");
        Assert.assertEquals(AbstractProgressLogger.pad("hello",5),"hello");
        Assert.assertEquals(AbstractProgressLogger.pad("hello",4),"hello");
        Assert.assertEquals(AbstractProgressLogger.pad("hello",-1),"hello");
    }
}