package htsjdk.samtools.cram;

import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.CRAMCRAIIndexer;
import htsjdk.samtools.DiskBasedBAMFileIndex;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.seekablestream.SeekableBufferedStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.zip.GZIPOutputStream;

/**
 * Created by vadim on 25/08/2015.
 */
public class CRAIIndexTest {

    @Test
    public void testFind() throws IOException, CloneNotSupportedException {
        final List<CRAIEntry> index = new ArrayList<CRAIEntry>();

        final int sequenceId = 1;
        CRAIEntry e = new CRAIEntry();
        e.sequenceId = sequenceId;
        e.alignmentStart = 1;
        e.alignmentSpan = 1;
        e.containerStartOffset = 1;
        e.sliceOffset = 1;
        e.sliceSize = 0;
        index.add(e);

        e = e.clone();
        e.alignmentStart = 2;
        e.containerStartOffset = 2;
        index.add(e);

        e = e.clone();
        e.alignmentStart = 3;
        e.containerStartOffset = 3;
        index.add(e);

        Assert.assertFalse(allFoundEntriesIntersectQueryInFind(index, sequenceId, 1, 0));

        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, sequenceId, 1, 1));
        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, sequenceId, 1, 2));
        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, sequenceId, 2, 1));
        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, sequenceId, 1, 3));

        final int nonExistentSequenceId = 2;
        Assert.assertFalse(allFoundEntriesIntersectQueryInFind(index, nonExistentSequenceId, 2, 1));
        // a query starting beyond all entries:
        Assert.assertFalse(allFoundEntriesIntersectQueryInFind(index, sequenceId, 4, 1));
    }

    private boolean allFoundEntriesIntersectQueryInFind(final List<CRAIEntry> index, final int sequenceId, final int start, final int span) {
        int foundCount = 0;
        for (final CRAIEntry found : CRAIIndex.find(index, sequenceId, start, span)) {
            foundCount++;
            Assert.assertEquals(found.sequenceId, sequenceId);
            boolean intersects = false;
            for (int pos = Math.min(found.alignmentStart, start); pos <= Math.max(found.alignmentStart + found.alignmentSpan, start + span); pos++) {
                if (pos >= found.alignmentStart && pos >= start &&
                        pos <= found.alignmentStart + found.alignmentSpan && pos <= start + span) {
                    intersects = true;
                    break;
                }
            }
            if (!intersects) {
                return false;
            }
        }
        return foundCount > 0;
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testCraiRequiresDictionary() throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final GZIPOutputStream gos = new GZIPOutputStream(baos);
             final BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            CRAIIndex.openCraiFileAsBaiStream(bis, null);
        }
    }

    @Test
    public void testCraiInMemory() throws IOException {
        doCRAITest(this::getBaiStreamFromMemory);
    }

    @Test
    public void testCraiFromFile() throws IOException {
        doCRAITest(this::getBaiStreamFromFile);
    }

    private void doCRAITest(BiFunction<SAMSequenceDictionary, List<CRAIEntry>, SeekableStream> getBaiStreamForIndex) throws IOException {
        final ArrayList<CRAIEntry> index = new ArrayList<CRAIEntry>();
        final CRAIEntry entry = new CRAIEntry();
        entry.sequenceId = 0;
        entry.alignmentStart = 1;
        entry.alignmentSpan = 2;
        entry.sliceOffset = 3;
        entry.sliceSize = 4;
        entry.containerStartOffset = 5;
        index.add(entry);

        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));

        final SeekableStream baiStream = getBaiStreamForIndex.apply(dictionary, index);

        final DiskBasedBAMFileIndex bamIndex = new DiskBasedBAMFileIndex(baiStream, dictionary);
        final BAMFileSpan span = bamIndex.getSpanOverlapping(entry.sequenceId, entry.alignmentStart, entry.alignmentStart);
        Assert.assertNotNull(span);
        final long[] coordinateArray = span.toCoordinateArray();
        Assert.assertEquals(coordinateArray.length, 2);
        Assert.assertEquals(coordinateArray[0] >> 16, entry.containerStartOffset);
        Assert.assertEquals(coordinateArray[1] & 0xFFFF, 1);
    }

    public SeekableStream getBaiStreamFromMemory(SAMSequenceDictionary dictionary, final List<CRAIEntry> index) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            SAMFileHeader samHeader = new SAMFileHeader();
            samHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);
            CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(baos, samHeader);
            for (CRAIEntry entry: index) {
                indexer.addEntry(entry);
            }
            indexer.finish();
            final SeekableStream baiStream = CRAIIndex.openCraiFileAsBaiStream(new ByteArrayInputStream(baos.toByteArray()), dictionary);
            Assert.assertNotNull(baiStream);
            return baiStream;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SeekableStream getBaiStreamFromFile(SAMSequenceDictionary dictionary, final List<CRAIEntry> index) {
        try {
            final File file = File.createTempFile("test", ".crai");
            file.deleteOnExit();
            final FileOutputStream fos = new FileOutputStream(file);
            SAMFileHeader samHeader = new SAMFileHeader();
            samHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);
            CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(fos, samHeader);
            for (CRAIEntry entry: index) {
                indexer.addEntry(entry);
            }
            indexer.finish();
            final SeekableStream baiStream = CRAIIndex.openCraiFileAsBaiStream(new SeekableBufferedStream(new SeekableFileStream(file)), dictionary);
            Assert.assertNotNull(baiStream);
            return baiStream;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetLeftmost() throws CloneNotSupportedException {
        final List<CRAIEntry> index = new ArrayList<CRAIEntry>();
        Assert.assertNull(CRAIIndex.getLeftmost(index));

        final CRAIEntry e1 = new CRAIEntry();
        e1.sequenceId = 1;
        e1.alignmentStart = 2;
        e1.alignmentSpan = 3;
        e1.containerStartOffset = 4;
        e1.sliceOffset = 5;
        e1.sliceSize = 6;
        index.add(e1);
        // trivial case of single entry in index:
        Assert.assertEquals(e1, CRAIIndex.getLeftmost(index));

        final CRAIEntry e2 = e1.clone();
        e2.alignmentStart = e1.alignmentStart + 1;
        index.add(e2);
        Assert.assertEquals(e1, CRAIIndex.getLeftmost(index));
    }

    @Test
    public void testFindLastAlignedEntry() {
        final List<CRAIEntry> index = new ArrayList<CRAIEntry>();
        Assert.assertEquals(-1, CRAIIndex.findLastAlignedEntry(index));

        // Scan all allowed combinations of 10 mapped/unmapped entries and assert the found last aligned entry:
        final int indexSize = 10;
        for (int lastAligned = 0; lastAligned < indexSize; lastAligned++) {
            index.clear();
            for (int i = 0; i < indexSize; i++) {
                final CRAIEntry e = new CRAIEntry();

                e.sequenceId = (i <= lastAligned ? 0 : -1);
                e.alignmentStart = i;
                index.add(e);
            }
            // check expectations are correct before calling findLastAlignedEntry method:
            Assert.assertTrue(index.get(lastAligned).sequenceId != -1);
            if (lastAligned < index.size() - 1) {
                Assert.assertTrue(index.get(lastAligned + 1).sequenceId == -1);
            }
            // assert the the found value matches the expectation:
            Assert.assertEquals(CRAIIndex.findLastAlignedEntry(index), lastAligned);
        }
    }

}
