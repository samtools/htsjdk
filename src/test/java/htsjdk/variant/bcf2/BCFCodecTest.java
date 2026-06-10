package htsjdk.variant.bcf2;

import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.vcf.VCFHeader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BCFCodecTest extends VariantBaseTest {
    final String TEST_DATA_DIR = "src/test/resources/htsjdk/variant/";

    // should reject bcf v2.2 on read, see issue https://github.com/samtools/htsjdk/issues/1323
    @Test(expectedExceptions = TribbleException.class)
    private void testRejectBCFVersion22() throws IOException {
        BCF2Codec bcfCodec = new BCF2Codec();
        try (final PositionalBufferedStream pbs = new PositionalBufferedStream(
                Files.newInputStream(Path.of(TEST_DATA_DIR, "BCFVersion22Uncompressed.bcf")))) {
            bcfCodec.readHeader(pbs);
        }
    }

    @Test
    private void testBCFCustomVersionCompatibility() throws IOException {
        final BCF2Codec bcfCodec = new BCF2Codec() {
            @Override
            protected void validateVersionCompatibility(
                    final BCFVersion supportedVersion, final BCFVersion actualVersion) {
                return;
            }
        };

        // the default BCF2Codec version compatibility policy is to reject BCF 2.2 input; but make sure we can
        // provide a codec that implements a more tolerant custom policy that accepts
        try (final PositionalBufferedStream pbs = new PositionalBufferedStream(
                Files.newInputStream(Path.of(TEST_DATA_DIR, "BCFVersion22Uncompressed.bcf")))) {
            final FeatureCodecHeader featureCodecHeader = (FeatureCodecHeader) bcfCodec.readHeader(pbs);
            final VCFHeader vcfHeader = (VCFHeader) featureCodecHeader.getHeaderValue();
            Assert.assertNotEquals(vcfHeader.getMetaDataInInputOrder().size(), 0);
        }
    }
}
