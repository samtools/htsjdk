package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class VCFHeaderLineTranslatorUnitTest extends VariantBaseTest {

    @Test
    public void testParseVCF4HeaderLine() {
        // the following tests exercise the escaping of quotes and backslashes in VCF header lines

        // test a case with no escapes
        final Map<String, String> values = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                             "<ID=SnpCluster,Description=\"SNPs found in clusters\">",
                                                                             null);
        Assert.assertEquals(values.size(), 2);
        Assert.assertEquals(values.get("ID"), "SnpCluster");
        Assert.assertEquals(values.get("Description"), "SNPs found in clusters");

        // test escaped quotes
        final Map<String, String> values2 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION != \\\"NA\\\" || ANNOTATION <= 0.01\">",
                                                                              null);
        Assert.assertEquals(values2.size(), 2);
        Assert.assertEquals(values2.get("ID"), "ANNOTATION");
        Assert.assertEquals(values2.get("Description"), "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");

        // test escaped quotes and an escaped backslash
        final Map<String, String> values3 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\">",
                                                                              null);
        Assert.assertEquals(values3.size(), 2);
        Assert.assertEquals(values3.get("ID"), "ANNOTATION");
        Assert.assertEquals(values3.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");

        // test a header line with two value tags, one with an escaped backslash and two escaped quotes, one with an escaped quote
        final Map<String, String> values4 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\", Description2=\"foo\\\"bar\">",
                                                                              null);
        Assert.assertEquals(values4.size(), 3);
        Assert.assertEquals(values4.get("ID"), "ANNOTATION");
        Assert.assertEquals(values4.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");
        Assert.assertEquals(values4.get("Description2"), "foo\"bar");

        // test a line with a backslash that appears before something other than a quote or backslash
        final Map<String, String> values5 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\">",
                                                                              null);
        Assert.assertEquals(values5.size(), 2);
        Assert.assertEquals(values5.get("ID"), "ANNOTATION");
        Assert.assertEquals(values5.get("Description"), "ANNOTATION \\n with a newline in it");

        // test with an unclosed quote
        try {
            final Map<String, String> values6 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                                  "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it>",
                                                                                  null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        }
        catch (TribbleException.InvalidHeader e) {
        }

        // test with an escaped quote at the end
        try {
            final Map<String, String> values7 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                                  "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\\\">",
                                                                                  null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        }
        catch (TribbleException.InvalidHeader e) {
        }

    }

    @DataProvider(name = "validHeaderLines")
    private Object[][] getValidHeaderLines() {
        String line = "<ID=X,Description=\"Y\">";
        return new Object[][]{
                // to parse, required, optional
                {line, Arrays.asList("ID", "Description"), Arrays.asList()},
                {line, Arrays.asList("ID"), Arrays.asList("Description")},
                {line, Arrays.asList("ID"), Arrays.asList("Description", "Optional2")},
                {line, Arrays.asList(), Arrays.asList("Description", "ID")},
                {line, Arrays.asList("ID", "Description", "Extra"), Arrays.asList()},
                {"<>", Arrays.asList(), Arrays.asList()},
                {"<>", Arrays.asList(), Arrays.asList("ID", "Description")},
                {"<ID=X,Description=<Y>>", Arrays.asList("ID", "Description"), Arrays.asList()}
        };
    }

    @DataProvider(name = "invalidHeaderLines")
    private Object[][] getInvalidHeaderLines() {
        String line = "<ID=X,Description=\"Y\">";
        return new Object[][]{
                // to parse, required, optional, error message
                {line, Arrays.asList("Description", "ID"), Arrays.asList(), "Tag ID in wrong order"},
                {line, Arrays.asList("Description"), Arrays.asList("ID"), "Optional tag ID must be listed after all expected tags"},
                {line, Arrays.asList("ID", "Desc"), Arrays.asList(), "Unexpected tag Description"},
                {line, Arrays.asList("ID"), Arrays.asList("Desc"), "Unexpected tag Description"},
                {"<>", Arrays.asList("ID"), Arrays.asList(), "Header with no tags is not supported when there are expected tags"},
                {"<ID=X,Description=\"Y\",Extra=E>", Arrays.asList("ID"), Arrays.asList("Description"), "Unexpected tag count 3"},
        };
    }

    private static void callTranslator(final String line,
                                final List<String> expectedTagOrder,
                                final List<String> optionalTags) {
        // To cover both constructors for code coverage
        if (optionalTags.isEmpty()) {
            VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, line, expectedTagOrder);
        }
        else {
            VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, line, expectedTagOrder, optionalTags);
        }
    }

    @Test(dataProvider = "validHeaderLines")
    public void testParseVCF4HeaderLineWithTagsValid(final String line,
                                                final List<String> expectedTagOrder,
                                                final List<String> optionalTags) {
        callTranslator(line, expectedTagOrder, optionalTags);
    }
    
    @Test(dataProvider = "invalidHeaderLines")
    public void testParseVCF4HeaderLineWithTagsInvalid(final String line,
                                                     final List<String> expectedTagOrder,
                                                     final List<String> optionalTags,
                                                     final String error) {
        try {
            callTranslator(line, expectedTagOrder, optionalTags);
            Assert.fail("Expected failure: '" + error + "', got success");
        }
        catch (Exception e) {
            Assert.assertTrue(
                    e.getMessage().contains(error),
                    String.format("Error string '%s' should be present in error message '%s'", error, e.getMessage())
            );
        }
    }

    @DataProvider(name = "vcfv3")
    private Object[][] getVcfV3Versions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF3_2},
                {VCFHeaderVersion.VCF3_3}
        };
    }

    @Test(dataProvider = "vcfv3", expectedExceptions = TribbleException.class)
    public void testVcfV3FailsOptionalTags(final VCFHeaderVersion vcfVersion) {
        VCFHeaderLineTranslator.parseLine(
                vcfVersion,
                "<ID=X,Description=\"Y\">",
                Arrays.asList("ID"),
                Arrays.asList("Description")
        );
    }
}
