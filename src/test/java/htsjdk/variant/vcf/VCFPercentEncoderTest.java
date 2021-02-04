package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFPercentEncoderTest extends HtsjdkTest {

    @DataProvider(name = "validPercentEncodings")
    public Object[][] validPercentEncodings() {
        return new Object[][]{
            {"", ""},
            {":", "%3A"},
            {";", "%3B"},
            {"=", "%3D"},
            {"%", "%25"},
            {",", "%2C"},
            {"\r", "%0D"},
            {"\n", "%0A"},
            {"\t", "%09"},
            {":A", "%3AA"},
            {"abc:", "abc%3A"},
            {":abc", "%3Aabc"},
            {":abc:", "%3Aabc%3A"},
        };
    }

    @Test(dataProvider = "validPercentEncodings")
    public void testDecodeValidEncodings(final String rawText, final String encodedText) {
        Assert.assertEquals(VCFPercentEncoder.percentEncode(rawText), encodedText);
    }
}
