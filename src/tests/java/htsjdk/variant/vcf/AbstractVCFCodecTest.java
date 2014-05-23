package htsjdk.variant.vcf;

import java.io.File;

import htsjdk.variant.VariantBaseTest;
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
}
