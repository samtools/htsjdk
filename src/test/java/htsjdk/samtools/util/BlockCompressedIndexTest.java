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

package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class BlockCompressedIndexTest extends HtsjdkTest {

    @DataProvider
    public Object[][] indexFiles() {
        return new Object[][] {
                // TODO - requires more test files
                {new File("src/test/resources/htsjdk/samtools/block_compressed.sam.gz.gzi"), 1}
        };
    }

    @Test(dataProvider = "indexFiles")
    public void testLoadIndex(final File indexFile, final int expectedBlocks) throws Exception {
        // test reading of the input file
        final BlockCompressedIndex index = BlockCompressedIndex.loadIndex(indexFile.toPath());
        index.getIndexEntries().forEach(System.err::println);
        Assert.assertEquals(index.getNumberOfBlocks(), expectedBlocks);
    }

    @Test(dataProvider = "indexFiles")
    public void testWriteIndex(final File indexFile, final int exprectedBlocks) throws Exception {
        // load the index and write it down
        final BlockCompressedIndex index = BlockCompressedIndex.loadIndex(indexFile.toPath());
        final File temp = File.createTempFile("testWriteIndex", indexFile.getName());
        temp.deleteOnExit();
        index.writeIndex(temp.toPath());

        // test equal byte representation on disk
        final byte[] expected = Files.readAllBytes(indexFile.toPath());
        final byte[] actual = Files.readAllBytes(temp.toPath());
        Assert.assertEquals(expected, actual);
    }

    @DataProvider
    public Object[][] filesWithIndex() {
        return new Object[][] {
                // TODO - requires more test files
                {new File("src/test/resources/htsjdk/samtools/block_compressed.sam.gz"),
                        new File("src/test/resources/htsjdk/samtools/block_compressed.sam.gz.gzi")}
        };
    }

    @Test(dataProvider = "filesWithIndex")
    public void testCreateIndex(final File fileToIndex, final File expectedIndex) throws Exception {
        // create the index for the provided file
        final BlockCompressedIndex actual = BlockCompressedIndex.createIndex(fileToIndex.toPath());
        // load the expected index to check for equality
        final BlockCompressedIndex expected = BlockCompressedIndex.loadIndex(expectedIndex.toPath());
        Assert.assertEquals(actual, expected);
    }

}