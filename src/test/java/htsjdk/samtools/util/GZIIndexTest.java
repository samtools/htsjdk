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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class GZIIndexTest extends HtsjdkTest {

    @DataProvider
    public Object[][] indexFiles() {
        return new Object[][] {
                {new File("src/test/resources/htsjdk/samtools/block_compressed.sam.gz.gzi"), 2},
                {new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz.gzi"), 17}
        };
    }

    @Test(dataProvider = "indexFiles")
    public void testLoadIndex(final File indexFile, final int expectedBlocks) throws Exception {
        // test reading of the input file
        final GZIIndex index = GZIIndex.loadIndex(indexFile.toPath());
        Assert.assertEquals(index.getNumberOfBlocks(), expectedBlocks);
    }

    @Test(dataProvider = "indexFiles")
    public void testWriteIndex(final File indexFile, final int exprectedBlocks) throws Exception {
        // load the index and write it down
        final GZIIndex index = GZIIndex.loadIndex(indexFile.toPath());
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
                {new File("src/test/resources/htsjdk/samtools/block_compressed.sam.gz"),
                        new File("src/test/resources/htsjdk/samtools/block_compressed.sam.gz.gzi")},
                {new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz"),
                        new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz.gzi")}
        };
    }

    @Test(dataProvider = "filesWithIndex")
    public void testBuildIndex(final File fileToIndex, final File expectedIndex) throws Exception {
        // create the index for the provided file
        final GZIIndex actual = GZIIndex.buildIndex(fileToIndex.toPath());
        // load the expected index to check for equality
        final GZIIndex expected = GZIIndex.loadIndex(expectedIndex.toPath());
        Assert.assertEquals(actual, expected);
    }

    @DataProvider
    public Iterator<Object[]> virtualOffsetForSeekData() throws Exception {
        // wer use the index from the FASTA file for testing seek
        final GZIIndex index = GZIIndex.loadIndex(new File("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz.gzi").toPath());
        final List<Object[]> data = new ArrayList<>(2 * index.getNumberOfBlocks() + 3);
        // position 0
        data.add(new Object[]{0, 0, 0, index});
        // postion 10 bytes before the first index
        final GZIIndex.IndexEntry firstFileEntry = index.getIndexEntries().get(0);
        data.add(new Object[]{firstFileEntry.getUncompressedOffset() - 10, 0, firstFileEntry.getUncompressedOffset() - 10, index});

        // add for each entry 2 tests (the entry itself and 10 bytes after the block)
        for (final GZIIndex.IndexEntry entry: index.getIndexEntries()) {
            // add to the test data the offset for the beginning of each block
            data.add(new Object[]{entry.getUncompressedOffset(), entry.getCompressedOffset(), 0, index});
            // and also the offset for 10 bytes after the block
            data.add(new Object[]{entry.getUncompressedOffset() + 10, entry.getCompressedOffset(), 10, index});
        }

        return data.iterator();
    }

    @Test(dataProvider = "virtualOffsetForSeekData")
    public void testGetVirtualOffsetForSeek(final long uncompressedOffset,
            final long expectedBlockAddress, final long expectedBlockOffset,
            final GZIIndex index) throws Exception {
        final long virtualOffset = index.getVirtualOffsetForSeek(uncompressedOffset);
        Assert.assertEquals(BlockCompressedFilePointerUtil.getBlockAddress(virtualOffset), expectedBlockAddress);
        Assert.assertEquals(BlockCompressedFilePointerUtil.getBlockOffset(virtualOffset), expectedBlockOffset);
    }

}