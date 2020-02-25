package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExternalIntegerEncodingTest extends HtsjdkTest {

    @Test
    public void testToString() {
        final ExternalIntegerEncoding encoding = new ExternalIntegerEncoding(87);
        Assert.assertTrue(encoding.toString().contains("87"));
    }
}
