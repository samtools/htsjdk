/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.seekablestream;

import htsjdk.HtsjdkTest;
import htsjdk.testutil.ftp.LocalFtpServer;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Jim Robinson
 * @since 10/3/11
 */
public class SeekableFTPStreamTest extends HtsjdkTest {
    private static final String FILE = "/pub/test.txt";
    private static final byte[] CONTENTS = "abcdefghijklmnopqrstuvwxyz\n".getBytes(StandardCharsets.US_ASCII);

    private LocalFtpServer server;

    @BeforeMethod
    public void setUp() throws IOException {
        server = new LocalFtpServer().addFile(FILE, CONTENTS);
    }

    @AfterMethod
    public void tearDown() {
        server.close();
    }

    @Test
    public void testLength() throws Exception {
        try (final SeekableFTPStream stream = new SeekableFTPStream(server.url(FILE))) {
            Assert.assertEquals(stream.length(), CONTENTS.length);
        }
    }

    /** A buffer much larger than the file reads the whole file. */
    @Test
    public void testBufferedRead() throws Exception {
        try (final SeekableFTPStream stream = new SeekableFTPStream(server.url(FILE))) {
            final byte[] buffer = new byte[64000];
            final int nRead = stream.read(buffer);
            Assert.assertEquals(nRead, CONTENTS.length);
            Assert.assertEquals(Arrays.copyOf(buffer, nRead), CONTENTS);
        }
    }

    /** A range that extends beyond the end of the file reads to the end. */
    @Test
    public void testRange() throws Exception {
        try (final SeekableFTPStream stream = new SeekableFTPStream(server.url(FILE))) {
            stream.seek(20);
            final byte[] buffer = new byte[64000];
            final int nRead = stream.read(buffer);
            Assert.assertEquals(nRead, CONTENTS.length - 20);
            Assert.assertEquals(Arrays.copyOf(buffer, nRead), Arrays.copyOfRange(CONTENTS, 20, CONTENTS.length));
        }
    }

    /** A range that begins beyond the end of the file reads nothing. */
    @Test
    public void testBadRange() throws Exception {
        try (final SeekableFTPStream stream = new SeekableFTPStream(server.url(FILE))) {
            stream.seek(30);
            Assert.assertEquals(stream.read(new byte[64000]), -1);
        }
    }

    @Test
    public void readingAMissingFileFailsInsteadOfReadingNothing() throws Exception {
        try (final SeekableFTPStream stream = new SeekableFTPStream(server.url("/pub/missing.txt"))) {
            final IOException e = Assert.expectThrows(IOException.class, () -> stream.read(new byte[100]));
            Assert.assertTrue(e.getMessage().contains("550"), e.getMessage());
        }
    }

    @Test
    public void aPercentEncodedNameIsSentToTheServerDecoded() throws Exception {
        // the server knows the file only by its decoded name
        server.addFile("/pub/reads #1.txt", CONTENTS);
        try (final SeekableFTPStream stream = new SeekableFTPStream(server.url("/pub/reads #1.txt"))) {
            Assert.assertEquals(stream.length(), CONTENTS.length);
            final byte[] buffer = new byte[CONTENTS.length];
            Assert.assertEquals(stream.read(buffer), CONTENTS.length);
            Assert.assertEquals(buffer, CONTENTS);
        }
    }

    @Test
    public void theSourceIsTheUrlWithoutItsUserInfo() throws Exception {
        final URL withPassword = new URL("ftp://" + LocalFtpServer.USER + ":" + LocalFtpServer.PASSWORD + "@127.0.0.1:"
                + server.getPort() + "/pub/reads.bam");
        try (final SeekableFTPStream stream = new SeekableFTPStream(withPassword)) {
            Assert.assertEquals(stream.getSource(), "ftp://127.0.0.1:" + server.getPort() + "/pub/reads.bam");
        }
    }
}
