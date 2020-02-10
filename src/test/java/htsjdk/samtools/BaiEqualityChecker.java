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

import org.testng.Assert;

import java.io.IOException;
import java.nio.file.Path;

public class BaiEqualityChecker {

    private Path bamFile;
    private Path baiFile1;
    private Path baiFile2;

    public static void assertEquals(Path bamFile, Path baiFile1, Path baiFile2) throws IOException {
        new BaiEqualityChecker(bamFile, baiFile1, baiFile2).assertEquals();
    }

    public BaiEqualityChecker(Path bamFile, Path baiFile1, Path baiFile2) {
        this.bamFile = bamFile;
        this.baiFile1 = baiFile1;
        this.baiFile2 = baiFile2;
    }

    private void assertEquals() throws IOException {
        SamReaderFactory readerFactory =
                SamReaderFactory.makeDefault()
                        .setOption(SamReaderFactory.Option.EAGERLY_DECODE, false)
                        .setUseAsyncIo(false);
        SAMFileHeader header = readerFactory.getFileHeader(bamFile);
        SAMSequenceDictionary dict = header.getSequenceDictionary();
        AbstractBAMFileIndex bai1 = new CachingBAMFileIndex(baiFile1.toFile(), dict);
        AbstractBAMFileIndex bai2 = new CachingBAMFileIndex(baiFile2.toFile(), dict);

        Assert.assertEquals(
                bai1.getNumberOfReferences(), bai2.getNumberOfReferences(), "Number of references");
        Assert.assertEquals(
                bai1.getNoCoordinateCount(), bai2.getNoCoordinateCount(), "No coordinate index count");
        int numReferences = bai1.getNumberOfReferences();
        for (int i = 0; i < numReferences; i++) {
            BAMIndexContent bamIndexContent1 = bai1.getQueryResults(i);
            BAMIndexContent bamIndexContent2 = bai2.getQueryResults(i);
            assertEquals(bamIndexContent1, bamIndexContent2);
        }
    }

    private void assertEquals(BAMIndexContent bamIndexContent1, BAMIndexContent bamIndexContent2)
            throws IOException {
        assertEquals(bamIndexContent1.getMetaData(), bamIndexContent2.getMetaData());
        assertEquals(bamIndexContent1.getBins(), bamIndexContent2.getBins());
        assertEquals(bamIndexContent1.getLinearIndex(), bamIndexContent2.getLinearIndex());
    }

    private void assertEquals(BAMIndexMetaData metaData1, BAMIndexMetaData metaData2) {
        Assert.assertEquals(metaData1.getFirstOffset(), metaData2.getFirstOffset(), "First offset");
        Assert.assertEquals(metaData1.getLastOffset(), metaData2.getLastOffset(), "Last offset");
        Assert.assertEquals(
                metaData1.getAlignedRecordCount(), metaData2.getAlignedRecordCount(), "AlignedRecordCount");
        Assert.assertEquals(
                metaData1.getUnalignedRecordCount(),
                metaData2.getUnalignedRecordCount(),
                "UnalignedRecordCount");
    }

    private void assertEquals(BinningIndexContent.BinList bins1, BinningIndexContent.BinList bins2)
            throws IOException {
        Assert.assertEquals(bins1.maxBinNumber, bins2.maxBinNumber, "Max bin number");
        Assert.assertEquals(
                bins1.getNumberOfNonNullBins(), bins2.getNumberOfNonNullBins(), "Number of non-null bins");
        for (int i = 0; i <= bins1.maxBinNumber; i++) {
            assertEquals(bins1.getBin(i), bins2.getBin(i));
        }
    }

    private void assertEquals(Bin bin1, Bin bin2) throws IOException {
        if (bin1 == null || bin2 == null) {
            Assert.assertEquals(bin1, bin2);
            return;
        }
        Assert.assertEquals(bin1.getBinNumber(), bin2.getBinNumber(), "Bin number");
        Assert.assertEquals(bin1.getChunkList().size(), bin2.getChunkList().size(), "Chunk list size");
        for (int i = 0; i < bin1.getChunkList().size(); i++) {
            assertEquals(bin1.getChunkList().get(i), bin2.getChunkList().get(i));
        }
    }

    private void assertEquals(LinearIndex linearIndex1, LinearIndex linearIndex2) {
        Assert.assertEquals(
                linearIndex1.getReferenceSequence(),
                linearIndex2.getReferenceSequence(),
                "Linear index ref");
        Assert.assertEquals(linearIndex1.size(), linearIndex2.size(), "Linear index size");
        Assert.assertEquals(
                linearIndex1.getIndexStart(), linearIndex2.getIndexStart(), "Linear index start");
        Assert.assertEquals(
                linearIndex1.getIndexEntries(), linearIndex2.getIndexEntries(), "Linear index entries");
    }

    private void assertEquals(Chunk chunk1, Chunk chunk2) {
        Assert.assertEquals(chunk1.getChunkStart(), chunk2.getChunkStart(), "Chunk start");
        Assert.assertEquals(chunk1.getChunkEnd(), chunk2.getChunkEnd(), "Chunk end");
    }
}
