/*
 * The MIT License
 *
 * Copyright (c) 2016 Tim Fennell
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
package htsjdk.samtools.util;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for a simple phred-style quality trimming algorithm.
 */
public class TrimmingUtilTest {
    @Test
    public void testEasyCases() {
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(30,30,30,30,30, 2, 2, 2, 2, 2), 15), 5);
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(30,30,30,30,30,30,30,30,30,30), 15), 10);
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(12,12,12,12,12,12,12,12,12,12), 15), 0);
    }

    @Test
    public void testBoundaryCasesForTrimQual() {
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(12,12,12,12,12,12,12,12,12,12), 11), 10);
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(12,12,12,12,12,12,12,12,12,12), 12), 10);
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(12,12,12,12,12,12,12,12,12,12), 13), 0);
    }

    @Test
    public void testLowQualityWithOccasionalHighQuality() {
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(30,30,30, 2, 5, 2, 3,20, 2, 6), 15), 3);
    }

    @Test
    public void testAlternatingHighAndLowQuality() {
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(30, 2,30, 2,30, 2,30, 2,30, 2), 15), 9);
    }
    @Test
    public void testEmptyQuals() {
        Assert.assertEquals(TrimmingUtil.findQualityTrimPoint(byteArray(), 15), 0);
    }

    /** Makes a byte[] from a variable length argument list of ints. */
    byte[] byteArray(final int... ints) {
        final byte[] bytes = new byte[ints.length];
        for (int i=0; i<bytes.length; ++i) {
            bytes[i] = (byte) ints[i];
        }

        return bytes;
    }
}
