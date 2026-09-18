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
package htsjdk.tribble.index.tabix;

import htsjdk.index.ReferenceBins;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.index.IndexFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.testng.Assert;

public class TbiEqualityChecker {

    private Path vcfFile;
    private Path tbiFile1;
    private Path tbiFile2;

    private BlockCompressedInputStream blockStream;

    /**
     * Assert that two tabix files for a given VCF file are equal.
     * @param vcfFile path to the VCF file
     * @param tbiFile1 path to the first tabix index
     * @param tbiFile2 path to the second tabix index
     * @param identical if true, virtual file pointers must be identical, if false, then they can be equivalent
     *                  since two virtual file pointers can point to the same physical location in a file
     * @throws IOException
     */
    public static void assertEquals(Path vcfFile, Path tbiFile1, Path tbiFile2, boolean identical) throws IOException {
        new TbiEqualityChecker(vcfFile, tbiFile1, tbiFile2).assertEquals(identical);
    }

    public TbiEqualityChecker(Path vcfFile, Path tbiFile1, Path tbiFile2) throws IOException {
        this.vcfFile = vcfFile;
        this.tbiFile1 = tbiFile1;
        this.tbiFile2 = tbiFile2;

        this.blockStream = new BlockCompressedInputStream(vcfFile);
    }

    private void assertEquals(boolean identical) throws IOException {
        TabixIndex tbi1 = (TabixIndex) IndexFactory.loadIndex(tbiFile1.toString());
        TabixIndex tbi2 = (TabixIndex) IndexFactory.loadIndex(tbiFile2.toString());

        Assert.assertEquals(tbi1.getSequenceNames(), tbi2.getSequenceNames(), "Sequences");

        int numReferences = tbi1.getSequenceNames().size();
        for (int i = 0; i < numReferences; i++) {
            assertEquals(
                    tbi1.getBinningIndex().getReference(i),
                    tbi2.getBinningIndex().getReference(i),
                    identical);
        }
    }

    private void assertEquals(ReferenceBins reference1, ReferenceBins reference2, boolean identical) {
        Assert.assertEquals(reference1.getBinCount(), reference2.getBinCount(), "Number of bins");
        for (int i = 0; i < reference1.getBinCount(); i++) {
            Assert.assertEquals(reference1.getBinNumber(i), reference2.getBinNumber(i), "Bin number");
            final List<Chunk> chunks1 = reference1.getChunks(i);
            final List<Chunk> chunks2 = reference2.getChunks(i);
            Assert.assertEquals(chunks1.size(), chunks2.size(), "Chunk list size");
            for (int j = 0; j < chunks1.size(); j++) {
                assertEquals(chunks1.get(j), chunks2.get(j), identical);
            }
        }
        Assert.assertEquals(reference1.getLinearIndex(), reference2.getLinearIndex(), "Linear index entries");
    }

    private void assertEquals(Chunk chunk1, Chunk chunk2, boolean identical) {
        if (identical) {
            Assert.assertEquals(chunk1.getChunkStart(), chunk2.getChunkStart(), "Chunk start");
            Assert.assertEquals(chunk1.getChunkEnd(), chunk2.getChunkEnd(), "Chunk end");
        } else {
            assertEquivalent("Chunk start", chunk1.getChunkStart(), chunk2.getChunkStart());
            assertEquivalent("Chunk end", chunk1.getChunkEnd(), chunk2.getChunkEnd());
        }
    }

    private void assertEquivalent(String message, long virtualFilePointer1, long virtualFilePointer2) {
        // seek to the given positions check they are actually equivalent
        long norm1 = normalizeVirtualFilePointer(virtualFilePointer1);
        long norm2 = normalizeVirtualFilePointer(virtualFilePointer2);
        Assert.assertEquals(norm1, norm2, message);
    }

    private long normalizeVirtualFilePointer(long virtualFilePointer) {
        try {
            blockStream.seek(virtualFilePointer);
            return blockStream.getFilePointer();
        } catch (IOException e) {
            Assert.fail("Failed to seek to " + BlockCompressedFilePointerUtil.asString(virtualFilePointer));
            return -1; // never reached
        }
    }
}
