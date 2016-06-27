/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.EOFException;
import java.io.IOException;

public class SeekableMemoryStreamTest {

    @Test
    public void test_getSource() {
        String source = "source";
        SeekableMemoryStream stream = new SeekableMemoryStream("qwe".getBytes(), source);
        Assert.assertEquals(stream.getSource(), source);
    }

    @Test
    public void test_EOF() throws IOException {
        SeekableMemoryStream stream = new SeekableMemoryStream(new byte[]{}, null);
        Assert.assertTrue(stream.eof());
        Assert.assertEquals(stream.read(), -1);
        Assert.assertTrue(stream.eof());
    }

    @Test
    public void test_read_byte() throws IOException {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        SeekableMemoryStream stream = new SeekableMemoryStream(data, null);

        for (int i = 0; i < data.length; i++) {
            byte expectedByteValue = (byte) i;
            Assert.assertEquals((byte) stream.read(), expectedByteValue);
        }
    }

    @Test
    public void test_read_into_array() throws IOException {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        SeekableMemoryStream stream = new SeekableMemoryStream(data, null);

        byte[] copy = new byte[data.length];

        int length = data.length;
        int numberOfBytesReadSoFar = 0, maxBytesPerRead = 11;
        while (numberOfBytesReadSoFar < length) {
            final int count = stream.read(copy, numberOfBytesReadSoFar, Math.min(maxBytesPerRead, length - numberOfBytesReadSoFar));
            if (count < 0) {
                throw new EOFException();
            }
            numberOfBytesReadSoFar += count;
        }

        Assert.assertEquals(copy, data);
    }

    @Test(expectedExceptions = IOException.class)
    public void test_reset() throws IOException {
        SeekableMemoryStream stream = new SeekableMemoryStream("qwe".getBytes(), null);
        stream.mark(3);
        stream.reset();
    }
}
