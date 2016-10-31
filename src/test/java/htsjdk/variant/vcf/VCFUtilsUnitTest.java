package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class VCFUtilsUnitTest {

    @Test (dataProvider = "percentEncodedCharsDecodeTest")
    public static void decodePercentEncodedCharsTest(String original, String expected) {
        String actual = VCFUtils.decodePercentEncodedChars(original);
        Assert.assertEquals(actual, expected,
                String.format("decodePercentEncodedChars produced invalid result decoding string '%s', expected string '%s' and received '%s'",original, expected, actual));
    }

    @Test (dataProvider = "percentEncodedCharsDecodeTestBadInput", expectedExceptions = TribbleException.VCFException.class )
    public static void decodePercentEncodedCharsTestBadInput(String original) {
        VCFUtils.decodePercentEncodedChars(original);
    }

    @Test (dataProvider = "toPercentEncodingFastTest")
    public static void toPercentEncodingFastTest(String original, String expected) {
        String actual = VCFUtils.toPercentEncodingFast(original);
        Assert.assertEquals(actual, expected,
                String.format("toPercentEncodingFast produced invalid result encoding string '%s', expected string '%s' and received '%s'",original, expected, actual));
    }

    @Test (dataProvider = "toPercentEncodingTest")
    public static void toPercentEncodingSlowTest(String original, String expected) {
        String actual = VCFUtils.toPercentEncodingSlow(original);
        Assert.assertEquals(actual, expected,
                String.format("toPercentEncodingFast produced invalid result encoding string '%s', expected string '%s' and received '%s'",original, expected, actual));
    }

    @DataProvider(name = "percentEncodedCharsDecodeTest")
    public Object[][] makePercentEncodedCharsDecodeTest() {
        final Object[][] tests = new Object[][] {
                {"ab cde","ab cde"},
                {"%41bcde","Abcde"},
                {"%2c%2524",",%24"},
                {"%41%42%43%44","ABCD"},
                {"%3B",";"},
                {"%3b",";"},
                {"",""}
        };
        return tests;
    }

    @DataProvider(name = "percentEncodedCharsDecodeTestBadInput")
    public Object[][] makePercentEncodedCharsDecodeTestBadInput() {
        final Object[][] tests = new Object[][] {
                {"%4"},//Test for incomplete end
                {"%%%%%"},//Test for non-single charater encoded stirng
                {"%GF"},//Test for codepoint >127
                {"40% of"},
        };
        return tests;
    }

    @DataProvider(name = "toPercentEncodingTest")
    public Object[][] makeToPercentEncodingTest() {
        final Object[][] tests = new Object[][] {
                {"",""},//Test for empty strings behaving
                {"abcd","abcd"},//Test for simple strings
                {", %24","%2C %2524"},//Test for mix of encoded and unencoded characters
                {"a,;:=%\n","a%2C%3B%3A%3D%25%0A"}, //test of most disalowed characters
                {"eéeü","eéeü"}//Test for >127 unicode codepoints
        };
        return tests;
    }

    @DataProvider(name = "toPercentEncodingFastTest")
    public Object[][] makeToPercentEncodingFastTest() {
        final Object[][] tests = new Object[][] {
                {"",""},//Test for empty strings behaving
                {"abcd","abcd"},//Test for simple strings
                {",\n ,%",",\n ,%25"},//Test that ONLY the percent character is being encoded
                {", ;,",", ;,"},
                {"%","%25"},
                {"abc%abc%cdb%","abc%25abc%25cdb%25"}
        };
        return tests;
    }
}
