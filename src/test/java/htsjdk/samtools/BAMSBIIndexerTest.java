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
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;

public class BAMSBIIndexerTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");
    private static final File BAM_FILE = new File(TEST_DATA_DIR, "example.bam");
    private static final File EMPTY_BAM_FILE = new File(TEST_DATA_DIR, "empty.bam");
    private static final File LARGE_BAM_FILE = new File(TEST_DATA_DIR, "CEUTrio.HiSeq.WGS.b37.ch20.1m-1m1k.NA12878.bam");

    @Test
    public void testEmptyBam() throws Exception {
        long bamFileSize = EMPTY_BAM_FILE.length();
        SBIIndex index1 = fromBAMFile(EMPTY_BAM_FILE, SBIIndexWriter.DEFAULT_GRANULARITY);
        SBIIndex index2 = fromSAMRecords(EMPTY_BAM_FILE, SBIIndexWriter.DEFAULT_GRANULARITY);
        Assert.assertEquals(index1, index2);
        Assert.assertEquals(index1.dataFileLength(), bamFileSize);
        Assert.assertEquals(index2.dataFileLength(), bamFileSize);
        // the splitting index for a BAM with no records has a single entry that is just the length of the BAM file
        Assert.assertEquals(index1.getVirtualOffsets(), Collections.singletonList(BlockCompressedFilePointerUtil.makeFilePointer(bamFileSize)));
        Assert.assertEquals(index1.getVirtualOffsets(), Collections.singletonList(BlockCompressedFilePointerUtil.makeFilePointer(bamFileSize)));
    }

    @Test
    public void testReadFromIndexPositions() throws Exception {
        SBIIndex index = fromBAMFile(BAM_FILE, 2);
        NavigableSet<Long> virtualOffsets = index.getVirtualOffsets();
        Long firstVirtualOffset = virtualOffsets.first();
        Long expectedFirstAlignment = SAMUtils.findVirtualOffsetOfFirstRecordInBam(new SeekableFileStream(BAM_FILE));
        Assert.assertEquals(firstVirtualOffset, expectedFirstAlignment);
        Assert.assertNotNull(getReadAtOffset(BAM_FILE, firstVirtualOffset));

        for (Long virtualOffset : virtualOffsets.headSet(virtualOffsets.last())) { // for all but the last offset
            Assert.assertNotNull(getReadAtOffset(BAM_FILE, virtualOffset));
        }
    }

    @Test
    public void testSplit() throws Exception {
        long bamFileSize = LARGE_BAM_FILE.length();
        SBIIndex index = fromBAMFile(LARGE_BAM_FILE, 100);
        List<Chunk> chunks = index.split(bamFileSize / 10);
        Assert.assertTrue(chunks.size() > 1);

        SamReader samReader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS).open(LARGE_BAM_FILE);
        List<SAMRecord> allReads = Iterables.slurp(samReader);

        List<SAMRecord> allReadsFromChunks = new ArrayList<>();
        for (Chunk chunk : chunks) {
            allReadsFromChunks.addAll(getReadsInChunk(LARGE_BAM_FILE, chunk));
        }
        Assert.assertEquals(allReadsFromChunks, allReads);

        List<Chunk> optimizedChunks = Chunk.optimizeChunkList(chunks, 0);
        Assert.assertEquals(optimizedChunks.size(), 1);
        List<SAMRecord> allReadsFromOneChunk = getReadsInChunk(LARGE_BAM_FILE, optimizedChunks.get(0));
        Assert.assertEquals(allReadsFromOneChunk, allReads);
    }

    @Test
    public void testIndexersProduceSameIndexes() throws Exception {
        long bamFileSize = BAM_FILE.length();
        for (long g : new long[] { 1, 2, 10, SBIIndexWriter.DEFAULT_GRANULARITY }) {
            SBIIndex index1 = fromBAMFile(BAM_FILE, g);
            SBIIndex index2 = fromSAMRecords(BAM_FILE, g);
            Assert.assertEquals(index1, index2);
            Assert.assertEquals(index1.dataFileLength(), bamFileSize);
            Assert.assertEquals(index2.dataFileLength(), bamFileSize);
        }
    }

    private SBIIndex fromBAMFile(File bamFile, long granularity) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BAMSBIIndexer.createIndex(new SeekableFileStream(bamFile), out, granularity);
        return SBIIndex.load(new ByteArrayInputStream(out.toByteArray()));
    }

    private SBIIndex fromSAMRecords(File bamFile, long granularity) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SBIIndexWriter indexWriter = new SBIIndexWriter(out, granularity);
        BAMFileReader bamFileReader = bamFileReader(bamFile);
        CloseableIterator<SAMRecord> iterator = bamFileReader.getIterator();
        while (iterator.hasNext()) {
            processAlignment(indexWriter, iterator.next());
        }
        indexWriter.writeVirtualOffset(bamFileReader.getVirtualFilePointer());
        indexWriter.finish(bamFile.length());
        return SBIIndex.load(new ByteArrayInputStream(out.toByteArray()));
    }

    public void processAlignment(SBIIndexWriter indexWriter, SAMRecord rec) {
        SAMFileSource source = rec.getFileSource();
        if (source == null) {
            throw new SAMException("No source (virtual file offsets); needed for indexing on BAM Record " + rec);
        }
        BAMFileSpan filePointer = (BAMFileSpan) source.getFilePointer();
        indexWriter.processRecord(filePointer.getFirstOffset());
    }

    private BAMFileReader bamFileReader(File bamFile) {
        SamReader samReader = SamReaderFactory.makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS).open(bamFile);
        return (BAMFileReader) ((SamReader.PrimitiveSamReaderToSamReaderAdapter) samReader).underlyingReader();
    }

    private SAMRecord getReadAtOffset(File bamFile, long virtualOffset) {
        Chunk chunk = new Chunk(virtualOffset, BlockCompressedFilePointerUtil.makeFilePointer(bamFile.length()));
        try (CloseableIterator<SAMRecord> iterator = bamFileReader(bamFile).getIterator(new BAMFileSpan(chunk))) {
            Assert.assertTrue(iterator.hasNext());
            return iterator.next();
        }
    }

    private List<SAMRecord> getReadsInChunk(File bamFile, Chunk chunk) {
        try (CloseableIterator<SAMRecord> iterator = bamFileReader(bamFile).getIterator(new BAMFileSpan(chunk))) {
            return Iterables.slurp(iterator);
        }
    }
}
