package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.rans.RANS;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ExternalCompressionTest extends HtsjdkTest {
    public static final File BZIP2_FILE = new File("src/test/resources/htsjdk/samtools/cram/io/bzip2-test.bz2");
    public static final byte[] TEST_BYTES = "This is a simple string to test compression".getBytes();

    @Test
    public void testBZip2Decompression() throws IOException {
        final byte [] input = Files.readAllBytes(BZIP2_FILE.toPath());
        final byte [] output = ExternalCompression.unbzip2(input);
        Assert.assertEquals(output, "BZip2 worked".getBytes());
    }

    @Test
    public void testGZipRoundtrip() throws IOException {
        final byte [] compressed = ExternalCompression.gzip(TEST_BYTES);
        final byte [] restored = ExternalCompression.gunzip(compressed);
        Assert.assertEquals(TEST_BYTES, restored);
    }

    @Test
    public void testBZip2Roundtrip() throws IOException {
        final byte [] compressed = ExternalCompression.bzip2(TEST_BYTES);
        final byte [] restored = ExternalCompression.unbzip2(compressed);
        Assert.assertEquals(TEST_BYTES, restored);
    }

    @Test
    public void testRANSRoundtrip() {
        for(RANS.ORDER order : RANS.ORDER.values()) {
            final byte[] compressed = ExternalCompression.rans(TEST_BYTES, order);
            final byte[] restored = ExternalCompression.unrans(compressed);
            Assert.assertEquals(TEST_BYTES, restored);
        }
    }

    @Test
    public void testXZRoundtrip() throws IOException {
        final byte [] compressed = ExternalCompression.xz(TEST_BYTES);
        final byte [] restored = ExternalCompression.unxz(compressed);
        Assert.assertEquals(TEST_BYTES, restored);
    }


}
