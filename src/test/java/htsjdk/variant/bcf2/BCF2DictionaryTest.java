package htsjdk.variant.bcf2;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.vcf.VCFContigHeaderLine;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFSimpleHeaderLine;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class BCF2DictionaryTest extends VariantBaseTest {

    @DataProvider(name = "dictionaryProvider")
    public Object[][] dictionaryProvider() {
        final List<Object[]> cases = new ArrayList<>();

        final List<VCFHeaderLine> inputLines = new ArrayList<>();
        int counter = 0;
        inputLines.add(new VCFFilterHeaderLine("l" + counter++));
        inputLines.add(new VCFFilterHeaderLine("l" + counter++));
        inputLines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", String.valueOf(counter++)), counter));
        inputLines.add(new VCFContigHeaderLine(Collections.singletonMap("ID", String.valueOf(counter++)), counter));
        inputLines.add(new VCFInfoHeaderLine("A" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        inputLines.add(new VCFInfoHeaderLine("A" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        inputLines.add(new VCFHeaderLine("x", "misc"));
        inputLines.add(new VCFHeaderLine("y", "misc"));
        inputLines.add(new VCFFilterHeaderLine("aFilter", "misc"));
        inputLines.add(new VCFFormatHeaderLine("A" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        inputLines.add(new VCFFormatHeaderLine("A" + counter++, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.Integer, "x"));
        final VCFHeader inputHeader = new VCFHeader(new LinkedHashSet<>(inputLines));

        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS) {
            final BCF2Dictionary dict = BCF2Dictionary.makeBCF2StringDictionary(inputHeader, version);
            cases.add(new Object[]{dict});
        }

        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "dictionaryProvider")
    public void testCreateDictionary(final BCF2Dictionary dict) {
        final int dict_size = dict.size();
        Assert.assertEquals(8, dict_size);
    }

    /*
    @DataProvider(name = "inconsistentIDXProvider")
    public Object[][] inconsistentIDXProvider() {
        final List<Object[]> cases = new ArrayList<>();

        // TODO can't create FILTER/FORMAT/INFO lines with arbitrary attributes
        //  should probably be addressed as part of refactoring, would be simpler and more consistent
        for (final BCFVersion version : BCFVersion.SUPPORTED_VERSIONS) {
            // String lines with inconsistent IDX
            {
                int counter = 0;
                final List<VCFHeaderLine> inputLines = new ArrayList<>();
                inputLines.add(new VCFFilterHeaderLine(String.valueOf(counter++)));
                inputLines.add(new VCFFilterHeaderLine(String.valueOf(counter++)).getGenericFieldValue());

                new VCFSimpleHeaderLine()


                final VCFHeader header = new VCFHeader(new LinkedHashSet<>(inputLines));
                final BCF2Dictionary dict = BCF2Dictionary.makeBCF2StringDictionary(header, version);
                cases.add(new Object[]{dict});
            }

            // Contig lines with inconsistent IDX
            {

            }
        }

        return cases.toArray(new Object[0][]);
    }

    @Test(expectedExceptions = {TribbleException.class})
    public void inconsistentIDX(final VCFHeader header, final BCFVersion version, final boolean string) {
        if (string) {
            BCF2Dictionary.makeBCF2StringDictionary(header, version);
        } else {
            BCF2Dictionary.makeBCF2ContigDictionary(header, version);
        }
    }
     */
}
