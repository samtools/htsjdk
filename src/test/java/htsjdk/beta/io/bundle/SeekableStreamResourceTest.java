package htsjdk.beta.io.bundle;

import htsjdk.HtsjdkTest;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.io.IOPath;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class SeekableStreamResourceTest extends HtsjdkTest {
    final static int TEST_STREAM_SIZE = 10;
    final byte[] testBuffer = "zzzzzzzzzz".getBytes();

    @Test
    public void testSeekableStream() throws IOException {
        final SeekableStreamResource seekableStreamResource = makeSeekableStreamResource(testBuffer);

        Assert.assertFalse(seekableStreamResource.hasOutputType());
        Assert.assertTrue(seekableStreamResource.hasInputType());
        Assert.assertTrue(seekableStreamResource.hasSeekableStream());
        Assert.assertTrue(seekableStreamResource.getInputStream().isPresent());
        Assert.assertFalse(seekableStreamResource.getOutputStream().isPresent());
        Assert.assertTrue(seekableStreamResource.getSeekableStream().isPresent());

        final byte[] roundTripBuffer = new byte[TEST_STREAM_SIZE];
        try (final SeekableStream ss = seekableStreamResource.getSeekableStream().get()) {
            ss.read(roundTripBuffer);
            Assert.assertEquals(roundTripBuffer, testBuffer);

            // overwrite the test results, reset the stream, and read again
            Arrays.fill(roundTripBuffer, (byte) '9');
            ss.reset();
            ss.read(roundTripBuffer);
            Assert.assertEquals(roundTripBuffer, testBuffer);
        }
    }

    @Test
    public void testGetInputStream() throws IOException {
        Assert.assertTrue(makeSeekableStreamResource(testBuffer).getInputStream().isPresent());
    }

    @Test
    public void testGetSeekableStream() throws IOException {
        Assert.assertTrue(makeSeekableStreamResource(testBuffer).getSeekableStream().isPresent());
    }

    @Test
    public void testGetOutputStream() throws IOException {
        Assert.assertFalse(makeSeekableStreamResource(testBuffer).getOutputStream().isPresent());
    }

    @Test
    public void testGetSignatureStream() throws IOException {
        try (final SignatureStream signatureStream = makeSeekableStreamResource(testBuffer)
                .getSignatureStream(TEST_STREAM_SIZE)) {
            final byte[] roundTripBuffer = new byte[TEST_STREAM_SIZE];
            signatureStream.read(roundTripBuffer);
            Assert.assertEquals(roundTripBuffer, testBuffer);
        }
    }

    @Test
    public void testHashCodeAndEquality() throws IOException {

        final SeekableStreamResource testSeekableStreamResource1 = makeSeekableStreamResource(testBuffer);
        final SeekableStreamResource testSeekableStreamResource2 = makeSeekableStreamResource(testBuffer);
        final SeekableStreamResource otherSeekableStreamResource = makeSeekableStreamResource(new byte[5]);

        Assert.assertEquals(testSeekableStreamResource1, testSeekableStreamResource1);
        Assert.assertNotEquals(testSeekableStreamResource1, otherSeekableStreamResource);
        Assert.assertNotEquals(testSeekableStreamResource2, otherSeekableStreamResource);

        Assert.assertEquals(testSeekableStreamResource1.hashCode(), testSeekableStreamResource1.hashCode());
        Assert.assertNotEquals(testSeekableStreamResource1.hashCode(), testSeekableStreamResource2.hashCode());
        Assert.assertNotEquals(testSeekableStreamResource1.hashCode(), otherSeekableStreamResource.hashCode());
        Assert.assertNotEquals(testSeekableStreamResource2.hashCode(), otherSeekableStreamResource.hashCode());
    }

    private final SeekableStreamResource makeSeekableStreamResource(final byte[] testBuffer) throws IOException {
        final IOPath ioPath = IOPathUtils.createTempPath("testSeekable", ".txt");
        try (final FileOutputStream fos = new FileOutputStream(ioPath.toPath().toFile().toString())) {
            fos.write(testBuffer);
        }

        final SeekableStreamResource seekableStreamResource = new SeekableStreamResource(
                new SeekablePathStream(ioPath.toPath()),
                "contenttype",
                ioPath.getRawInputString());

        return seekableStreamResource;
    }

}
