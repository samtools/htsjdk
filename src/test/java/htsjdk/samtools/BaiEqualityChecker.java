/*
 * The MIT License
 *
 * Copyright (c) 2019 The Broad Institute
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

import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import java.nio.file.Path;
import org.testng.Assert;

/** Asserts that two BAI files hold the same index, reference by reference so that a failure says where. */
public class BaiEqualityChecker {

    public static void assertEquals(final Path baiFile1, final Path baiFile2) {
        final BinningIndex bai1 = load(baiFile1);
        final BinningIndex bai2 = load(baiFile2);

        Assert.assertEquals(bai1.getReferenceCount(), bai2.getReferenceCount(), "Number of references");
        Assert.assertEquals(bai1.getNoCoordinateCount(), bai2.getNoCoordinateCount(), "No coordinate index count");
        for (int i = 0; i < bai1.getReferenceCount(); i++) {
            Assert.assertEquals(bai1.getReference(i), bai2.getReference(i), "Reference " + i);
        }
    }

    private static BinningIndex load(final Path baiFile) {
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(baiFile, true)) {
            return index.loadAll();
        }
    }
}
