package htsjdk.variant.vcf;

import java.io.File;
import java.util.List;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;

import org.testng.Assert;
import org.testng.annotations.Test;



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

	@Test
	public void TestSpanDelParseAlleles(){
		List<Allele> list = VCF3Codec.parseAlleles("A", Allele.SPAN_DEL_STRING, 0);
	}

	@Test(expectedExceptions = TribbleException.class)
	public void TestSpanDelParseAllelesException(){
		List<Allele> list1 = VCF3Codec.parseAlleles(Allele.SPAN_DEL_STRING, "A", 0);
	}
}
