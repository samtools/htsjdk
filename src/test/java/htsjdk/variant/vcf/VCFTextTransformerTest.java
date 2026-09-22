package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFTextTransformerTest extends HtsjdkTest {

    @DataProvider(name = "validPercentEncodings")
    public Object[][] validPercentEncodings() {
        return new Object[][] {
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

    @Test(dataProvider = "validPercentEncodings")
    public void testDecodeValidEncodings(final String rawText, final String decodedText) {
        final VCFTextTransformer vcfTextTransformer = new VCFPercentEncodedTextTransformer();
        Assert.assertEquals(vcfTextTransformer.decodeText(rawText), decodedText);
    }

    @Test(dataProvider = "validPercentEncodings")
    public void testPassThruValidEncodings(final String rawText, final String unused) {
        final VCFPassThruTextTransformer vcfPassThruTransformer = new VCFPassThruTextTransformer();
        Assert.assertEquals(vcfPassThruTransformer.decodeText(rawText), rawText);
    }

    // percentEncode

    @Test
    public void percentEncodeReturnsTheSameInstanceWhenNothingNeedsEncoding() {
        final String plain = "no special characters here";
        Assert.assertSame(VCFPercentEncodedTextTransformer.percentEncode(plain), plain);
    }

    @Test
    public void percentEncodeEncodesEachOfTheEightSpecialCharactersInUpperCaseHex() {
        Assert.assertEquals(VCFPercentEncodedTextTransformer.percentEncode("%:;=,\r\n\t"), "%25%3A%3B%3D%2C%0D%0A%09");
    }

    @Test
    public void percentEncodeLeavesOtherCharactersAlone() {
        Assert.assertEquals(VCFPercentEncodedTextTransformer.percentEncode("a b/c|d&e#日本"), "a b/c|d&e#日本");
    }

    @Test
    public void percentEncodeEncodesOnlyTheSpecialCharactersOfAMixedValue() {
        Assert.assertEquals(VCFPercentEncodedTextTransformer.percentEncode("key=50%;x"), "key%3D50%25%3Bx");
    }

    @Test
    public void percentEncodeIsUndoneByDecodeText() {
        final String value = "a=b;c:d,e%f\tg";
        final String encoded = VCFPercentEncodedTextTransformer.percentEncode(value);
        Assert.assertEquals(new VCFPercentEncodedTextTransformer().decodeText(encoded), value);
    }

    @Test
    public void percentEncodeJoinedListKeepsTheCommas() {
        Assert.assertEquals(VCFPercentEncodedTextTransformer.percentEncodeJoinedList("a;b,c=d,e"), "a%3Bb,c%3Dd,e");
    }

    @Test
    public void percentEncodeJoinedListReturnsTheSameInstanceWhenOnlyCommasArePresent() {
        final String list = "a,b,c";
        Assert.assertSame(VCFPercentEncodedTextTransformer.percentEncodeJoinedList(list), list);
    }
}
