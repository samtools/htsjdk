/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.seekablestream;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Random;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class SeekableStreamTest extends HtsjdkTest {

    @Test
    public void testMarkAndReset() throws Exception {
        final int length = 100;
        // instantiate the stream
        final SeekableStream stream = getRandomSeekableStream(length);
        // read one byte and mark the stream
        stream.read();
        stream.mark(0);
        final int current = stream.read();
        // consume the stream
        stream.readFully(new byte[length - 2]);
        Assert.assertEquals(stream.read(), -1);
        // come back to the mark
        stream.reset();
        Assert.assertEquals(stream.read(), current);
    }

    @Test
    public void testResetUnmark() throws Exception {
        final int length = 100;
        // instantiate the stream
        final SeekableStream stream = getRandomSeekableStream(100);
        // consume the stream
        final int current = stream.read();
        stream.readFully(new byte[length - 1]);
        Assert.assertEquals(stream.read(), -1);
        stream.reset();
        Assert.assertEquals(stream.read(), current);
    }

    @Test
    public void testAvailable() throws Exception {
        // initiate random stream
        final int length = 100;
        final SeekableStream stream = getRandomSeekableStream(length);
        // check that available returns the length
        Assert.assertEquals(stream.available(), length);
        // consume the stream
        for(int i = 1; i < length + 1; i++) {
            Assert.assertNotEquals(stream.read(), -1);
            Assert.assertEquals(stream.available(), length - i);
        }
        // once consumed, no stream available
        Assert.assertEquals(stream.available(), 0);
    }

    private static SeekableStream getRandomSeekableStream(final int size) {
        // generate random array
        final byte[] array = new byte[size];
        new Random().nextBytes(array);
        // instantiate the stream
        return new SeekableMemoryStream(array, "test");
    }

}