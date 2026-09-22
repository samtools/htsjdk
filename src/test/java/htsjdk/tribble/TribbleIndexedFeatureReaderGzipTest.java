package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.GzipTestStreams;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TribbleIndexedFeatureReaderGzipTest extends HtsjdkTest {

    private static final String HEADER = "##fileformat=VCFv4.2\n"
            + "##contig=<ID=chr1,length=1000>\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
    private static final String RECORD_1 = "chr1\t10\t.\tA\tC\t.\t.\t.\n";
    private static final String RECORD_2 = "chr1\t20\t.\tG\tT\t.\t.\t.\n";
    private static final String RECORD_3 = "chr1\t30\t.\tC\tA\t.\t.\t.\n";

    /** Counts records read from an unindexed VCF file with the given extension, which is served by the Tribble reader. */
    private static int countRecords(final String extension, final byte[] vcfBytes) throws IOException {
        final Path vcf = Files.createTempFile("TribbleIndexedFeatureReaderGzipTest.", extension);
        IOUtil.deleteOnExit(vcf);
        Files.write(vcf, vcfBytes);

        int count = 0;
        try (AbstractFeatureReader<VariantContext, LineIterator> reader =
                AbstractFeatureReader.getFeatureReader(vcf.toString(), new VCFCodec(), false)) {
            Assert.assertTrue(reader instanceof TribbleIndexedFeatureReader);
            for (final VariantContext ignored : reader.iterator()) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void readsRecordsFromEveryMemberOfMultiMemberGzip() throws IOException {
        Assert.assertEquals(
                countRecords(".vcf.gz", GzipTestStreams.multiMemberGzip(HEADER, RECORD_1, RECORD_2, RECORD_3)), 3);
    }

    @Test
    public void readsRecordsFromEveryBlockOfBgzf() throws IOException {
        Assert.assertEquals(
                countRecords(".vcf.gz", GzipTestStreams.multiBlockBgzf(HEADER, RECORD_1, RECORD_2, RECORD_3)), 3);
    }

    @Test
    public void aGzippedFileWithoutAGzExtensionIsReadAsGzip() throws IOException {
        Assert.assertEquals(
                countRecords(".vcf", GzipTestStreams.multiMemberGzip(HEADER, RECORD_1, RECORD_2, RECORD_3)), 3);
    }

    @Test
    public void aFileWithAGzExtensionThatIsPlainTextIsReadAsText() throws IOException {
        final byte[] plainVcf = (HEADER + RECORD_1 + RECORD_2 + RECORD_3).getBytes(StandardCharsets.UTF_8);
        Assert.assertEquals(countRecords(".vcf.gz", plainVcf), 3);
    }
}
