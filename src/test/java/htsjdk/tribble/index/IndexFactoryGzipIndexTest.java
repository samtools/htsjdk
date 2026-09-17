package htsjdk.tribble.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.GzipTestStreams;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.variant.vcf.VCFCodec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IndexFactoryGzipIndexTest extends HtsjdkTest {

    private static final String VCF = "##fileformat=VCFv4.2\n"
            + "##contig=<ID=chr1,length=1000>\n"
            + "##contig=<ID=chr2,length=1000>\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
            + "chr1\t10\t.\tA\tC\t.\t.\t.\n"
            + "chr2\t20\t.\tG\tT\t.\t.\t.\n";

    @Test
    public void loadsGzippedIndexSplitAcrossMembers() throws IOException {
        final Path vcf = Files.createTempFile("IndexFactoryGzipIndexTest.", ".vcf");
        IOUtil.deleteOnExit(vcf);
        Files.write(vcf, VCF.getBytes(StandardCharsets.UTF_8));
        final Index expected = IndexFactory.createLinearIndex(vcf, new VCFCodec());

        final ByteArrayOutputStream serialized = new ByteArrayOutputStream();
        try (LittleEndianOutputStream out = new LittleEndianOutputStream(serialized)) {
            expected.write(out);
        }
        final byte[] indexBytes = serialized.toByteArray();
        final int midpoint = indexBytes.length / 2;
        final byte[] gzippedIndex = GzipTestStreams.multiMemberGzip(
                Arrays.copyOfRange(indexBytes, 0, midpoint),
                Arrays.copyOfRange(indexBytes, midpoint, indexBytes.length));

        final Path gzippedIndexPath = Files.createTempFile("IndexFactoryGzipIndexTest.", ".vcf.idx.gz");
        IOUtil.deleteOnExit(gzippedIndexPath);
        Files.write(gzippedIndexPath, gzippedIndex);

        final Index loaded = IndexFactory.loadIndex(gzippedIndexPath.toString());
        Assert.assertEquals(loaded.getSequenceNames(), expected.getSequenceNames());
        Assert.assertTrue(loaded.equalsIgnoreProperties(expected));
    }
}
