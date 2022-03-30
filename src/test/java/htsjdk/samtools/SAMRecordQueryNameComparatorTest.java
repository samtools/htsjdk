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

package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.function.Consumer;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class SAMRecordQueryNameComparatorTest extends HtsjdkTest {

    private final static SAMRecordQueryNameComparator COMPARATOR = new SAMRecordQueryNameComparator();

    // this test is separated to be able to use the data provider for the SAMRecordQueryHashComparator
    @Test
    public void testCompareDifferentNames() throws Exception {
        final SAMRecord a = new SAMRecord(null);
        a.setReadName("A");
        final SAMRecord b = new SAMRecord(null);
        b.setReadName("B");
        Assert.assertTrue(COMPARATOR.compare(a, b) < 0);
        Assert.assertTrue(COMPARATOR.compare(b, a) > 0);
    }

    @DataProvider(name = "readNameOrderTestCases")
    public Object[][] readNameOrderTestCases() {
        // See: https://sourceforge.net/p/samtools/mailman/message/26674128/
        final String[] names1 = {"#01", "#2", ".1", ".09", "a001", "a01", "a01z", "a1", "a1a"};
        // See: https://github.com/samtools/samtools/blob/bdc5bb81c11f2ab25ea97351213cb87b33857c4d/test/sort/name.sort.expected.sam#L14-L28
        final String[] names2 = {"r000", "r001", "r002", "r003", "r004", "u1", "x1", "x2", "x3", "x4", "x5", "x6"};
        return new Object[][]{
            names1, names2
        };
    }

    @Test(dataProvider = "readNameOrderTestCases")
    public void testReadNameOrder(final String[] names) {
        final SAMRecord a    = new SAMRecord(null);
        final SAMRecord b    = new SAMRecord(null);
        int i, j;
        for (i = 0; i < names.length; i++) {
            for (j = 0; j < names.length; j++) {
                a.setReadName(names[i]);
                b.setReadName(names[j]);
                final int actual   = Integer.compare(COMPARATOR.compare(a, b), 0);
                final int expected = Integer.compare(i, j);
                Assert.assertEquals(actual, expected, names[i] + " < " + names[j]);
            }
        }
    }


}
