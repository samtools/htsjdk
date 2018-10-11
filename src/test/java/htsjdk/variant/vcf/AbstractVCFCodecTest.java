package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import java.io.File;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AbstractVCFCodecTest extends VariantBaseTest {

  @Test
  public void shouldPreserveSymbolicAlleleCase() {
    final VariantContext variant;
    try (final VCFFileReader reader =
        new VCFFileReader(
            new File(VariantBaseTest.variantTestDataRoot + "breakpoint.vcf"), false)) {
      variant = reader.iterator().next();
    }
    // VCF v4.1 s1.4.5
    // Tools processing VCF files are not required to preserve case in the allele String, except for
    // IDs, which are case sensitive.
    Assert.assertTrue(variant.getAlternateAllele(0).getDisplayString().contains("chr12"));
  }

  @Test
  public void TestSpanDelParseAlleles() {
    final List<Allele> list = VCF3Codec.parseAlleles("A", Allele.SPAN_DEL_STRING, 0);
  }

  @Test(expectedExceptions = TribbleException.class)
  public void TestSpanDelParseAllelesException() {
    final List<Allele> list1 = VCF3Codec.parseAlleles(Allele.SPAN_DEL_STRING, "A", 0);
  }

  @DataProvider(name = "thingsToTryToDecode")
  public Object[][] getThingsToTryToDecode() {
    return new Object[][] {
      {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf", true},
      {"src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz", true},
      {"src/test/resources/htsjdk/tribble/nonexistant.garbage", false},
      {"src/test/resources/htsjdk/tribble/testIntervalList.list", false}
    };
  }

  @Test(dataProvider = "thingsToTryToDecode")
  public void testCanDecodeFile(String potentialInput, boolean canDecode) {
    Assert.assertEquals(
        AbstractVCFCodec.canDecodeFile(potentialInput, VCFCodec.VCF4_MAGIC_HEADER), canDecode);
  }

  @Test
  public void testGetTabixFormat() {
    Assert.assertEquals(new VCFCodec().getTabixFormat(), TabixFormat.VCF);
    Assert.assertEquals(new VCF3Codec().getTabixFormat(), TabixFormat.VCF);
  }

  @Test
  public void testGLnotOverridePL() {
    final VariantContext variant;
    try (final VCFFileReader reader =
        new VCFFileReader(
            new File("src/test/resources/htsjdk/variant/test_withGLandPL.vcf"), false)) {
      variant = reader.iterator().next();
    }
    Assert.assertEquals(variant.getGenotype(0).getPL(), new int[] {45, 0, 50});
  }
}
