package htsjdk.variant.variantcontext;

import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.vcf.VCFCodec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LazyGenotypesContextUnitTest extends VariantBaseTest {

    private static final String TWO_SAMPLE_HEADER = "##fileformat=VCFv4.2\n"
            + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
            + "##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1\ts2\n";

    private static final String FORMAT_AND_SAMPLES = "GT:DP\t0/1:12\t1/1:7";

    /**
     * The genotypes of a record as the VCF codec leaves them, not yet decoded. The samples are in sorted order: the
     * codec decodes the genotypes of a file whose samples are not.
     */
    private static LazyGenotypesContext undecodedGenotypes() {
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(new SynchronousLineReader(
                new ByteArrayInputStream(TWO_SAMPLE_HEADER.getBytes(StandardCharsets.UTF_8)))));
        final VariantContext vc = codec.decode("chr1\t100\t.\tA\tC\t50\tPASS\t.\t" + FORMAT_AND_SAMPLES);
        final LazyGenotypesContext genotypes = (LazyGenotypesContext) vc.getGenotypes();
        Assert.assertTrue(genotypes.isLazyWithData(), "decoded on read");
        return genotypes;
    }

    @Test
    public void toStringOfUndecodedGenotypesDoesNotDecodeThem() {
        final LazyGenotypesContext genotypes = undecodedGenotypes();
        final String text = genotypes.toString();
        Assert.assertTrue(genotypes.isLazyWithData(), "decoded by toString");
        Assert.assertEquals(text, FORMAT_AND_SAMPLES);
    }

    @Test
    public void toStringOfDecodedGenotypesListsThem() {
        final LazyGenotypesContext genotypes = undecodedGenotypes();
        genotypes.decode();
        Assert.assertEquals(genotypes.toString(), "[" + genotypes.get("s1") + "," + genotypes.get("s2") + "]");
    }

    @Test
    public void isLazyWithDataIsFalseOnceDecoded() {
        final LazyGenotypesContext genotypes = undecodedGenotypes();
        genotypes.decode();
        Assert.assertFalse(genotypes.isLazyWithData());
    }
}
