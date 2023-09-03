package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
            final Map<String,String> values6 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it>", null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        }
        catch (TribbleException.InvalidHeader e) {
        }

        // test with an escaped quote at the end
        try {
            final Map<String,String> values7 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\\\">", null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        }
        catch (TribbleException.InvalidHeader e) {
        }

    }

    @DataProvider(name = "validHeaderLines")
    private Object[][] getValidHeaderLines() {
        List<String> idDesc = Arrays.asList("ID", "Description");
        List<Object> none = Collections.emptyList();
        List<Object> sourceVersion = Arrays.asList("Source", "Version");
        List<String> extra = Arrays.asList("Extra");
        return new Object[][]{
                // to parse, expected, recommended
                {"<ID=X,Description=\"Y\",Source=\"source\",Version=\"1.2.3\">", idDesc, sourceVersion},
                {"<ID=X,Description=\"Y\",Source=\"source\">", idDesc, sourceVersion},
                {"<ID=X,Description=\"Y\",Version=\"1.2.3\">", idDesc, sourceVersion},
                {"<ID=X,Description=\"Y\">", idDesc, sourceVersion},
                {"<ID=X>", idDesc, sourceVersion},
                {"<ID=X,Description=\"Y\",Extra=\"extra\",Source=\"source\",Version=\"1.2.3\">", idDesc, sourceVersion},

                {"<ID=X>", idDesc, none},
                {"<ID=X,Description=\"Y\">", idDesc, none},
                {"<ID=X,Description=\"Y\",Extra=\"extra\">", idDesc, none},
                {"<ID=X,Description=<Y>>", idDesc, none},
                {"<ID=X,Description=\"Y\",Extra=E>", idDesc, none},

                {"<ID=X,Description=\"Y\",Extra=\"extra\">", idDesc, extra},
                {"<ID=X,Description=\"Y\">", idDesc, extra},
                {"<>", none, none},
                {"<>", none, extra},
                {"<>", none, sourceVersion}
        };
    }
    
    @DataProvider(name = "invalidHeaderLines")
    private Object[][] getInvalidHeaderLines() {
        List<String> idDesc = Arrays.asList("ID", "Description");
        List<Object> none = Collections.emptyList();
        List<String> sourceVersion = Arrays.asList("Source", "Version");
        return new Object[][]{
                // to parse, expected, recommended, error message
                {"<Description=\"Y\",ID=X>", idDesc, none, "Unexpected tag or tag order for tag \"Description\""},
                {"<ID=X,Desc=\"Y\">", idDesc, none, "Unexpected tag or tag order for tag \"Desc\""},
                {"<>", idDesc, none, "Unexpected tag or tag order for tag \"\""},

                {"<Source=\"source\",ID=X,Description=\"Y\">", idDesc, sourceVersion,
                        "Unexpected tag or tag order for tag \"Source\""},
                {"<ID=X,Source=\"E\",Description=\"Y\">", idDesc, sourceVersion,
                        "Unexpected tag or tag order for tag \"Source\""}
        };
    }

    private static void callTranslator(final String line,
                                final List<String> expectedTagOrder,
                                final List<String> recommendedTags) {
        // To cover both constructors for code coverage
        if (recommendedTags.isEmpty()) {
            VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, line, expectedTagOrder);
        }
        else {
            VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, line, expectedTagOrder);
        }
    }

    @Test(dataProvider = "validHeaderLines")
    public void testParseVCF4HeaderLineWithTagsValid(final String line,
                                                final List<String> expectedTagOrder,
                                                final List<String> recommendedTags) {
        callTranslator(line, expectedTagOrder, recommendedTags);
    }
    
    @Test(dataProvider = "invalidHeaderLines")
    public void testParseVCF4HeaderLineWithTagsInvalid(final String line,
                                                     final List<String> expectedTagOrder,
                                                     final List<String> recommendedTags,
                                                     final String error) {
        final TribbleException e = Assert.expectThrows(
                TribbleException.class,
                () -> callTranslator(line, expectedTagOrder, recommendedTags)
        );
        Assert.assertTrue(
                e.getMessage().contains(error),
                String.format("Error string '%s' should be present in error message '%s'", error, e.getMessage())
        );
    }

    @DataProvider(name = "vcfv3")
    private Object[][] getVcfV3Versions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF3_2},
                {VCFHeaderVersion.VCF3_3}
        };
    }

}
