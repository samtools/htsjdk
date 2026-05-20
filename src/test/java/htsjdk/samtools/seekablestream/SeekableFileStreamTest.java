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
import htsjdk.samtools.util.BufferedLineReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SeekableFileStreamTest extends HtsjdkTest {

    @Test
    public void testSeek() throws Exception {
        String expectedLine = "ccccccccc";
        File testFile = new File("src/test/resources/htsjdk/samtools/seekablestream/seekTest.txt");
        SeekableFileStream is = new SeekableFileStream(testFile);
        is.seek(20);
        BufferedLineReader reader = new BufferedLineReader(is);
        String nextLine = reader.readLine();
        Assert.assertEquals(expectedLine, nextLine);
        reader.close();
    }

    // Many of the tests below verify that the locally-tracked position field stays in sync with
    // the underlying RandomAccessFile across the various mutation paths (seek, skip, the three
    // read() overloads, and EOF handling).

    private static File makeTestFile(final byte[] contents) throws IOException {
        final Path tmp = Files.createTempFile("seekable", ".bin");
        tmp.toFile().deleteOnExit();
        Files.write(tmp, contents);
        return tmp.toFile();
    }

    @Test
    public void testLengthAndInitialPosition() throws Exception {
        final byte[] contents = new byte[123];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = (byte) i;
        }
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(contents))) {
            Assert.assertEquals(s.length(), 123L);
            Assert.assertEquals(s.position(), 0L);
            Assert.assertFalse(s.eof());
            Assert.assertEquals(s.available(), 123);
        }
    }

    @Test
    public void testReadSingleByteAdvancesPosition() throws Exception {
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[] {10, 20, 30}))) {
            Assert.assertEquals(s.read(), 10);
            Assert.assertEquals(s.position(), 1L);
            Assert.assertEquals(s.read(), 20);
            Assert.assertEquals(s.position(), 2L);
            Assert.assertEquals(s.read(), 30);
            Assert.assertEquals(s.position(), 3L);
            Assert.assertEquals(s.read(), -1);
            Assert.assertEquals(s.position(), 3L); // position must not advance past EOF
            Assert.assertTrue(s.eof());
            Assert.assertEquals(s.available(), 0);
        }
    }

    @Test
    public void testReadByteArrayAdvancesPosition() throws Exception {
        final byte[] contents = new byte[100];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = (byte) i;
        }
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(contents))) {
            final byte[] dst = new byte[40];
            final int n = s.read(dst);
            Assert.assertEquals(n, 40);
            Assert.assertEquals(s.position(), 40L);
            Assert.assertEquals(s.available(), 60);
        }
    }

    @Test
    public void testReadByteArrayWithOffsetAdvancesPosition() throws Exception {
        final byte[] contents = new byte[50];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = (byte) i;
        }
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(contents))) {
            final byte[] dst = new byte[50];
            final int n = s.read(dst, 10, 30);
            Assert.assertEquals(n, 30);
            Assert.assertEquals(s.position(), 30L);
            // The first 30 source bytes should land at dst[10..40)
            for (int i = 0; i < 30; i++) {
                Assert.assertEquals(dst[10 + i], (byte) i);
            }
        }
    }

    @Test
    public void testSeekUpdatesPosition() throws Exception {
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[100]))) {
            s.seek(42);
            Assert.assertEquals(s.position(), 42L);
            Assert.assertEquals(s.available(), 58);
            Assert.assertFalse(s.eof());
            s.seek(100);
            Assert.assertEquals(s.position(), 100L);
            Assert.assertEquals(s.available(), 0);
            Assert.assertTrue(s.eof());
        }
    }

    @Test
    public void testSkipUpdatesPosition() throws Exception {
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[100]))) {
            s.skip(25);
            Assert.assertEquals(s.position(), 25L);
            s.skip(50);
            Assert.assertEquals(s.position(), 75L);
        }
    }

    @Test
    public void testEofAfterReadingAllBytes() throws Exception {
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[10]))) {
            Assert.assertFalse(s.eof());
            final byte[] dst = new byte[10];
            s.read(dst);
            Assert.assertTrue(s.eof());
            Assert.assertEquals(s.available(), 0);
        }
    }

    @Test
    public void testAvailableNeverNegative() throws Exception {
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[5]))) {
            s.seek(10); // past EOF (allowed by RandomAccessFile)
            Assert.assertEquals(s.available(), 0);
        }
    }

    @Test
    public void testReadWithLengthZeroLeavesPositionUnchanged() throws Exception {
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[100]))) {
            final byte[] dst = new byte[10];
            Assert.assertEquals(s.read(dst, 0, 0), 0);
            Assert.assertEquals(s.position(), 0L);
        }
    }

    @Test
    public void testSkipReturnsBytesActuallySkippedWithinFile() throws Exception {
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[100]))) {
            Assert.assertEquals(s.skip(40), 40L);
            Assert.assertEquals(s.position(), 40L);
        }
    }

    @Test
    public void testSkipClampedAtEof() throws Exception {
        // skip(n) requesting past EOF must return the bytes actually skipped, not the requested n.
        // Position must end exactly at file length (not beyond).
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[100]))) {
            Assert.assertEquals(s.skip(500), 100L);
            Assert.assertEquals(s.position(), 100L);
            Assert.assertTrue(s.eof());
            Assert.assertEquals(s.available(), 0);
        }
    }

    @Test
    public void testSkipNegativeReturnsZero() throws Exception {
        // InputStream.skip(n) with n <= 0 must return 0 and not move the position.
        try (final SeekableFileStream s = new SeekableFileStream(makeTestFile(new byte[100]))) {
            s.seek(50);
            Assert.assertEquals(s.skip(-10), 0L);
            Assert.assertEquals(s.position(), 50L);
        }
    }
}
