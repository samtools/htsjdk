/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
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

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Iterables;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BAMSBIIndexerTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");
    private static final File CRAM_TEST_DATA_DIR = new File(TEST_DATA_DIR, "cram");
    private static final File BAM_FILE = new File(TEST_DATA_DIR, "example.bam");
    private static final File EMPTY_BAM_FILE = new File(TEST_DATA_DIR, "empty.bam");
    private static final File LARGE_BAM_FILE = new File(CRAM_TEST_DATA_DIR, "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam");

    @Test
    public void testEmptyBam() throws Exception {
        final long bamFileSize = EMPTY_BAM_FILE.length();
        final SBIIndex index1 = fromBAMFile(EMPTY_BAM_FILE, SBIIndexWriter.DEFAULT_GRANULARITY);
        final SBIIndex index2 = fromSAMRecords(EMPTY_BAM_FILE, SBIIndexWriter.DEFAULT_GRANULARITY);
        Assert.assertEquals(index1, index2);
        Assert.assertEquals(index1.dataFileLength(), bamFileSize);
        Assert.assertEquals(index2.dataFileLength(), bamFileSize);
        // the splitting index for a BAM with no records has a single entry that is just the length of the BAM file
        Assert.assertEquals(index1.getVirtualOffsets(), new long[] { BlockCompressedFilePointerUtil.makeFilePointer(bamFileSize) });
        Assert.assertEquals(index1.getVirtualOffsets(), new long[] { BlockCompressedFilePointerUtil.makeFilePointer(bamFileSize) });
    }

    @Test
    public void testReadFromIndexPositions() throws Exception {
        final SBIIndex index = fromBAMFile(BAM_FILE, 2);
        final long[] virtualOffsets = index.getVirtualOffsets();
        final Long firstVirtualOffset = virtualOffsets[0];
        final Long expectedFirstAlignment = SAMUtils.findVirtualOffsetOfFirstRecordInBam(new SeekableFileStream(BAM_FILE));
        Assert.assertEquals(firstVirtualOffset, expectedFirstAlignment);
        Assert.assertNotNull(getReadAtOffset(BAM_FILE, firstVirtualOffset));

        for (int i = 0; i < virtualOffsets.length - 1; i++) { // for all but the last offset
            Assert.assertNotNull(getReadAtOffset(BAM_FILE, virtualOffsets[i]));
        }
    }

    @Test
    public void testSplit() throws Exception {
        final long bamFileSize = LARGE_BAM_FILE.length();
        final SBIIndex index = fromBAMFile(LARGE_BAM_FILE, 100);
        final List<Chunk> chunks = index.split(bamFileSize / 10);
        Assert.assertTrue(chunks.size() > 1);

        final List<SAMRecord> allReads = getReads(LARGE_BAM_FILE);

        final List<SAMRecord> allReadsFromChunks = new ArrayList<>();
        for (final Chunk chunk : chunks) {
            allReadsFromChunks.addAll(getReadsInChunk(LARGE_BAM_FILE, chunk));
        }
        Assert.assertEquals(allReadsFromChunks, allReads);

        final List<Chunk> optimizedChunks = Chunk.optimizeChunkList(chunks, 0);
        Assert.assertEquals(optimizedChunks.size(), 1);
        final List<SAMRecord> allReadsFromOneChunk = getReadsInChunk(LARGE_BAM_FILE, optimizedChunks.get(0));
        Assert.assertEquals(allReadsFromOneChunk, allReads);
    }

    @Test
    public void testIndexersProduceSameIndexes() throws Exception {
        final long bamFileSize = BAM_FILE.length();
        for (final long g : new long[] { 1, 2, 10, SBIIndexWriter.DEFAULT_GRANULARITY }) {
            final SBIIndex index1 = fromBAMFile(BAM_FILE, g);
            final SBIIndex index2 = fromSAMRecords(BAM_FILE, g);
            Assert.assertEquals(index1, index2);
            Assert.assertEquals(index1.dataFileLength(), bamFileSize);
            Assert.assertEquals(index2.dataFileLength(), bamFileSize);
        }
    }

    private SBIIndex fromBAMFile(final File bamFile, final long granularity) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        BAMSBIIndexer.createIndex(new SeekableFileStream(bamFile), out, granularity);
        return SBIIndex.load(new ByteArrayInputStream(out.toByteArray()));
    }

    private SBIIndex fromSAMRecords(final File bamFile, final long granularity) throws IOException {
        final BAMFileReader bamFileReader = bamFileReader(bamFile);
        try (CloseableIterator<SAMRecord> iterator = bamFileReader.getIterator();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final SBIIndexWriter indexWriter = new SBIIndexWriter(out, granularity);
            while (iterator.hasNext()) {
                processAlignment(indexWriter, iterator.next());
            }
            indexWriter.finish(bamFileReader.getVirtualFilePointer(), bamFile.length());
            return SBIIndex.load(new ByteArrayInputStream(out.toByteArray()));
        }
    }

    private void processAlignment(final SBIIndexWriter indexWriter, final SAMRecord rec) {
        final SAMFileSource source = rec.getFileSource();
        if (source == null) {
            throw new SAMException("No source (virtual file offsets); needed for indexing on BAM Record " + rec);
        }
        final BAMFileSpan filePointer = (BAMFileSpan) source.getFilePointer();
        indexWriter.processRecord(filePointer.getFirstOffset());
    }

    private BAMFileReader bamFileReader(final File bamFile) {
        final SamReader samReader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS).open(bamFile);
        return (BAMFileReader) ((SamReader.PrimitiveSamReaderToSamReaderAdapter) samReader).underlyingReader();
    }

    private SAMRecord getReadAtOffset(final File bamFile, final long virtualOffset) {
        final Chunk chunk = new Chunk(virtualOffset, BlockCompressedFilePointerUtil.makeFilePointer(bamFile.length()));
        try (CloseableIterator<SAMRecord> iterator = bamFileReader(bamFile).getIterator(new BAMFileSpan(chunk))) {
            Assert.assertTrue(iterator.hasNext());
            return iterator.next();
        }
    }

    private List<SAMRecord> getReads(final File bamFile) throws IOException {
        try (SamReader samReader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS).open(bamFile)) {
            return Iterables.slurp(samReader);
        }
    }

    private List<SAMRecord> getReadsInChunk(final File bamFile, final Chunk chunk) {
        try (CloseableIterator<SAMRecord> iterator = bamFileReader(bamFile).getIterator(new BAMFileSpan(chunk))) {
            return Iterables.slurp(iterator);
        }
    }
}
