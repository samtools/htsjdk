/*
 * The MIT License
 *
 * Copyright (c) 2017 Nils Homer
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
package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SAMFileHeaderTest extends HtsjdkTest {

    @Test
    public void testSortOrder() {
        final SAMFileHeader header = new SAMFileHeader();

        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        Assert.assertEquals(header.getSortOrder(), SAMFileHeader.SortOrder.coordinate);

        header.setAttribute(SAMFileHeader.SORT_ORDER_TAG, SAMFileHeader.SortOrder.queryname.name());
        Assert.assertEquals(header.getSortOrder(), SAMFileHeader.SortOrder.queryname);
    }

    @Test
    public void testGroupOrder() {
        final SAMFileHeader header = new SAMFileHeader();

        header.setGroupOrder(SAMFileHeader.GroupOrder.query);
        Assert.assertEquals(header.getGroupOrder(), SAMFileHeader.GroupOrder.query);

        header.setAttribute(SAMFileHeader.GROUP_ORDER_TAG, SAMFileHeader.GroupOrder.reference.name());
        Assert.assertEquals(header.getGroupOrder(), SAMFileHeader.GroupOrder.reference);
    }
}
