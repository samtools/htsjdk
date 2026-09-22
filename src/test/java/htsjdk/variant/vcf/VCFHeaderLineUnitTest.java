package htsjdk.variant.vcf;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import htsjdk.variant.VariantBaseTest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testng.annotations.Test;

public class VCFHeaderLineUnitTest extends VariantBaseTest {

    @Test
    public void testEncodeVCFHeaderLineWithUnescapedQuotes() {

        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "VariantFiltration");
        attributes.put(
                "CommandLineOptions",
                "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \"NA\" || ANNOTATION <= 2.0]");

        final String encodedAttributes = VCFHeaderLine.toStringEncoding(attributes);
        assertNotNull(encodedAttributes);

        final String expectedEncoding =
                "<ID=VariantFiltration,CommandLineOptions=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
    }

    /**
     * A value is taken as it is, not as text someone has already escaped: a backslash before a quote is a backslash,
     * and is escaped along with the quote, so that the value reads back exactly as it was given.
     */
    @Test
    public void testEncodeVCFHeaderLineWithBackslashesBeforeQuotes() {

        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "VariantFiltration");
        attributes.put(
                "CommandLineOptions",
                "filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\"NA\\\" || ANNOTATION <= 2.0]");

        final String encodedAttributes = VCFHeaderLine.toStringEncoding(attributes);
        assertNotNull(encodedAttributes);

        final String expectedEncoding =
                "<ID=VariantFiltration,CommandLineOptions=\"filterName=[ANNOTATION] filterExpression=[ANNOTATION == \\\\\\\"NA\\\\\\\" || ANNOTATION <= 2.0]\">";
        assertEquals(encodedAttributes, expectedEncoding);
        assertEquals(
                VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_3, encodedAttributes, null)
                        .get("CommandLineOptions"),
                attributes.get("CommandLineOptions"));
    }

    @Test(
            expectedExceptions = {IllegalArgumentException.class},
            expectedExceptionsMessageRegExp =
                    "Invalid count number, with fixed count the number should be 1 or higher: .*")
    public void testFormatNumberExeptions() {
        new VCFFormatHeaderLine("test", 0, VCFHeaderLineType.Integer, "");
    }

    @Test(
            expectedExceptions = {IllegalArgumentException.class},
            expectedExceptionsMessageRegExp =
                    "Invalid count number, with fixed count the number should be 1 or higher: .*")
    public void testInfoNumberExeptions() {
        new VCFInfoHeaderLine("test", 0, VCFHeaderLineType.Integer, "");
    }

    @Test
    public void testNumberExceptionFlag() {
        // Should not raise an exception
        new VCFInfoHeaderLine("test", 0, VCFHeaderLineType.Flag, "");
    }

    @Test
    public void quotesAndBackslashesInAQuotedValueSurviveARoundTrip() {
        // a leading quote, a quote after a backslash, two backslashes together and a trailing backslash
        final String description = "\"quoted\" a\\\"b c\\\\d e\\";
        final VCFInfoHeaderLine line = new VCFInfoHeaderLine("X", 1, VCFHeaderLineType.String, description);
        final VCFInfoHeaderLine reread =
                new VCFInfoHeaderLine(line.toString().substring("INFO=".length()), VCFHeaderVersion.VCF4_3);
        assertEquals(reread.getDescription(), description);
        assertEquals(reread, line);
    }

    @Test
    public void anIdIsNeverQuoted() {
        // other tools take quotes around an ID to be part of it, and then nothing in the records matches it
        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ID", "q=10");
        attributes.put("URL", "http://x?y=z");
        assertEquals(VCFHeaderLine.toStringEncoding(attributes), "<ID=q=10,URL=\"http://x?y=z\">");
    }

    @Test
    public void aPlainLineCanBeWrittenAsAnyVersionTheWritersProduce() {
        assertEquals(new VCFHeaderLine("source", "x").minimumVersion(), VCFHeaderVersion.VCF4_0);
    }
}
