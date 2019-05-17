package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFTextTransformerTest extends HtsjdkTest {

    @DataProvider(name="validPercentEncodings")
    public Object[][] validPercentEncodings() {
        return new Object[][] {
                { "", ""},
                { "%3A", ":"},
                { "%3B", ";"},
                { "%3D", "="},
                { "%25", "%"},
                { "%2C", ","},
                { "%0D", "\r"},
                { "%0A", "\n"},
                { "%09", "\t"},
                { "%3AA", ":A"},
                { "abc%3A", "abc:"},
                { "%3Aabc", ":abc"},
                { "%3Aabc%3A", ":abc:"},
        };
    }

    @Test(dataProvider="validPercentEncodings")
    public void testDecodeValidEncodings(final String rawText, final String decodedText) {
        final VCFTextTransformer vcfTextTransformer = new VCFPercentEncodedTextTransformer();
        Assert.assertEquals(vcfTextTransformer.decodeText(rawText), decodedText);
    }

    @Test(dataProvider = "validPercentEncodings")
    public void testPassThruValidEncodings(final String rawText, final String unused) {
        final VCFPassThruTextTransformer vcfPassThruTransformer = new VCFPassThruTextTransformer();
        Assert.assertEquals(vcfPassThruTransformer.decodeText(rawText), rawText);
    }

    @DataProvider(name="invalidPercentEncodings")
    public Object[][] invalidPercentEncodings() {
        return new Object[][] {
                { "%3"},
                { "%d"},
                { "%a"},
                { "abcdefg%"},
                { "%3Aabcdefg%"},
                { "abcdefg%0"},
                { "abcdefg%1"},
                { "abcdefg%a"},
                { "abcdefg%d"},
                { "abcdefg%g"},
                { "abcdefg%z"},
                { "abcdefg%gg"},
                { "abcdefg%-1"},
        };
    }

    @Test(dataProvider="invalidPercentEncodings", expectedExceptions = TribbleException.class)
    public void testDecodeInvalidEncodings(final String rawText) {
        final VCFTextTransformer vcfTextTransformer = new VCFPercentEncodedTextTransformer();
        vcfTextTransformer.decodeText(rawText);
    }

}
