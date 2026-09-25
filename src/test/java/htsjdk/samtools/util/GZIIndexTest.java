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
import htsjdk.samtools.seekablestream.SeekableFileStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class GZIIndexTest extends HtsjdkTest {

    @DataProvider
    public Object[][] indexFiles() {
        return new Object[][] {
            {Paths.get("src/test/resources/htsjdk/samtools/block_compressed.sam.gz.gzi"), 2},
            {Paths.get("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz.gzi"), 17}
        };
    }

    @Test(dataProvider = "indexFiles")
    public void testLoadIndex(final Path indexFile, final int expectedBlocks) throws Exception {
        // test reading of the input file
        final GZIIndex index = GZIIndex.loadIndex(indexFile);
        Assert.assertEquals(index.getNumberOfBlocks(), expectedBlocks);
    }

    @Test(dataProvider = "indexFiles")
    public void testLoadIndexFromStream(final Path indexFile, final int expectedBlocks) throws Exception {
        try (InputStream in = Files.newInputStream(indexFile)) {
            final GZIIndex index = GZIIndex.loadIndex(indexFile.toString(), in);
            Assert.assertEquals(index.getNumberOfBlocks(), expectedBlocks);
        }
    }

    @Test(dataProvider = "indexFiles")
    public void testWriteIndex(final Path indexFile, final int exprectedBlocks) throws Exception {
        // load the index and write it down
        final GZIIndex index = GZIIndex.loadIndex(indexFile);
        final Path temp =
                Files.createTempFile("testWriteIndex", indexFile.getFileName().toString());
        IOUtil.deleteOnExit(temp);
        index.writeIndex(temp);

        // test equal byte representation on disk
        final byte[] expected = Files.readAllBytes(indexFile);
        final byte[] actual = Files.readAllBytes(temp);
        Assert.assertEquals(expected, actual);
    }

    @DataProvider
    public Object[][] filesWithIndex() {
        return new Object[][] {
            {
                Paths.get("src/test/resources/htsjdk/samtools/block_compressed.sam.gz"),
                Paths.get("src/test/resources/htsjdk/samtools/block_compressed.sam.gz.gzi")
            },
            {
                Paths.get("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz"),
                Paths.get("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz.gzi")
            }
        };
    }

    @Test(dataProvider = "filesWithIndex")
    public void testBuildIndex(final Path fileToIndex, final Path expectedIndex) throws Exception {
        // create the index for the provided file
        final GZIIndex actual = GZIIndex.buildIndex(fileToIndex);
        // load the expected index to check for equality
        final GZIIndex expected = GZIIndex.loadIndex(expectedIndex);
        Assert.assertEquals(actual, expected);
    }

    @DataProvider
    public Iterator<Object[]> virtualOffsetForSeekData() throws Exception {
        // wer use the index from the FASTA file for testing seek
        final GZIIndex index = GZIIndex.loadIndex(
                Paths.get("src/test/resources/htsjdk/samtools/reference/Homo_sapiens_assembly18.trimmed.fasta.gz.gzi"));
        final List<Object[]> data = new ArrayList<>(2 * index.getNumberOfBlocks() + 3);
        // position 0
        data.add(new Object[] {0, 0, 0, index});
        // postion 10 bytes before the first index
        final GZIIndex.IndexEntry firstFileEntry = index.getIndexEntries().get(0);
        data.add(new Object[] {
            firstFileEntry.getUncompressedOffset() - 10, 0, firstFileEntry.getUncompressedOffset() - 10, index
        });

        // add for each entry 2 tests (the entry itself and 10 bytes after the block)
        for (final GZIIndex.IndexEntry entry : index.getIndexEntries()) {
            // add to the test data the offset for the beginning of each block
            data.add(new Object[] {entry.getUncompressedOffset(), entry.getCompressedOffset(), 0, index});
            // and also the offset for 10 bytes after the block
            data.add(new Object[] {entry.getUncompressedOffset() + 10, entry.getCompressedOffset(), 10, index});
        }

        return data.iterator();
    }

    @Test(dataProvider = "virtualOffsetForSeekData")
    public void testGetVirtualOffsetForSeek(
            final long uncompressedOffset,
            final long expectedBlockAddress,
            final long expectedBlockOffset,
            final GZIIndex index)
            throws Exception {
        final long virtualOffset = index.getVirtualOffsetForSeek(uncompressedOffset);
        Assert.assertEquals(BlockCompressedFilePointerUtil.getBlockAddress(virtualOffset), expectedBlockAddress);
        Assert.assertEquals(BlockCompressedFilePointerUtil.getBlockOffset(virtualOffset), expectedBlockOffset);
    }

    @Test
    public void testIndexWrittenWhileCompressingMatchesOneBuiltFromTheFile() throws IOException {
        final Path dir = Files.createTempDirectory("gziIndexer");
        IOUtil.deleteOnExit(dir);
        final Path bgzf = dir.resolve("data.gz");
        // Random bytes do not compress, so they fill several blocks.
        final byte[] data = new byte[300_000];
        new Random(7).nextBytes(data);
        final ByteArrayOutputStream gzi = new ByteArrayOutputStream();
        try (BlockCompressedOutputStream out = new BlockCompressedOutputStream(Files.newOutputStream(bgzf), bgzf)) {
            out.addIndexer(gzi);
            out.write(data);
        }
        final GZIIndex written = GZIIndex.loadIndex("written", new ByteArrayInputStream(gzi.toByteArray()));
        final List<GZIIndex.IndexEntry> entries = written.getIndexEntries();
        Assert.assertTrue(entries.size() > 1);
        // As in bgzip's index, there is no entry for the empty block that ends the file.
        Assert.assertTrue(entries.get(entries.size() - 1).getUncompressedOffset() < data.length);
        try (BlockCompressedInputStream in = new BlockCompressedInputStream(new SeekableFileStream(bgzf))) {
            for (final GZIIndex.IndexEntry entry : entries) {
                in.seek(BlockCompressedFilePointerUtil.makeFilePointer(entry.getCompressedOffset(), 0));
                final byte[] bytes = new byte[16];
                Assert.assertEquals(in.read(bytes), bytes.length);
                final int offset = (int) entry.getUncompressedOffset();
                Assert.assertEquals(bytes, Arrays.copyOfRange(data, offset, offset + bytes.length));
            }
        }
    }

    @Test
    public void testIndexerKeepsUncompressedOffsetsPastTwoGibibytes() throws IOException {
        final long blockSize = 1L << 30;
        final ByteArrayOutputStream gzi = new ByteArrayOutputStream();
        try (GZIIndex.GZIIndexer indexer = new GZIIndex.GZIIndexer(gzi)) {
            indexer.addGzipBlock(0, blockSize);
            indexer.addGzipBlock(100, blockSize);
            indexer.addGzipBlock(200, blockSize);
        }
        final GZIIndex index = GZIIndex.loadIndex("indexer", new ByteArrayInputStream(gzi.toByteArray()));
        Assert.assertEquals(index.getIndexEntries().get(1).getUncompressedOffset(), 2 * blockSize);
    }
}
