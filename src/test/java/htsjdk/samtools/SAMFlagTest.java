/*
 * The MIT License
 *
 * Author: Pierre Lindenbaum PhD @yokofakun
 *  Institut du Thorax - Nantes - France
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
package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SAMFlagTest {
    @Test
    public void testFlags() {
        Assert.assertTrue(SAMFlag.getFlags(83).contains(SAMFlag.READ_PAIRED));
        Assert.assertTrue(SAMFlag.getFlags(83).contains(SAMFlag.PROPER_PAIR));
        Assert.assertTrue(SAMFlag.getFlags(83).contains(SAMFlag.READ_REVERSE_STRAND));
        Assert.assertTrue(SAMFlag.getFlags(83).contains(SAMFlag.FIRST_OF_PAIR));
        Assert.assertFalse(SAMFlag.getFlags(83).contains(SAMFlag.READ_UNMAPPED));
        Assert.assertFalse(SAMFlag.getFlags(83).contains(SAMFlag.MATE_UNMAPPED));
        Assert.assertTrue(SAMFlag.getFlags(0).isEmpty());
        Assert.assertEquals(SAMFlag.getFlags(4095).size(),12);
    }
}
