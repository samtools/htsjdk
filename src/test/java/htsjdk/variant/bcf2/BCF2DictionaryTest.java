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
        inputLines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
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


    @DataProvider(name = "invalidIDXProvider")
    public Object[][] invalidIDXProvider() {
        final List<Object[]> cases = new ArrayList<>();
        // String lines with inconsistent IDX
        {
            final LinkedHashSet<VCFHeaderLine> lines = new LinkedHashSet<>();
            lines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
            lines.add(new VCFInfoHeaderLine(
                "<ID=FOO,Number=A,Type=Integer,Description=\"test\",IDX=1>",
                VCFHeader.DEFAULT_VCF_VERSION
            ));
            lines.add(new VCFInfoHeaderLine(
                "<ID=BAR,Number=A,Type=Integer,Description=\"test\">",
                VCFHeader.DEFAULT_VCF_VERSION
            ));

            final VCFHeader header = new VCFHeader(lines);
            cases.add(new Object[]{header, BCFVersion.BCF2_2Version, true});
        }
        {
            final LinkedHashSet<VCFHeaderLine> lines = new LinkedHashSet<>();
            lines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
            lines.add(new VCFInfoHeaderLine(
                "<ID=FOO,Number=A,Type=Integer,Description=\"test\">",
                VCFHeader.DEFAULT_VCF_VERSION
            ));
            lines.add(new VCFInfoHeaderLine(
                "<ID=BAR,Number=A,Type=Integer,Description=\"test\",IDX=2>",
                VCFHeader.DEFAULT_VCF_VERSION
            ));

            final VCFHeader header = new VCFHeader(lines);
            cases.add(new Object[]{header, BCFVersion.BCF2_2Version, true});
        }
        // Contig lines with inconsistent IDX
        {
            final LinkedHashSet<VCFHeaderLine> lines = new LinkedHashSet<>();
            lines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
            lines.add(new VCFContigHeaderLine(
                "<ID=chr3,Number=A,Type=Integer,Description=\"test\",IDX=3>",
                VCFHeader.DEFAULT_VCF_VERSION,
                3
            ));
            lines.add(new VCFContigHeaderLine(
                "<ID=chr4,Number=A,Type=Integer,Description=\"test\">",
                VCFHeader.DEFAULT_VCF_VERSION,
                4
            ));

            final VCFHeader header = new VCFHeader(lines);
            cases.add(new Object[]{header, BCFVersion.BCF2_2Version, false});
        }
        {
            final LinkedHashSet<VCFHeaderLine> lines = new LinkedHashSet<>();
            lines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
            lines.add(new VCFContigHeaderLine(
                "<ID=chr3,Number=A,Type=Integer,Description=\"test\">",
                VCFHeader.DEFAULT_VCF_VERSION,
                3
            ));
            lines.add(new VCFContigHeaderLine(
                "<ID=chr4,Number=A,Type=Integer,Description=\"test\",IDX=4>",
                VCFHeader.DEFAULT_VCF_VERSION,
                4
            ));

            final VCFHeader header = new VCFHeader(lines);
            cases.add(new Object[]{header, BCFVersion.BCF2_2Version, false});
        }

        // Headers with one IDX mapped to multiple strings/contigs
        {
            final LinkedHashSet<VCFHeaderLine> lines = new LinkedHashSet<>();
            lines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
            lines.add(new VCFInfoHeaderLine(
                "<ID=FOO,Number=A,Type=Integer,Description=\"test\",IDX=2>",
                VCFHeader.DEFAULT_VCF_VERSION
            ));
            lines.add(new VCFInfoHeaderLine(
                "<ID=BAR,Number=A,Type=Integer,Description=\"test\",IDX=2>",
                VCFHeader.DEFAULT_VCF_VERSION
            ));

            final VCFHeader header = new VCFHeader(lines);
            cases.add(new Object[]{header, BCFVersion.BCF2_2Version, true});
        }

        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "invalidIDXProvider", expectedExceptions = TribbleException.class)
    public void invalidIDXUsage(final VCFHeader header, final BCFVersion version, final boolean isString) {
        if (isString) {
            BCF2Dictionary.makeBCF2StringDictionary(header, version);
        } else {
            BCF2Dictionary.makeBCF2ContigDictionary(header, version);
        }
    }

    @Test
    public void testOutOfOrderAndMissingIDX() {
        final LinkedHashSet<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
        lines.add(new VCFInfoHeaderLine(
            "<ID=FOO,Number=A,Type=Integer,Description=\"test\",IDX=6>",
            VCFHeader.DEFAULT_VCF_VERSION
        ));
        lines.add(new VCFInfoHeaderLine(
            "<ID=BAR,Number=A,Type=Integer,Description=\"test\",IDX=4>",
            VCFHeader.DEFAULT_VCF_VERSION
        ));
        lines.add(new VCFInfoHeaderLine(
            "<ID=BAZ,Number=A,Type=Integer,Description=\"test\",IDX=2>",
            VCFHeader.DEFAULT_VCF_VERSION
        ));
        final VCFHeader header = new VCFHeader(lines);

        final BCF2Dictionary stringDict = BCF2Dictionary.makeBCF2StringDictionary(header, BCFVersion.BCF2_2Version);
        Assert.assertEquals(stringDict.get(6), "FOO");
        Assert.assertEquals(stringDict.get(4), "BAR");
        Assert.assertEquals(stringDict.get(2), "BAZ");
    }

    @Test
    public void testLinesWithDifferentKeySameIDShareIDX() {
        final LinkedHashSet<VCFHeaderLine> lines = new LinkedHashSet<>();
        lines.add(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
        lines.add(new VCFInfoHeaderLine(
            "<ID=FOO,Number=A,Type=Integer,Description=\"test\",IDX=2>",
            VCFHeader.DEFAULT_VCF_VERSION
        ));
        lines.add(new VCFFormatHeaderLine(
            "<ID=FOO,Number=A,Type=Integer,Description=\"test\",IDX=2>",
            VCFHeader.DEFAULT_VCF_VERSION
        ));
        lines.add(new VCFFilterHeaderLine(
            "<ID=FOO,Description=\"test\",IDX=2>",
            VCFHeader.DEFAULT_VCF_VERSION
        ));
        final VCFHeader header = new VCFHeader(lines);

        final BCF2Dictionary stringDict = BCF2Dictionary.makeBCF2StringDictionary(header, BCFVersion.BCF2_2Version);
        Assert.assertEquals(stringDict.get(2), "FOO");
    }
}
