package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.seekablestream.SeekableBufferedStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.zip.GZIPOutputStream;

/**
 * Created by vadim on 25/08/2015.
 */
public class CRAIIndexTest extends HtsjdkTest {

    @Test
    public void testFind() {
        final List<CRAIEntry> index = new ArrayList<>();

        final int sequenceId = 1;
        CRAIEntry e = CRAIEntryTest.newEntry(sequenceId, 1, 1, 1, 1, 0);
        index.add(e);

        e = CRAIEntryTest.updateStartContOffset(e, 2, 2);
        index.add(e);

        e = CRAIEntryTest.updateStartContOffset(e, 3, 3);
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
            Assert.assertEquals(found.getSequenceId(), sequenceId);
            boolean intersects = false;
            for (int pos = Math.min(found.getAlignmentStart(), start); pos <= Math.max(found.getAlignmentStart() + found.getAlignmentSpan(), start + span); pos++) {
                if (pos >= found.getAlignmentStart() && pos >= start &&
                        pos <= found.getAlignmentStart() + found.getAlignmentSpan() && pos <= start + span) {
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

    private void doCRAITest(BiFunction<SAMSequenceDictionary, List<CRAIEntry>, SeekableStream> getBaiStreamForIndex) {
        final ArrayList<CRAIEntry> index = new ArrayList<>();
        final CRAIEntry entry = CRAIEntryTest.newEntry(0, 1, 2, 5, 3, 4);
        index.add(entry);

        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));

        final SeekableStream baiStream = getBaiStreamForIndex.apply(dictionary, index);

        final DiskBasedBAMFileIndex bamIndex = new DiskBasedBAMFileIndex(baiStream, dictionary);
        final BAMFileSpan span = bamIndex.getSpanOverlapping(entry.getSequenceId(), entry.getAlignmentStart(), entry.getAlignmentStart());
        Assert.assertNotNull(span);
        final long[] coordinateArray = span.toCoordinateArray();
        Assert.assertEquals(coordinateArray.length, 2);
        Assert.assertEquals(coordinateArray[0] >> 16, entry.getContainerStartByteOffset());
        Assert.assertEquals(coordinateArray[1] & 0xFFFF, 1);
    }

    public SeekableStream getBaiStreamFromMemory(SAMSequenceDictionary dictionary, final List<CRAIEntry> index) {
        byte[] written;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SAMFileHeader samHeader = new SAMFileHeader();
            samHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);
            CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(baos, samHeader);
            for (CRAIEntry entry : index) {
                indexer.addEntry(entry);
            }
            indexer.finish();
            written = baos.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (final SeekableStream baiStream = CRAIIndex.openCraiFileAsBaiStream(new ByteArrayInputStream(written), dictionary)) {
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
    public void testGetLeftmost()  {
        final List<CRAIEntry> index = new ArrayList<>();
        Assert.assertNull(CRAIIndex.getLeftmost(index));

        final CRAIEntry e1 = CRAIEntryTest.newEntry(1, 2, 3, 4, 5, 6);
        index.add(e1);
        // trivial case of single entry in index:
        Assert.assertEquals(e1, CRAIIndex.getLeftmost(index));

        final CRAIEntry e2 = CRAIEntryTest.updateStart(e1, e1.getAlignmentStart() + 1);
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
                final CRAIEntry e = CRAIEntryTest.newEntrySeqStart(i <= lastAligned ? 0 : -1, i);
                index.add(e);
            }
            // check expectations are correct before calling findLastAlignedEntry method:
            Assert.assertTrue(index.get(lastAligned).getSequenceId() != -1);
            if (lastAligned < index.size() - 1) {
                Assert.assertTrue(index.get(lastAligned + 1).getSequenceId() == -1);
            }
            // assert the the found value matches the expectation:
            Assert.assertEquals(CRAIIndex.findLastAlignedEntry(index), lastAligned);
        }
    }

}
