package htsjdk.variant.vcf;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;


public class AbstractVCFCodecTest extends VariantBaseTest {

	@Test
	public void shouldPreserveSymbolicAlleleCase() {
		VCFFileReader reader = new VCFFileReader(new File(VariantBaseTest.variantTestDataRoot + "breakpoint.vcf"), false);
		VariantContext variant = reader.iterator().next();
		reader.close();
		
		// VCF v4.1 s1.4.5
		// Tools processing VCF files are not required to preserve case in the allele String, except for IDs, which are case sensitive.
		Assert.assertTrue(variant.getAlternateAllele(0).getDisplayString().contains("chr12"));
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

	@Test(dataProvider = "AllVCFCodecs", expectedExceptions = TribbleException.class)
	public void TestSpanDelParseAllelesException(final AbstractVCFCodec vcfCodec){
		vcfCodec.parseAlleles(Allele.SPAN_DEL_STRING, "A", 0);
	}

	@DataProvider(name="thingsToTryToDecode")
	public Object[][] getThingsToTryToDecode(){
		return new Object[][] {
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

}
