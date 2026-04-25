/*
 * The MIT License
 *
 * Copyright (c) 2026 Tim Fennell
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
package htsjdk.samtools.util.zip;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests that libdeflate-backed deflater/inflater work correctly for BGZF round-trips.
 */
public class LibdeflateTest extends HtsjdkTest {

    @Test
    public void testLibdeflateAvailable() {
        Assert.assertTrue(
                DeflaterFactory.isLibdeflateAvailable(),
                "libdeflate native library should be available in test environment");
    }

    @Test
    public void testRoundTripSmallData() throws IOException {
        final byte[] original = "Hello, libdeflate BGZF world!".getBytes(StandardCharsets.UTF_8);
        final byte[] result = roundTrip(original);
        Assert.assertEquals(result, original);
    }

    @Test
    public void testRoundTripLargeRandomData() throws IOException {
        final Random random = new Random(42);
        final byte[] original = new byte[1_000_000];
        random.nextBytes(original);
        final byte[] result = roundTrip(original);
        Assert.assertEquals(result, original);
    }

    @Test
    public void testRoundTripEmptyData() throws IOException {
        final byte[] original = new byte[0];
        final byte[] result = roundTrip(original);
        Assert.assertEquals(result, original);
    }

    @Test
    public void testCompressionLevels() throws IOException {
        final byte[] original = new byte[10_000];
        new Random(123).nextBytes(original);

        for (int level = 1; level <= 9; level++) {
            final byte[] result = roundTrip(original, level);
            Assert.assertEquals(result, original, "Round-trip failed at compression level " + level);
        }
    }

    @Test
    public void testLibdeflateWriteJdkRead() throws IOException {
        final byte[] original = "Cross-implementation compatibility test".getBytes(StandardCharsets.UTF_8);

        // Write with libdeflate (default factory when libdeflate is available)
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BlockCompressedOutputStream out = new BlockCompressedOutputStream(baos, (java.io.File) null)) {
            out.write(original);
        }

        // Read with JDK inflater
        try (final BlockCompressedInputStream in =
                new BlockCompressedInputStream(new ByteArrayInputStream(baos.toByteArray()), new InflaterFactory() {
                    @Override
                    public java.util.zip.Inflater makeInflater(boolean gzipCompatible) {
                        return new java.util.zip.Inflater(gzipCompatible);
                    }
                })) {
            final byte[] result = in.readAllBytes();
            Assert.assertEquals(result, original);
        }
    }

    @Test
    public void testJdkWriteLibdeflateRead() throws IOException {
        final byte[] original = "Cross-implementation compatibility test".getBytes(StandardCharsets.UTF_8);

        // Write with JDK deflater
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BlockCompressedOutputStream out =
                new BlockCompressedOutputStream(baos, (java.io.File) null, 5, new DeflaterFactory() {
                    @Override
                    public java.util.zip.Deflater makeDeflater(int compressionLevel, boolean gzipCompatible) {
                        return new java.util.zip.Deflater(compressionLevel, gzipCompatible);
                    }
                })) {
            out.write(original);
        }

        // Read with libdeflate (default factory)
        try (final BlockCompressedInputStream in =
                new BlockCompressedInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            final byte[] result = in.readAllBytes();
            Assert.assertEquals(result, original);
        }
    }

    /** Round-trips data through BGZF using the default factories (libdeflate when available). */
    private byte[] roundTrip(final byte[] data) throws IOException {
        return roundTrip(data, 5);
    }

    private byte[] roundTrip(final byte[] data, final int compressionLevel) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final BlockCompressedOutputStream out =
                new BlockCompressedOutputStream(baos, (java.io.File) null, compressionLevel, new DeflaterFactory())) {
            out.write(data);
        }

        try (final BlockCompressedInputStream in =
                new BlockCompressedInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return in.readAllBytes();
        }
    }
}
