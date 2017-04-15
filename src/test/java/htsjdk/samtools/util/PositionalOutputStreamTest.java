/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Daniel Gomez-Sanchez
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

package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class PositionalOutputStreamTest extends HtsjdkTest {

    @Test
    public void basicPositionTest() throws Exception {
        // wrapped null output stream to check
        final PositionalOutputStream wrapped = new PositionalOutputStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {}
        });
        int position = 0;
        // check that we start at position 0
        Assert.assertEquals(wrapped.getPosition(), position);
        // check that write one int just add one
        wrapped.write(100);
        Assert.assertEquals(wrapped.getPosition(), ++position);
        // check that write a byte array adds its length
        final byte[] bytes = new byte[]{1, 3, 5, 7};
        wrapped.write(bytes);
        position += bytes.length;
        Assert.assertEquals(wrapped.getPosition(), position);
        // check that write just some bytes from an array adds its length
        wrapped.write(bytes, 2, 2);
        position += 2;
        Assert.assertEquals(wrapped.getPosition(), position);
    }

}
