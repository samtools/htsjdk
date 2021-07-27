package htsjdk.beta.io.bundle;

import htsjdk.HtsjdkTest;
import htsjdk.beta.exception.HtsjdkException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class OutputStreamResourceTest extends HtsjdkTest {

    final static int TEST_STREAM_SIZE = 10;

    @Test
    public void testOutputStream() throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream(TEST_STREAM_SIZE);
        final OutputStreamResource outputStreamResource = makeOutputStreamResource(bos);

        Assert.assertTrue(outputStreamResource.hasOutputType());
        Assert.assertFalse(outputStreamResource.hasInputType());
        Assert.assertFalse(outputStreamResource.hasSeekableStream());
        Assert.assertFalse(outputStreamResource.getInputStream().isPresent());
        Assert.assertTrue(outputStreamResource.getOutputStream().isPresent());
        Assert.assertFalse(outputStreamResource.getSeekableStream().isPresent());

        final byte[] testBuffer = new byte[TEST_STREAM_SIZE];
        Arrays.fill(testBuffer, (byte) 'z');
        try (final OutputStream os = outputStreamResource.getOutputStream().get()) {
            os.write(testBuffer);
        }
        Assert.assertTrue(Arrays.equals(bos.toByteArray(), testBuffer));
    }

    @Test
    public void testGetInputStreamEmpty() {
        Assert.assertFalse(makeOutputStreamResource(new ByteArrayOutputStream(TEST_STREAM_SIZE)).getInputStream().isPresent());
    }

    @Test
    public void testGetSeekableStreamEmpty() {
        Assert.assertFalse(makeOutputStreamResource(new ByteArrayOutputStream(TEST_STREAM_SIZE)).getSeekableStream().isPresent());
    }

    @Test(expectedExceptions = HtsjdkException.class)
    public void testSignatureStreamThrows() {
        new OutputStreamResource(new ByteArrayOutputStream(TEST_STREAM_SIZE), "teststream", "contenttype")
                .getSignatureStream(10);
    }

    @Test
    public void testHashCodeAndEquality() {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream(TEST_STREAM_SIZE);

        final OutputStreamResource testBOSStreamResource1 = makeOutputStreamResource(bos);
        final OutputStreamResource testBOSStreamResource2 = makeOutputStreamResource(bos);
        final OutputStreamResource otherBOSStreamResource = makeOutputStreamResource(new ByteArrayOutputStream(5));

        Assert.assertEquals(testBOSStreamResource1, testBOSStreamResource1);
        Assert.assertNotEquals(testBOSStreamResource1, otherBOSStreamResource);
        Assert.assertNotEquals(testBOSStreamResource2, otherBOSStreamResource);

        Assert.assertEquals(testBOSStreamResource1.hashCode(), testBOSStreamResource2.hashCode());
        Assert.assertNotEquals(testBOSStreamResource1.hashCode(), otherBOSStreamResource.hashCode());
        Assert.assertNotEquals(testBOSStreamResource2.hashCode(), otherBOSStreamResource.hashCode());
    }

    private final OutputStreamResource makeOutputStreamResource(final OutputStream os) {
        return new OutputStreamResource(os, "teststream", "contenttype");
    }

}
