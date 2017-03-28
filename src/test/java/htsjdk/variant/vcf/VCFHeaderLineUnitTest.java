package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;


public class VCFHeaderLineUnitTest extends VariantBaseTest {

    @Test
    public void testEncodeVCFHeaderLineWithUnescapedQuotes() {

        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "VariantFiltration");
        attributes.put("CommandLineOptions", "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");

        final String encodedAttributes = VCFHeaderLine.toStringEncoding(attributes);
        assertNotNull(encodedAttributes);

        final String expectedEncoding = "<ID=VariantFiltration,CommandLineOptions=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
    }

    @Test
    public void testEncodeVCFHeaderLineWithEscapedQuotes() {

        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "VariantFiltration");
        attributes.put("CommandLineOptions", "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]");

        final String encodedAttributes = VCFHeaderLine.toStringEncoding(attributes);
        assertNotNull(encodedAttributes);

        final String expectedEncoding = "<ID=VariantFiltration,CommandLineOptions=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
    }

    @Test
    public void testIsNotStructuredHeaderLine() {
        VCFHeaderLine hl = new VCFHeaderLine("key", "value");
        Assert.assertFalse(hl.isIDHeaderLine());
        Assert.assertNull(hl.getID());
    }

    @Test
    public void testStringEncoding() {
        VCFHeaderLine hl = new VCFHeaderLine("key", "value");
        Assert.assertEquals(hl.toStringEncoding(), "key=value");
    }

    @DataProvider(name = "headerLineEquals")
    public Object[][] headerLineEquals() {
        return new Object[][]{
                {
                        new VCFHeaderLine("key", "value"),
                        new VCFHeaderLine("key", "value"),
                        true
                },
                {
                        new VCFHeaderLine("key", "value1"),
                        new VCFHeaderLine("key", "value2"),
                        false
                },
                {
                        new VCFHeaderLine("key1", "value"),
                        new VCFHeaderLine("key2", "value"),
                        false
                },
                {
                        new VCFHeaderLine("key1", "value1"),
                        new VCFHeaderLine("key2", "value2"),
                        false
                }
        };
    }

    @Test(dataProvider = "headerLineEquals")
    public void testEquals(final VCFHeaderLine hl1, final VCFHeaderLine hl2, final boolean expectedEquals) {
        Assert.assertEquals(hl1.equals(hl2), expectedEquals);
    }

    @DataProvider(name = "invalidHeaderLineKeys")
    public Object[][] invalidHeaderLineKeys() {
        return new Object[][]{
                {null},
                {"embedded<"},
                {"embedded="}};
    }

    @Test(dataProvider = "invalidHeaderLineKeys", expectedExceptions=TribbleException.class)
    public void testInvalidKeys(final String testKey) {
        new VCFHeaderLine(testKey, "");
    }

    @Test(dataProvider = "invalidHeaderLineKeys", expectedExceptions=TribbleException.class)
    public void testValidateAsIdInvalid(final String testKey) {
        VCFHeaderLine.validateKeyOrID(testKey, "test");
    }

    @DataProvider(name = "vcfVersions")
    public Object[][] vcfVersions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF3_2},
                {VCFHeaderVersion.VCF3_3},
                {VCFHeaderVersion.VCF4_0},
                {VCFHeaderVersion.VCF4_1},
                {VCFHeaderVersion.VCF4_2},
                {VCFHeaderVersion.VCF4_3}
        };
    }

    @Test(dataProvider = "vcfVersions")
    public void testValidateForVersion(final VCFHeaderVersion vcfVersion) {
        VCFHeaderLine headerLine = new VCFHeaderLine(vcfVersion.getFormatString(), vcfVersion.getVersionString());
        headerLine.validateForVersion(vcfVersion);
    }

    @DataProvider(name = "incompatibleVersions")
    public Object[][] incompatibleVersionPairs() {
        return new Object[][]{
                // each pair just has to be different
                {VCFHeaderVersion.VCF3_2, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF3_3, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF4_0, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF4_1, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF4_2, VCFHeaderVersion.VCF4_3},
                {VCFHeaderVersion.VCF4_3, VCFHeaderVersion.VCF4_2}
        };
    }

    @Test(dataProvider="incompatibleVersions", expectedExceptions=TribbleException.class)
    public void testValidateForVersionFails(final VCFHeaderVersion vcfVersion, final VCFHeaderVersion incompatibleVersion) {
        VCFHeaderLine headerLine = new VCFHeaderLine(vcfVersion.getFormatString(), vcfVersion.getVersionString());
        headerLine.validateForVersion(incompatibleVersion);
    }

    @Test(expectedExceptions = { TribbleException.InvalidHeader.class }, expectedExceptionsMessageRegExp = ".*For fixed count, the count number must be 1 or higher.")
    public void testFormatNumberExceptions() {
        new VCFFormatHeaderLine("test",
                0,
                VCFHeaderLineType.Integer,
                "");
    }

    @Test(expectedExceptions = { TribbleException.InvalidHeader.class }, expectedExceptionsMessageRegExp = ".*For fixed count, the count number must be 1 or higher.")
    public void testInfoNumberExceptions() {
        new VCFInfoHeaderLine("test",
                0,
                VCFHeaderLineType.Integer,
                "");
    }

    @Test
    public void testNumberExceptionFlag() {
        // Should not raise an exception
        new VCFInfoHeaderLine("test",
                0,
                VCFHeaderLineType.Flag,
                "");
    }
}
