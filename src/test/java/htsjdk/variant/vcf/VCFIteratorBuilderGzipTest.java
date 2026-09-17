package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.GzipTestStreams;
import java.io.IOException;
import java.io.InputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VCFIteratorBuilderGzipTest extends HtsjdkTest {

    private static final String HEADER = "##fileformat=VCFv4.2\n"
            + "##contig=<ID=chr1,length=1000>\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
    private static final String RECORD_1 = "chr1\t10\t.\tA\tC\t.\t.\t.\n";
    private static final String RECORD_2 = "chr1\t20\t.\tG\tT\t.\t.\t.\n";
    private static final String RECORD_3 = "chr1\t30\t.\tC\tA\t.\t.\t.\n";

    private static int countRecords(final InputStream in) throws IOException {
        int count = 0;
        try (VCFIterator iterator = new VCFIteratorBuilder().open(in)) {
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    @Test
    public void readsRecordsFromEveryMemberOfMultiMemberGzip() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip(HEADER, RECORD_1, RECORD_2, RECORD_3);
        Assert.assertEquals(countRecords(GzipTestStreams.asSlowPipe(gzip)), 3);
    }

    @Test
    public void readsRecordsFromEveryBlockOfBgzf() throws IOException {
        final byte[] bgzf = GzipTestStreams.multiBlockBgzf(HEADER, RECORD_1, RECORD_2, RECORD_3);
        Assert.assertEquals(countRecords(GzipTestStreams.asSlowPipe(bgzf)), 3);
    }

    @Test
    public void readsHeaderSplitAcrossMembers() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip(
                "##fileformat=VCFv4.2\n",
                "##contig=<ID=chr1,length=1000>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n",
                RECORD_1);
        try (VCFIterator iterator = new VCFIteratorBuilder().open(GzipTestStreams.asSlowPipe(gzip))) {
            Assert.assertEquals(iterator.getHeader().getContigLines().size(), 1);
            Assert.assertTrue(iterator.hasNext());
        }
    }
}
