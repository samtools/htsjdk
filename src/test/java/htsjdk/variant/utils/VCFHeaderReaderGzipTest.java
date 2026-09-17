package htsjdk.variant.utils;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.GzipTestStreams;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VCFHeaderReaderGzipTest extends HtsjdkTest {

    private static final String FILE_FORMAT = "##fileformat=VCFv4.2\n";
    private static final String CONTIG_1 = "##contig=<ID=chr1,length=1000>\n";
    private static final String CONTIG_2 = "##contig=<ID=chr2,length=2000>\n";
    private static final String COLUMNS = "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";

    /** A seekable stream of unknown length that delivers one byte per read, like a remote resource. */
    private static SeekableStream asSlowSeekableStream(final byte[] bytes) {
        return new SeekableMemoryStream(bytes, "test") {
            @Override
            public int available() {
                return 0;
            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) throws IOException {
                return super.read(buffer, offset, Math.min(length, 1));
            }
        };
    }

    @Test
    public void readsHeaderSpanningSeveralGzipMembers() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip(FILE_FORMAT, CONTIG_1, CONTIG_2, COLUMNS);
        final VCFHeader header = VCFHeaderReader.readHeaderFrom(asSlowSeekableStream(gzip));
        Assert.assertEquals(header.getContigLines().size(), 2);
    }

    @Test
    public void readsHeaderSpanningSeveralBgzfBlocks() throws IOException {
        final byte[] bgzf = GzipTestStreams.multiBlockBgzf(FILE_FORMAT, CONTIG_1, CONTIG_2, COLUMNS);
        final VCFHeader header = VCFHeaderReader.readHeaderFrom(asSlowSeekableStream(bgzf));
        Assert.assertEquals(header.getContigLines().size(), 2);
    }
}
