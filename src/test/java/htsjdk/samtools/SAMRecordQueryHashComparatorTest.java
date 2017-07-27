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
import org.testng.annotations.Test;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class SAMRecordQueryHashComparatorTest extends HtsjdkTest {

    private static SAMRecordQueryHashComparator COMPARATOR = new SAMRecordQueryHashComparator();

    @Test
    public void testCompareDifferentNames() throws Exception {
        final SAMRecord a = new SAMRecord(null);
        a.setReadName("A");
        final SAMRecord b = new SAMRecord(null);
        b.setReadName("B");
        // hashes are providing a different order in this case
        Assert.assertTrue(COMPARATOR.compare(a, b) > 0);
        Assert.assertTrue(COMPARATOR.compare(b, a) < 0);
    }

    // with equal names, it delegates to the SAMRecordQuerynameComparator methods
    // so this should always provide the same result
    @Test(dataProvider = "equalNameComparisonData", dataProviderClass = SAMRecordQueryNameComparatorTest.class)
    public void testCompareEqualNames(final SAMRecord record1, final SAMRecord record2, final int sign) throws Exception {
        final int comparisonResult = COMPARATOR.compare(record1, record2);
        switch (sign) {
            case -1:
                Assert.assertTrue(comparisonResult < 0);
                break;
            case 0:
                Assert.assertEquals(comparisonResult, 0);
                break;
            case 1:
                Assert.assertTrue(comparisonResult > 0);
                break;
            default:
                throw new IllegalArgumentException("Invalid sign: " + sign);
        }
    }
}