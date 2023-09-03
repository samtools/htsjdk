package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.Tuple;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.variantcontext.VariantContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class VCFCodec42FeaturesTest extends HtsjdkTest {
    private static final Path TEST_PATH = Paths.get("src/test/resources/htsjdk/variant/");

    @Test
    public void testV42PedigreeParsing() {
        // make sure a vcf4.2 PEDIGREE header line is NOT modeled as a VCFPedigreeHeaderLine like it is
        // in vcf4.3, since those are structured VCFIDHeaderLines that require an ID
        final Path vcfWithPedigreeHeaderLine = TEST_PATH.resolve("vcf42HeaderLines.vcf");
        final VCFHeader headerWithPedigree = new VCFFileReader(vcfWithPedigreeHeaderLine, false).getFileHeader();
        final VCFHeaderLine vcf42PedigreeLine = headerWithPedigree.getMetaDataInInputOrder()
                .stream().filter((l) -> l.getKey().equals(VCFConstants.PEDIGREE_HEADER_KEY)).findFirst().get();
        Assert.assertEquals(vcf42PedigreeLine.getClass(), VCFHeaderLine.class);
        Assert.assertEquals(vcf42PedigreeLine.getValue(), "<Derived=NA12891, Original=NA12878>");
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testVCF42RejectsInfoFieldWithSpaces() {
        // 1st variant has an info field with a value containing an embedded space
        final Path infoSpace42File = TEST_PATH.resolve("infoSpace42.vcf");

        try ( final VCFFileReader vcfReader = new VCFFileReader(infoSpace42File, false) ){
            for (final VariantContext vc : vcfReader) {

            }
        } catch (final TribbleException e) {
            Assert.assertTrue(e.getMessage().contains("Whitespace is not allowed"));
            throw e;
        }
    }
}
