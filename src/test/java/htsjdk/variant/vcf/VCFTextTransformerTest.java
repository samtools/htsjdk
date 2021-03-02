package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.stream.Stream;

public class VCFTextTransformerTest extends HtsjdkTest {

    @DataProvider(name = "validPercentEncodings")
    public Object[][] validPercentEncodings() {
        return new Object[][]{
            {"", ""},
            {"%3A", ":"},
            {"%3B", ";"},
            {"%3D", "="},
            {"%25", "%"},
            {"%2C", ","},
            {"%0D", "\r"},
            {"%0A", "\n"},
            {"%09", "\t"},
            {"%3AA", ":A"},
            {"abc%3A", "abc:"},
            {"%3Aabc", ":abc"},
            {"%3Aabc%3A", ":abc:"},
        };
    }

    @DataProvider(name = "truncatedPercentEncodings")
    public Object[][] truncatedPercentEncodings() {
        return new Object[][]{
            // valid text containing % encodings that are not valid, and are passed through in raw form (no decoding)
            {"%3", "%3"},
            {"%d", "%d"},
            {"%a", "%a"},
            {"abcdefg%", "abcdefg%"},
            {"%3Aabcdefg%", ":abcdefg%"},
            {"abcdefg%0", "abcdefg%0"},
            {"abcdefg%1", "abcdefg%1"},
            {"abcdefg%a", "abcdefg%a"},
            {"abcdefg%d", "abcdefg%d"},
            {"abcdefg%g", "abcdefg%g"},
            {"abcdefg%gg", "abcdefg%gg"},
            {"abcdefg%-1", "abcdefg%-1"},
        };
    }

    @DataProvider(name = "allPercentEncodings")
    public Object[][] allPercentEncodings() {
        return Stream.concat(Arrays.stream(validPercentEncodings()), Arrays.stream(truncatedPercentEncodings()))
            .toArray(Object[][]::new);
    }

    @Test(dataProvider = "allPercentEncodings")
    public void testDecodeValidEncodings(final String encodedText, final String decodedText) {
        final VCFTextTransformer vcfTextTransformer = new VCFPercentEncodedTextTransformer();
        Assert.assertEquals(vcfTextTransformer.decodeText(encodedText), decodedText);
    }

    @Test(dataProvider = "allPercentEncodings")
    public void testPassThruValidEncodings(final String encodedText, final String unused) {
        final VCFPassThruTextTransformer vcfPassThruTransformer = new VCFPassThruTextTransformer();
        Assert.assertEquals(vcfPassThruTransformer.decodeText(encodedText), encodedText);
    }

    @Test(dataProvider = "validPercentEncodings")
    public void testInverseComposition(final String encodedText, final String decodedText) {
        final VCFTextTransformer vcfTextTransformer = new VCFPercentEncodedTextTransformer();
        Assert.assertEquals(vcfTextTransformer.encodeText(vcfTextTransformer.decodeText(encodedText)), encodedText);
        Assert.assertEquals(vcfTextTransformer.decodeText(vcfTextTransformer.encodeText(decodedText)), decodedText);
    }
}
