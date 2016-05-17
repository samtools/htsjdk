package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/** @author mccowan */

public class RelativeIso8601DateTest {
    @Test
    public void testLazyInstance() {
        final RelativeIso8601Date lazy = RelativeIso8601Date.generateLazyNowInstance();
        Assert.assertEquals(lazy.toString(), RelativeIso8601Date.LAZY_NOW_LABEL);
        Assert.assertEquals(lazy.toString(), RelativeIso8601Date.LAZY_NOW_LABEL);
        Assert.assertEquals(lazy.toString(), RelativeIso8601Date.LAZY_NOW_LABEL);
        Assert.assertEquals(lazy.getTime(), new Iso8601Date(new Date(System.currentTimeMillis())).getTime(), 1000); // 1 second resolution is ISO date
        // Assert no exception thrown; this should be valid, because toString should now return an iso-looking date.
        new Iso8601Date(lazy.toString());
    }

    @Test
    public void testNonLazyInstance() {
        final long time = new Iso8601Date(new Date(System.currentTimeMillis())).getTime(); // ISO strips off milliseconds

        // Test both constructor methods
        final List<RelativeIso8601Date> testDates = Arrays.<RelativeIso8601Date>asList(
                new RelativeIso8601Date(new Date(time)),
                new RelativeIso8601Date(new Iso8601Date(new Date(time)).toString())
        );

        for (final RelativeIso8601Date nonLazy : testDates) {
            Assert.assertFalse(nonLazy.toString().equals(RelativeIso8601Date.LAZY_NOW_LABEL));
            Assert.assertEquals((double) nonLazy.getTime(), (double) time);
            // Assert no exception thrown; this should be valid, because toString return an iso-looking date.
            new RelativeIso8601Date(nonLazy.toString());
        }
    }

    @Test
    public void equalityTest() {
        final String s = new Iso8601Date(new Date(12345)).toString();
        final Iso8601Date iso8601Date = new Iso8601Date(s);
        final RelativeIso8601Date relativeIso8601Date = new RelativeIso8601Date(s);
        Assert.assertEquals(relativeIso8601Date.getTime(), iso8601Date.getTime());
    }
}
