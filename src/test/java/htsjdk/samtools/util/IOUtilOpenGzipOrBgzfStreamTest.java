package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IOUtilOpenGzipOrBgzfStreamTest extends HtsjdkTest {

    private static String readAll(final InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void readsEveryMemberOfMultiMemberGzipFromSlowPipe() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip("first\n", "second\n", "third\n");
        final InputStream in = IOUtil.openGzipOrBgzfStream(GzipTestStreams.asSlowPipe(gzip));
        Assert.assertEquals(readAll(in), "first\nsecond\nthird\n");
    }

    @Test
    public void readsEveryBlockOfBgzfFromSlowPipe() throws IOException {
        final byte[] bgzf = GzipTestStreams.multiBlockBgzf("first\n", "second\n", "third\n");
        final InputStream in = IOUtil.openGzipOrBgzfStream(GzipTestStreams.asSlowPipe(bgzf));
        Assert.assertEquals(readAll(in), "first\nsecond\nthird\n");
    }

    @Test
    public void readsSingleMemberGzip() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip("only\n");
        final InputStream in = IOUtil.openGzipOrBgzfStream(new ByteArrayInputStream(gzip));
        Assert.assertEquals(readAll(in), "only\n");
    }

    @Test
    public void readsEmptyMemberBetweenNonEmptyMembers() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip("before\n", "", "after\n");
        final InputStream in = IOUtil.openGzipOrBgzfStream(GzipTestStreams.asSlowPipe(gzip));
        Assert.assertEquals(readAll(in), "before\nafter\n");
    }

    @Test
    public void usesBlockCompressedInputStreamForBgzf() throws IOException {
        final byte[] bgzf = GzipTestStreams.multiBlockBgzf("content\n");
        try (InputStream in = IOUtil.openGzipOrBgzfStream(new ByteArrayInputStream(bgzf))) {
            Assert.assertTrue(in instanceof BlockCompressedInputStream);
        }
    }

    @Test
    public void doesNotUseBlockCompressedInputStreamForPlainGzip() throws IOException {
        final byte[] gzip = GzipTestStreams.multiMemberGzip("content\n");
        try (InputStream in = IOUtil.openGzipOrBgzfStream(new ByteArrayInputStream(gzip))) {
            Assert.assertFalse(in instanceof BlockCompressedInputStream);
        }
    }

    @Test(expectedExceptions = IOException.class)
    public void throwsForInputThatIsNotGzip() throws IOException {
        IOUtil.openGzipOrBgzfStream(new ByteArrayInputStream("plain text\n".getBytes(StandardCharsets.UTF_8)));
    }
}
