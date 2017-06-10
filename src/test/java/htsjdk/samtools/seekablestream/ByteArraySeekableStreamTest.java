/*
 * The MIT License
 *
 * Copyright (c) 2017 The Broad Institute
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
 */

package htsjdk.samtools.seekablestream;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * Created by farjoun on 5/27/17.
 */
public class ByteArraySeekableStreamTest extends HtsjdkTest {
    private final byte[] bytes = "ABCDE12345".getBytes();

    @Test
    public void testNormalBehavior() throws IOException {
        ByteArraySeekableStream byteArraySeekableStream = new ByteArraySeekableStream(bytes);

        Assert.assertEquals(byteArraySeekableStream.length(), 10);
        for (int i = 0; i < 10; i++) {
            Assert.assertFalse(byteArraySeekableStream.eof());
            Assert.assertEquals(byteArraySeekableStream.position(), i);
            Assert.assertEquals(byteArraySeekableStream.read(), bytes[i]);
        }

        Assert.assertTrue(byteArraySeekableStream.eof());
        Assert.assertEquals(byteArraySeekableStream.position(), 10);
        Assert.assertEquals(byteArraySeekableStream.read(), -1);

        final long i = 0;
        byteArraySeekableStream.seek(i);

        Assert.assertEquals(byteArraySeekableStream.position(), i);
        Assert.assertEquals(byteArraySeekableStream.read(), bytes[(int) i]);

        byte[] copy = new byte[10];

        Assert.assertEquals(byteArraySeekableStream.read(copy), 9);
        Assert.assertEquals(byteArraySeekableStream.position(), 10);

        byteArraySeekableStream.seek(0L);

        Assert.assertEquals(byteArraySeekableStream.read(copy), 10);
        Assert.assertEquals(byteArraySeekableStream.position(), 10);

        Assert.assertEquals(copy, bytes);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCantSeekNegative() throws IOException {

        ByteArraySeekableStream byteArraySeekableStream = new ByteArraySeekableStream(bytes);

        byteArraySeekableStream.seek(-1L);

        // if allowed to seek, this will throw OutOfBounds
        final int f = byteArraySeekableStream.read();
    }

    @Test
    public void testCantReadPostEof() throws IOException {

        ByteArraySeekableStream byteArraySeekableStream = new ByteArraySeekableStream(bytes);
        byte[] copy = new byte[10];

        byteArraySeekableStream.seek(10);
        Assert.assertEquals(byteArraySeekableStream.read(copy), -1);
        Assert.assertEquals(byteArraySeekableStream.read(), -1);
    }

    @DataProvider(name = "abnormalReadRequests")
    public Object[][] abnormalReadRequestsProvider() {
        return new Object[][]{
                {new byte[10], -1, 0},
                {new byte[10], -1, -1},
                {new byte[10], 0, -1},
                {new byte[10], 0, -1},
                {new byte[10], 0, 11},
                {new byte[10], 6, 6},
                {new byte[10], 11, 0},
        };
    }

    @Test(dataProvider = "abnormalReadRequests", expectedExceptions = IndexOutOfBoundsException.class)
    public void testAbnormalReadRequest(final byte[] b, final int off, final int length) throws IOException {

        ByteArraySeekableStream byteArraySeekableStream = new ByteArraySeekableStream(bytes);
        int i = byteArraySeekableStream.read(b, off, length);

        Assert.assertEquals(i, -2); ///impossible
    }
}
