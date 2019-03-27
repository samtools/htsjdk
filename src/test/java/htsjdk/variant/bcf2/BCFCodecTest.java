package htsjdk.variant.bcf2;

import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BCFCodecTest extends VariantBaseTest {
    final String TEST_DATA_DIR = "src/test/resources/htsjdk/variant/";

    // should reject bcf v2.2 on read, see issue https://github.com/samtools/htsjdk/issues/1323
    @Test(expectedExceptions = TribbleException.class)
    private void testRejectBCFVersion22() throws IOException {
        BCF2Codec bcfCodec = new BCF2Codec();
        try (final FileInputStream fis = new FileInputStream(new File(TEST_DATA_DIR, "BCFVersion22Uncompressed.bcf"));
             final PositionalBufferedStream pbs = new PositionalBufferedStream(fis)) {
            bcfCodec.readHeader(pbs);
        }
    }

    @Test
    private void testBCFCustomVersionCompatibility() throws IOException {
        final BCF2Codec bcfCodec = new BCF2Codec() {
            @Override
            protected void validateVersionCompatibility(final BCFVersion supportedVersion, final BCFVersion actualVersion) {
                // assert the precondition for this test to be valid
                Assert.assertNotEquals(supportedVersion, actualVersion);
                Assert.assertTrue(actualVersion.majorVersion == BCF2Codec.ALLOWED_MAJOR_VERSION && actualVersion.minorVersion != BCF2Codec.ALLOWED_MINOR_VERSION);
            }
        };

        // the default BCF2Codec version compatibility policy is to reject BCF 2.2 input; but make sure we can
        // provide a codec that implements a more tolerant custom policy that accepts
        try (final FileInputStream fis = new FileInputStream(new File(TEST_DATA_DIR, "BCFVersion22Uncompressed.bcf"));
             final PositionalBufferedStream pbs = new PositionalBufferedStream(fis)) {
            bcfCodec.readHeader(pbs);
        }
    }


}

