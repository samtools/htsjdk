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
import htsjdk.samtools.util.CollectionUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;

public class SAMRecordQueryNameNaturalComparatorTest extends HtsjdkTest {
    private final static SAMRecordQueryNameNaturalComparator COMPARATOR = new SAMRecordQueryNameNaturalComparator();

    @Test
    public void testCompareDifferentNames() throws Exception {
        // Example names in order from the SAMv1 spec
        final List<String> names = CollectionUtil.makeList(
                "abc", "abc+5", "abc- 5", "abc.d", "abc03", "abc5",
                "abc008", "abc08", "abc8", "abc17", "abc17.+",
                "abc17.2", "abc17.d", "abc59", "abcd"
        );
        final List<String> sorted = names.stream().sorted(COMPARATOR::compare).collect(Collectors.toList());

        Assert.assertEquals(sorted, names);
    }

}
