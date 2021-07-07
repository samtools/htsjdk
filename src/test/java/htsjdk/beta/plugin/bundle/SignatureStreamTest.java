package htsjdk.beta.plugin.bundle;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class SignatureStreamTest extends HtsjdkTest {

    @Test
    private void testReadPastEndOfSignatureBuffer() throws IOException {
        final int SIGNATURE_BUFFER_SIZE = 100;
        try (final SignatureStream sis = new SignatureStream(
                SIGNATURE_BUFFER_SIZE, new byte[SIGNATURE_BUFFER_SIZE]
        )) {
            Assert.assertEquals(sis.getSignaturePrefixLength(), SIGNATURE_BUFFER_SIZE);

            final byte[] readBuffer = new byte[SIGNATURE_BUFFER_SIZE];
            final int nRead = sis.read(readBuffer);

            Assert.assertEquals(nRead, SIGNATURE_BUFFER_SIZE);

            final int lastChar = sis.read();
            Assert.assertEquals(lastChar, -1);
        }
    }
}
