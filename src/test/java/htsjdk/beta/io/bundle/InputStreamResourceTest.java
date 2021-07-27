package htsjdk.beta.io.bundle;

import htsjdk.HtsjdkTest;
import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.io.IOPath;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class InputStreamResourceTest extends HtsjdkTest {
    final static int TEST_STREAM_SIZE = 10;
    final byte[] testBuffer = "zzzzzzzzzz".getBytes();

    @Test
    public void testInputStream() throws IOException {
        final InputStreamResource inputStreamResource = makeInputStreamResource(testBuffer);

        Assert.assertFalse(inputStreamResource.hasOutputType());
        Assert.assertTrue(inputStreamResource.hasInputType());
        Assert.assertFalse(inputStreamResource.hasSeekableStream());
        Assert.assertTrue(inputStreamResource.getInputStream().isPresent());
        Assert.assertFalse(inputStreamResource.getOutputStream().isPresent());
        Assert.assertFalse(inputStreamResource.getSeekableStream().isPresent());

        final byte[] roundTripBuffer = new byte[TEST_STREAM_SIZE];
        try (final InputStream is = inputStreamResource.getInputStream().get()) {
            is.read(roundTripBuffer);
            Assert.assertEquals(roundTripBuffer, testBuffer);
        }
    }

    @Test
    public void testGetInputStream() throws IOException {
        Assert.assertTrue(makeInputStreamResource(testBuffer).getInputStream().isPresent());
    }

    @Test
    public void testGetSeekableStream() throws IOException {
        Assert.assertFalse(makeInputStreamResource(testBuffer).getSeekableStream().isPresent());
    }

    @Test
    public void testGetOutputStream() throws IOException {
        Assert.assertFalse(makeInputStreamResource(testBuffer).getOutputStream().isPresent());
    }

    @Test
    public void testGetSignatureStream() throws IOException {
        try (final SignatureStream signatureStream = makeInputStreamResource(testBuffer)
                .getSignatureStream(TEST_STREAM_SIZE)) {
            final byte[] roundTripBuffer = new byte[TEST_STREAM_SIZE];
            signatureStream.read(roundTripBuffer);
            Assert.assertEquals(roundTripBuffer, testBuffer);
        }
    }

    @Test(expectedExceptions = HtsjdkException.class)
    public void testSerialGetSignatureStreamThrows() throws IOException {
        final InputStreamResource inputStreamResource = makeInputStreamResource(testBuffer);
        try (final SignatureStream signatureStream = inputStreamResource.getSignatureStream(TEST_STREAM_SIZE)) {
            final byte[] roundTripBuffer = new byte[TEST_STREAM_SIZE];
            signatureStream.read(roundTripBuffer);
            Assert.assertEquals(roundTripBuffer, testBuffer);
        }

        //throws
        inputStreamResource.getSignatureStream(TEST_STREAM_SIZE);
    }

    @Test
    public void testHashCodeAndEquality() throws IOException {
        final InputStreamResource testBufferStreamResource1 = makeInputStreamResource(testBuffer);
        final InputStreamResource testBufferStreamResource2 = makeInputStreamResource(testBuffer);
        final InputStreamResource otherBufferStreamResource = makeInputStreamResource(new byte[5]);

        Assert.assertEquals(testBufferStreamResource1, testBufferStreamResource1);
        Assert.assertNotEquals(testBufferStreamResource1, testBufferStreamResource2);
        Assert.assertNotEquals(testBufferStreamResource1, otherBufferStreamResource);
        Assert.assertNotEquals(testBufferStreamResource2, otherBufferStreamResource);

        Assert.assertEquals(testBufferStreamResource1.hashCode(), testBufferStreamResource1.hashCode());
        Assert.assertNotEquals(testBufferStreamResource1.hashCode(), testBufferStreamResource2.hashCode());
        Assert.assertNotEquals(testBufferStreamResource1.hashCode(), otherBufferStreamResource.hashCode());
        Assert.assertNotEquals(testBufferStreamResource2.hashCode(), otherBufferStreamResource.hashCode());
    }

    private final InputStreamResource makeInputStreamResource(final byte[] testBuffer) throws IOException {
        final IOPath ioPath = IOPathUtils.createTempPath("testSeekable", ".txt");
        try (final FileOutputStream fos = new FileOutputStream(ioPath.toPath().toFile().toString())) {
            fos.write(testBuffer);
        }

        final InputStreamResource inputStreamResource = new InputStreamResource(
                new FileInputStream(ioPath.toPath().toFile()),
                "contenttype",
                ioPath.getRawInputString());

        return inputStreamResource;
    }
}
