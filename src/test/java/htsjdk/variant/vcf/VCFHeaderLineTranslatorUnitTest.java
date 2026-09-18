package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class VCFHeaderLineTranslatorUnitTest extends VariantBaseTest {

    @Test
    public void testParseVCF4HeaderLine() {
        // the following tests exercise the escaping of quotes and backslashes in VCF header lines

        // test a case with no escapes
        final Map<String, String> values = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2, "<ID=SnpCluster,Description=\"SNPs found in clusters\">", null);
        Assert.assertEquals(values.size(), 2);
        Assert.assertEquals(values.get("ID"), "SnpCluster");
        Assert.assertEquals(values.get("Description"), "SNPs found in clusters");

        // test escaped quotes
        final Map<String, String> values2 = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2,
                "<ID=ANNOTATION,Description=\"ANNOTATION != \\\"NA\\\" || ANNOTATION <= 0.01\">",
                null);
        Assert.assertEquals(values2.size(), 2);
        Assert.assertEquals(values2.get("ID"), "ANNOTATION");
        Assert.assertEquals(values2.get("Description"), "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");

        // test escaped quotes and an escaped backslash
        final Map<String, String> values3 = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2,
                "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\">",
                null);
        Assert.assertEquals(values3.size(), 2);
        Assert.assertEquals(values3.get("ID"), "ANNOTATION");
        Assert.assertEquals(values3.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");

        // test a header line with two value tags, one with an escaped backslash and two escaped quotes, one with an
        // escaped quote
        final Map<String, String> values4 = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2,
                "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\", Description2=\"foo\\\"bar\">",
                null);
        Assert.assertEquals(values4.size(), 3);
        Assert.assertEquals(values4.get("ID"), "ANNOTATION");
        Assert.assertEquals(values4.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");
        Assert.assertEquals(values4.get("Description2"), "foo\"bar");

        // test a line with a backslash that appears before something other than a quote or backslash
        final Map<String, String> values5 = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\">", null);
        Assert.assertEquals(values5.size(), 2);
        Assert.assertEquals(values5.get("ID"), "ANNOTATION");
        Assert.assertEquals(values5.get("Description"), "ANNOTATION \\n with a newline in it");

        // test with an unclosed quote
        try {
            final Map<String, String> values6 = VCFHeaderLineTranslator.parseLine(
                    VCFHeaderVersion.VCF4_2, "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it>", null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        } catch (TribbleException.InvalidHeader e) {
        }

        // test with an escaped quote at the end
        try {
            final Map<String, String> values7 = VCFHeaderLineTranslator.parseLine(
                    VCFHeaderVersion.VCF4_2,
                    "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\\\">",
                    null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        } catch (TribbleException.InvalidHeader e) {
        }
    }

    @DataProvider(name = "validHeaderLines")
    private Object[][] getValidHeaderLines() {
        List<String> idDesc = Arrays.asList("ID", "Description");
        List<Object> none = Collections.emptyList();
        List<Object> sourceVersion = Arrays.asList("Source", "Version");
        List<String> extra = Arrays.asList("Extra");
        return new Object[][] {
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
        return new Object[][] {
            // to parse, expected, recommended, error message
            {"<ID=X,Description=\"Y>", idDesc, none, "Unclosed quote"},
        };
    }

    /** Attribute order and unknown attributes are the file's business: a parser must not rely on either. */
    @Test
    public void attributesMayComeInAnyOrderAndAnyMayBePresent() {
        final List<String> idDesc = Arrays.asList("ID", "Description");
        final List<String> sourceVersion = Arrays.asList("Source", "Version");
        final Map<String, String> reordered = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2, "<Description=\"Y\",ID=X>", idDesc, sourceVersion);
        Assert.assertEquals(reordered, Map.of("ID", "X", "Description", "Y"));
        Assert.assertEquals(new ArrayList<>(reordered.keySet()), List.of("Description", "ID"));

        final Map<String, String> extra =
                VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=X,Desc=\"Y\">", idDesc, none());
        Assert.assertEquals(extra, Map.of("ID", "X", "Desc", "Y"));

        final Map<String, String> sourceFirst = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2, "<Source=\"s\",ID=X,Description=\"Y\">", idDesc, sourceVersion);
        Assert.assertEquals(sourceFirst, Map.of("Source", "s", "ID", "X", "Description", "Y"));

        Assert.assertEquals(VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<>", idDesc, none()), Map.of());
    }

    @Test
    public void onlyTheFirstEqualsSplitsKeyFromValue() {
        final Map<String, String> parsed =
                VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_3, "<ID=a=b,Description=\"x=y\">", null);
        Assert.assertEquals(parsed, Map.of("ID", "a=b", "Description", "x=y"));
    }

    @Test
    public void aQuoteInsideAnUnquotedValueIsLiteral() {
        // the hts-specs 4.3 corpus has an ALT ID made of every printable symbol, double quote included
        final String id = "complexcustomcontig!\"#$%&'()*+-./;=?@[\\]^_`{|}~";
        final Map<String, String> parsed = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_3, "<ID=" + id + ",Description=\"Valid ALT\">", null);
        Assert.assertEquals(parsed.get("ID"), id);
        Assert.assertEquals(parsed.get("Description"), "Valid ALT");
    }

    @Test
    public void quotedValuesKeepStructureCharactersAndEscapes() {
        final Map<String, String> parsed = VCFHeaderLineTranslator.parseLine(
                VCFHeaderVersion.VCF4_2, "<ID=X,Description=\"a,b=c<d> \\\"quoted\\\" back\\\\slash \\n\">", null);
        Assert.assertEquals(parsed.get("Description"), "a,b=c<d> \"quoted\" back\\slash \\n");
    }

    @Test
    public void aTokenWithoutAValueIsKeptWithAnEmptyOne() {
        final Map<String, String> parsed =
                VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, "<ID=X,Flag,Description=\"Y\">", null);
        Assert.assertEquals(parsed, Map.of("ID", "X", "Flag", "", "Description", "Y"));
    }

    private static List<String> none() {
        return Collections.emptyList();
    }

    private static void callTranslator(
            final String line, final List<String> expectedTagOrder, final List<String> recommendedTags) {
        // To cover both constructors for code coverage
        if (recommendedTags.isEmpty()) {
            VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, line, expectedTagOrder);
        } else {
            VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2, line, expectedTagOrder, recommendedTags);
        }
    }

    @Test(dataProvider = "validHeaderLines")
    public void testParseVCF4HeaderLineWithTagsValid(
            final String line, final List<String> expectedTagOrder, final List<String> recommendedTags) {
        callTranslator(line, expectedTagOrder, recommendedTags);
    }

    @Test(dataProvider = "invalidHeaderLines")
    public void testParseVCF4HeaderLineWithTagsInvalid(
            final String line,
            final List<String> expectedTagOrder,
            final List<String> recommendedTags,
            final String error) {
        final TribbleException e = Assert.expectThrows(
                TribbleException.class, () -> callTranslator(line, expectedTagOrder, recommendedTags));
        Assert.assertTrue(
                e.getMessage().contains(error),
                String.format("Error string '%s' should be present in error message '%s'", error, e.getMessage()));
    }

    @DataProvider(name = "vcfv3")
    private Object[][] getVcfV3Versions() {
        return new Object[][] {{VCFHeaderVersion.VCF3_2}, {VCFHeaderVersion.VCF3_3}};
    }

    @Test
    public void everyVcf4VersionParsesStructuredLines() {
        for (final VCFHeaderVersion version : VCFHeaderVersion.values()) {
            if (!version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0)) {
                continue;
            }
            final Map<String, String> parsed = VCFHeaderLineTranslator.parseLine(
                    version,
                    "<ID=DP,Number=1,Type=Integer,Description=\"Depth\">",
                    List.of("ID", "Number", "Type", "Description"));
            Assert.assertEquals(parsed.get("ID"), "DP", version.toString());
            Assert.assertEquals(parsed.get("Description"), "Depth", version.toString());
        }
    }

    @Test(dataProvider = "vcfv3", expectedExceptions = TribbleException.class)
    public void testVcfV3FailsRecommendedTags(final VCFHeaderVersion vcfVersion) {
        VCFHeaderLineTranslator.parseLine(
                vcfVersion, "<ID=X,Description=\"Y\">", Arrays.asList("ID"), Arrays.asList("Description"));
    }
}
