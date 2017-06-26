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
package htsjdk.samtools.util;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

public class AsyncWriteSortingCollectionTest extends SortingCollectionTest {

    @BeforeClass void setupClass() {System.setProperty("samjdk.sort_col_threads", "2");}

    @BeforeMethod void setup() { resetTmpDir(); }
    @AfterMethod void tearDown() { resetTmpDir(); }

    @DataProvider(name = "test1")
    public Object[][] createTestData() {
        return new Object[][] {
                {"empty", 0, 100},
                {"singleton", 1, 100},

                // maxRecordInRam for AsyncWriteSortingCollection is equals to 300 / (sort_col_threads + 1) = 100
                {"less than threshold", 100, 300},
                {"threshold minus 1", 99, 300},
                {"greater than threshold", 550, 300},
                {"threshold multiple", 600, 300},
                {"threshold multiple plus one", 101, 300},
                {"exactly threshold", 100, 300},
        };
    }
}
