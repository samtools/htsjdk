package htsjdk.beta.plugin.bundle;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class SignatureProbingInputStreamTest extends HtsjdkTest {

    @Test
    private void testReadPastEndOfSignatureBuffer() throws IOException {
        final int SIGNATURE_BUFFER_SIZE = 100;
        try (final SignatureProbingInputStream sis = new SignatureProbingInputStream(
                new byte[SIGNATURE_BUFFER_SIZE],
                SIGNATURE_BUFFER_SIZE)) {
            Assert.assertEquals(sis.getMaximumReadLength(), SIGNATURE_BUFFER_SIZE);

            final byte[] readBuffer = new byte[SIGNATURE_BUFFER_SIZE];
            final int nRead = sis.read(readBuffer);

            Assert.assertEquals(nRead, SIGNATURE_BUFFER_SIZE);

            final int lastChar = sis.read();
            Assert.assertEquals(lastChar, -1);
        }
    }
}
