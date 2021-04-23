package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Iterator;
import java.util.List;


public class AbstractVCFCodecTest extends VariantBaseTest {

    @Test
    public void shouldPreserveSymbolicAlleleCase() {
        final VariantContext variant;
        try (final VCFFileReader reader = new VCFFileReader(new File(VariantBaseTest.variantTestDataRoot + "breakpoint.vcf"), false)) {
            variant = reader.iterator().next();

        }
        // VCF v4.1 s1.4.5
        // Tools processing VCF files are not required to preserve case in the allele String, except for IDs, which are case sensitive.
        Assert.assertTrue(variant.getAlternateAllele(0).getDisplayString().contains("chr12"));
    }

    @Test
    public void TestSpanDelParseAlleles() {
        final List<Allele> list = VCF3Codec.parseAlleles("A", Allele.SPAN_DEL_STRING, 0);
    }
	@DataProvider(name="AllVCFCodecs")
	public Object[][] allVCFCodecVersions() {
		return new Object[][] {
				{new VCF3Codec() },
				{new VCFCodec() },
				//{new VCF43Codec ()}
		};
	}

	@Test(dataProvider = "AllVCFCodecs")
	public void TestSpanDelParseAlleles(final AbstractVCFCodec vcfCodec){
		// TODO: why is there no Assert here ??
		vcfCodec.parseAlleles("A", Allele.SPAN_DEL_STRING, 0);
	}

    @Test(expectedExceptions = TribbleException.class)
    public void TestSpanDelParseAllelesException() {
        final List<Allele> list1 = VCF3Codec.parseAlleles(Allele.SPAN_DEL_STRING, "A", 0);
    }
	@Test(dataProvider = "AllVCFCodecs", expectedExceptions = TribbleException.class)
	public void TestSpanDelParseAllelesException(final AbstractVCFCodec vcfCodec){
		vcfCodec.parseAlleles(Allele.SPAN_DEL_STRING, "A", 0);
	}

    @DataProvider(name = "thingsToTryToDecode")
    public Object[][] getThingsToTryToDecode() {
        return new Object[][]{
                {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf", true},
                {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz", true},
                {"src/test/resources/htsjdk/tribble/nonexistant.garbage", false},
                {"src/test/resources/htsjdk/tribble/testIntervalList.list", false}
        };
    }

	@Test(dataProvider = "thingsToTryToDecode")
	public void testCanDecodeFile(String potentialInput, boolean canDecode) {
		//TODO: add VCF43Codec when available
		//TODO: its not sufficient to test for ANY v4 prefix since it will succeed on 4.3 as well
		Assert.assertEquals(AbstractVCFCodec.canDecodeFile(potentialInput, VCFCodec.VCF4_MAGIC_HEADER), canDecode);
	}

	@Test(dataProvider = "AllVCFCodecs")
	public void testGetTabixFormat(final AbstractVCFCodec vcfCodec) {
		Assert.assertEquals(vcfCodec.getTabixFormat(), TabixFormat.VCF);
	}

	@DataProvider(name="otherHeaderLines")
	public Object[][] otherHeaderLines() {
		return new Object[][] {
                { "key=<", new VCFHeaderLine("key", "<") },
                // taken from Funcotator test file as ##ID=<Description="ClinVar Variation ID">
                // technically, this is invalid due to the lack of an "ID" attribute, but it should still parse
                // into a VCFHeaderLine (but noa VCFSimpleHeaderLine
                { "ID=<Description=\"ClinVar Variation ID\">",
                    new VCFHeaderLine("ID", "<Description=\"ClinVar Variation ID\">") },
		};
	}

	@Test(dataProvider="otherHeaderLines")
	public void testGetOtherHeaderLine(final String headerLineString, final VCFHeaderLine headerLine) {
		Assert.assertEquals(new VCFCodec().getOtherHeaderLine(headerLineString, VCFHeaderVersion.VCF4_2), headerLine);
	}

	@DataProvider(name="badOtherHeaderLines")
	public Object[][] badOtherHeaderLines() {
		return new Object[][] {
				{ "=" },
				{ "=<" },
                { "=<>" },
                { "key" },
		};
	}

	@Test(dataProvider="badOtherHeaderLines", expectedExceptions=TribbleException.InvalidHeader.class)
	public void testBadOtherHeaderLine(final String headerLineString) {
		Assert.assertNull(new VCFCodec().getOtherHeaderLine(headerLineString, VCFHeaderVersion.VCF4_2));
	}

    @Test
    public void testGLnotOverridePL() {
        final VariantContext variant;
        try (final VCFFileReader reader = new VCFFileReader(
                new File("src/test/resources/htsjdk/variant/test_withGLandPL.vcf"), false)) {
            variant = reader.iterator().next();
        }
        Assert.assertEquals(variant.getGenotype(0).getPL(), new int[]{45, 0, 50});
    }

    @DataProvider(name = "caseIntolerantDoubles")
    public Object[][] getCaseIntolerantDoubles() {
        return new Object[][]{
                {"src/test/resources/htsjdk/variant/test_withNanQual.vcf", Double.NaN},
                {"src/test/resources/htsjdk/variant/test_withPosInfQual.vcf", Double.POSITIVE_INFINITY},
                {"src/test/resources/htsjdk/variant/test_withNegInfQual.vcf", Double.NEGATIVE_INFINITY},
        };
    }

    @Test(dataProvider = "caseIntolerantDoubles")
    public void testCaseIntolerantDoubles(String vcfInput, double value) {
        try (final VCFFileReader reader = new VCFFileReader(new File(vcfInput), false)) {
            try {
                Iterator<VariantContext> iterator = reader.iterator();
                final VariantContext baseVariant = iterator.next(); // First row uses Java-style
                Assert.assertEquals(baseVariant.getPhredScaledQual(), value);
                iterator.forEachRemaining(v -> {
                    Assert.assertEquals(baseVariant.getPhredScaledQual(), v.getPhredScaledQual());
                    Assert.assertEquals(baseVariant.getGenotype(0).getGQ(), v.getGenotype(0).getGQ());
                });
            } catch (TribbleException e) {
                Assert.assertEquals(value, Double.NEGATIVE_INFINITY); // QUAL cannot be negative
            }
        }
    }
}
