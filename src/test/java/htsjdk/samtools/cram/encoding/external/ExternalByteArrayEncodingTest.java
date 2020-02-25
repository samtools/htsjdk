package htsjdk.samtools.cram.encoding.external;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExternalByteArrayEncodingTest extends HtsjdkTest {
    @Test
    public void testToString() {
        final ExternalByteArrayEncoding encoding = new ExternalByteArrayEncoding(87);
        Assert.assertTrue(encoding.toString().contains("87"));
    }
}
