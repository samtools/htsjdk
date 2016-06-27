package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class VCFHeaderLineTranslatorUnitTest extends VariantBaseTest {

    @Test
    public void testParseVCF4HeaderLine() {
        // the following tests exercise the escaping of quotes and backslashes in VCF header lines

        // test a case with no escapes
        final Map<String,String> values = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=SnpCluster,Description=\"SNPs found in clusters\">", null);
        Assert.assertEquals(values.size(), 2);
        Assert.assertEquals(values.get("ID"), "SnpCluster");
        Assert.assertEquals(values.get("Description"), "SNPs found in clusters");

        // test escaped quotes
        final Map<String,String> values2 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION != \\\"NA\\\" || ANNOTATION <= 0.01\">", null);
        Assert.assertEquals(values2.size(), 2);
        Assert.assertEquals(values2.get("ID"), "ANNOTATION");
        Assert.assertEquals(values2.get("Description"), "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");

        // test escaped quotes and an escaped backslash
        final Map<String,String> values3 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\">", null);
        Assert.assertEquals(values3.size(), 2);
        Assert.assertEquals(values3.get("ID"), "ANNOTATION");
        Assert.assertEquals(values3.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");

        // test a header line with two value tags, one with an escaped backslash and two escaped quotes, one with an escaped quote
        final Map<String,String> values4 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\", Description2=\"foo\\\"bar\">", null);
        Assert.assertEquals(values4.size(), 3);
        Assert.assertEquals(values4.get("ID"), "ANNOTATION");
        Assert.assertEquals(values4.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");
        Assert.assertEquals(values4.get("Description2"), "foo\"bar");

        // test a line with a backslash that appears before something other than a quote or backslash
        final Map<String,String> values5 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\">", null);
        Assert.assertEquals(values5.size(), 2);
        Assert.assertEquals(values5.get("ID"), "ANNOTATION");
        Assert.assertEquals(values5.get("Description"), "ANNOTATION \\n with a newline in it");

        // test with an unclosed quote
        try {
            final Map<String, String> values6 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it>", null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        } catch (TribbleException.InvalidHeader e) {
        }

        // test with an escaped quote at the end
        try {
            final Map<String, String> values7 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\\\">", null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        } catch (TribbleException.InvalidHeader e) {
        }

    }
}
