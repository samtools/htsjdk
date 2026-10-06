package htsjdk.tribble;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.testutil.streams.RecordingChannelWrapper;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TabixFeatureReaderTest extends HtsjdkTest {
    private static final String TEST_PATH = "src/test/resources/htsjdk/tribble/AbstractFeatureReaderTest/";
    private static final String VCF_TABIX_BLOCK_GZIPPED = TEST_PATH + "baseVariants.vcf.gz";
    private static final String VCF_TABIX_INDEX = TEST_PATH + "baseVariants.vcf.gz.tbi";

    @Test
    public void testClosingTheReaderClosesTheStreamOfAnIteratorLeftOpen() throws IOException {
        final RecordingChannelWrapper channels = new RecordingChannelWrapper();
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path vcf = TestUtils.getTribbleFileInJimfs(VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, fs);
            final TabixFeatureReader<VariantContext, LineIterator> featureReader =
                    new TabixFeatureReader<>(vcf.toUri().toString(), null, new VCFCodec(), channels, null);
            Assert.assertEquals(featureReader.iterator().stream().count(), 26);
            featureReader.close();

            // checked before the file system is closed, as that closes every channel open on it
            Assert.assertEquals(channels.stillOpenCount(), 0);
        }
    }

    @Test
    public void testTheStreamOfAnIteratorReadToItsEndIsClosedBeforeTheReaderIs() throws IOException {
        final RecordingChannelWrapper channels = new RecordingChannelWrapper();
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path vcf = TestUtils.getTribbleFileInJimfs(VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, fs);
            try (TabixFeatureReader<VariantContext, LineIterator> featureReader =
                    new TabixFeatureReader<>(vcf.toUri().toString(), null, new VCFCodec(), channels, null)) {
                final long openWithoutIterators = channels.stillOpenCount();
                Assert.assertEquals(featureReader.iterator().stream().count(), 26);

                Assert.assertEquals(channels.stillOpenCount(), openWithoutIterators);
            }
        }
    }

    @Test
    public void testTheStreamOfAnIteratorThatFailsToStartIsClosed() throws IOException {
        final RecordingChannelWrapper channels = new RecordingChannelWrapper();
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            // The index is kept, but the file it indexed is replaced with one whose first record is malformed
            final Path vcf = TestUtils.getTribbleFileInJimfs(VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, fs);
            try (BlockCompressedOutputStream out = new BlockCompressedOutputStream(vcf)) {
                out.write(("##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"
                                + "1\tnotANumber\t.\tA\tC\t.\t.\t.\n")
                        .getBytes(StandardCharsets.US_ASCII));
            }
            final TabixFeatureReader<VariantContext, LineIterator> featureReader =
                    new TabixFeatureReader<>(vcf.toUri().toString(), null, new VCFCodec(), channels, null);
            Assert.assertThrows(TribbleException.class, featureReader::iterator);
            featureReader.close();

            Assert.assertEquals(channels.stillOpenCount(), 0);
        }
    }
}
