package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.variantcontext.VariantContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Malformed genotype values are reported as TribbleExceptions naming the sample, key and position. */
public class VCFCodecMalformedGenotypeTest extends HtsjdkTest {
    private static final String HEADER = "##fileformat=VCFv4.2\n"
            + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
            + "##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n"
            + "##FORMAT=<ID=GQ,Number=1,Type=Integer,Description=\"Genotype quality\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tNA1\n";

    private static VariantContext decode(final String record) {
        final VCFCodec codec = new VCFCodec();
        codec.readActualHeader(new LineIteratorImpl(
                new SynchronousLineReader(new ByteArrayInputStream(HEADER.getBytes(StandardCharsets.UTF_8)))));
        return codec.decode(record);
    }

    @Test
    public void nonIntegerDpIsATribbleException() {
        final VariantContext vc = decode("chr1\t100\t.\tA\tC\t50\tPASS\t.\tGT:DP\t0/1:1.000");
        try {
            vc.getGenotype("NA1");
            Assert.fail("expected a TribbleException");
        } catch (final TribbleException e) {
            Assert.assertTrue(
                    e.getMessage().contains("Sample NA1 has a non-numeric DP value at chr1:100: 1.000"),
                    e.getMessage());
            Assert.assertTrue(e.getMessage().contains("line number 6"), e.getMessage());
        }
    }

    @Test
    public void nonNumericGqIsATribbleException() {
        final VariantContext vc = decode("chr1\t100\t.\tA\tC\t50\tPASS\t.\tGT:GQ\t0/1:high");
        try {
            vc.getGenotype("NA1");
            Assert.fail("expected a TribbleException");
        } catch (final TribbleException e) {
            Assert.assertTrue(e.getMessage().contains("non-numeric GQ value"), e.getMessage());
        }
    }

    @Test
    public void wellFormedValuesStillDecode() {
        final VariantContext vc = decode("chr1\t100\t.\tA\tC\t50\tPASS\t.\tGT:DP:GQ\t0/1:12:99");
        Assert.assertEquals(vc.getGenotype("NA1").getDP(), 12);
        Assert.assertEquals(vc.getGenotype("NA1").getGQ(), 99);
    }
}
