package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Created by vadim on 25/08/2015.
 */
public class CRAIIndexTest extends HtsjdkTest {

    @Test
    public void testFind() {
        final ReferenceContext refContextToFind = new ReferenceContext(1);
        final List<CRAIEntry> index = getCraiEntriesForTest(refContextToFind);

        Assert.assertFalse(allFoundEntriesIntersectQueryInFind(index, refContextToFind, 1, 0));

        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, refContextToFind, 1, 1));
        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, refContextToFind, 1, 2));
        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, refContextToFind, 2, 1));
        Assert.assertTrue(allFoundEntriesIntersectQueryInFind(index, refContextToFind, 1, 3));

        final ReferenceContext missingRefContext = new ReferenceContext(2);
        Assert.assertFalse(allFoundEntriesIntersectQueryInFind(index, missingRefContext, 2, 1));
        // a query starting beyond all entries:
        Assert.assertFalse(allFoundEntriesIntersectQueryInFind(index, refContextToFind, 4, 1));
    }

    // find treats start < 1 and span < 1 as "match all entries with this sequence ID"

    @Test
    public void testFindZeroStart() {
        final ReferenceContext refContextToFind = new ReferenceContext(1);
        final List<CRAIEntry> index = getCraiEntriesForTest(refContextToFind);

        Assert.assertTrue(CRAIIndex.find(index, refContextToFind, 0, 0).size() > 0);
        Assert.assertTrue(CRAIIndex.find(index, refContextToFind, 0, 1).size() > 0);

        final ReferenceContext missingRefContext = new ReferenceContext(2);
        Assert.assertTrue(CRAIIndex.find(index, missingRefContext, 0, 1).isEmpty());
    }

    @Test
    public void testFindZeroSpan() {
        final ReferenceContext refContextToFind = new ReferenceContext(1);
        final List<CRAIEntry> index = getCraiEntriesForTest(refContextToFind);

        Assert.assertTrue(CRAIIndex.find(index, refContextToFind, 0, 0).size() > 0);
        Assert.assertTrue(CRAIIndex.find(index, refContextToFind, 1, 0).size() > 0);

        final ReferenceContext missingRefContext = new ReferenceContext(2);
        Assert.assertTrue(CRAIIndex.find(index, missingRefContext, 1, 0).isEmpty());
    }

    private List<CRAIEntry> getCraiEntriesForTest(final ReferenceContext refContext) {
        final List<CRAIEntry> index = new ArrayList<>();
        index.add(new CRAIEntry(refContext, 1, 1, 1, 1, 0));
        index.add(new CRAIEntry(refContext, 2, 1, 2, 1, 0));
        index.add(new CRAIEntry(refContext, 3, 1, 3, 1, 0));
        return index;
    }

    private boolean allFoundEntriesIntersectQueryInFind(final List<CRAIEntry> index, final ReferenceContext refContext, final int start, final int span) {
        final List<CRAIEntry> found = CRAIIndex.find(index, refContext, start, span);
        for (final CRAIEntry entry : found) {
            Assert.assertEquals(entry.getReferenceContext(), refContext);
            final int dummy = -1;
            if (! CRAIEntry.intersect(entry, new CRAIEntry(refContext, start, span, dummy, dummy, dummy))) {
                return false;
            }
        }

        // don't pass if we had no matches
        return ! found.isEmpty();
    }

    @Test
    public void testCraiInMemory() {
        doCRAITest(this::getBaiStreamFromMemory);
    }

    @Test
    public void testCraiFromFile() {
        doCRAITest(this::getBaiStreamFromFile);
    }

    @Test
    public void testCraiFromFileAsSeekableStream() {
        doCRAITest(this::getBaiStreamFromFileAsSeekableStream);
    }

    private void doCRAITest(final BiFunction<SAMSequenceDictionary, List<CRAIEntry>, SeekableStream> getBaiStreamForIndex) {
        final ArrayList<CRAIEntry> index = new ArrayList<>();
        final CRAIEntry entry = new CRAIEntry(new ReferenceContext(0), 1, 2, 5, 3, 4);
        index.add(entry);

        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));

        final SeekableStream baiStream = getBaiStreamForIndex.apply(dictionary, index);

        final DiskBasedBAMFileIndex bamIndex = new DiskBasedBAMFileIndex(baiStream, dictionary);
        final BAMFileSpan span = bamIndex.getSpanOverlapping(
                entry.getReferenceContext().getSequenceId(),
                entry.getAlignmentStart(),
                entry.getAlignmentStart());
        Assert.assertNotNull(span);
        final long[] coordinateArray = span.toCoordinateArray();
        Assert.assertEquals(coordinateArray.length, 2);
        Assert.assertEquals(coordinateArray[0] >> 16, entry.getContainerStartByteOffset());
        Assert.assertEquals(coordinateArray[1] & 0xFFFF, 1);
    }

    private SeekableStream getBaiStreamFromMemory(final SAMSequenceDictionary dictionary, final List<CRAIEntry> index) {
        byte[] written;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doIndexing(index, baos);
            written = baos.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (final InputStream is = new ByteArrayInputStream(written);
             final SeekableStream baiStream = CRAIIndex.openCraiFileAsBaiStream(is, dictionary)) {
            Assert.assertNotNull(baiStream);
            return baiStream;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SeekableStream getBaiStreamFromFileAsSeekableStream(final SAMSequenceDictionary dictionary, final List<CRAIEntry> index) {
        try {
            final File file = File.createTempFile("test", ".crai");
            file.deleteOnExit();

            try (final FileOutputStream fos = new FileOutputStream(file)) {
                doIndexing(index, fos);
            }

            try (final InputStream is = new SeekableFileStream(file);
                 final SeekableStream baiStream = CRAIIndex.openCraiFileAsBaiStream(is, dictionary)) {
                Assert.assertNotNull(baiStream);
                return baiStream;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SeekableStream getBaiStreamFromFile(final SAMSequenceDictionary dictionary, final List<CRAIEntry> index) {
        try {
            final File file = File.createTempFile("test", ".crai");
            file.deleteOnExit();

            try (final FileOutputStream fos = new FileOutputStream(file)) {
                doIndexing(index, fos);
            }

            try (final SeekableStream baiStream = CRAIIndex.openCraiFileAsBaiStream(file, dictionary)) {
                Assert.assertNotNull(baiStream);
                return baiStream;
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void doIndexing(final List<CRAIEntry> index, final OutputStream fos) {
        final SAMFileHeader samHeader = new SAMFileHeader();
        samHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        final CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(fos, samHeader, index);
        indexer.finish();
    }

    // AKA get first

    @Test
    public void testGetLeftmost()  {
        final List<CRAIEntry> index = new ArrayList<>();
        Assert.assertNull(CRAIIndex.getLeftmost(index));

        final ReferenceContext refContext1 = new ReferenceContext(1);
        final int start1 = 2;
        final int offset1 = 4;
        final CRAIEntry e1 = new CRAIEntry(refContext1, start1, 3, offset1, 5, 6);
        index.add(e1);
        // trivial case of single entry in index:
        Assert.assertEquals(CRAIIndex.getLeftmost(index), e1);

        final int start2 = start1 + 1;
        final CRAIEntry e2 = new CRAIEntry(refContext1, start2, 3, offset1, 5, 6);
        index.add(e2);
        Assert.assertEquals(CRAIIndex.getLeftmost(index), e1);

        // earlier start, but later sequence
        final int start3 = start1 - 1;
        final ReferenceContext refContext3 = new ReferenceContext(3);
        final CRAIEntry e3 = new CRAIEntry(refContext3, start3, 3, offset1, 5, 6);
        index.add(e3);
        Assert.assertEquals(CRAIIndex.getLeftmost(index), e1);

        // same start, later container start offset
        final int offset4 = offset1 + 1;
        final CRAIEntry e4 = new CRAIEntry(refContext1, start1, 3, offset4, 5, 6);
        index.add(e4);
        Assert.assertEquals(CRAIIndex.getLeftmost(index), e1);

        // same start, earlier container start offset
        final int offset5 = offset1 - 1;
        final CRAIEntry e5 = new CRAIEntry(refContext1, start1, 3, offset5, 5, 6);
        index.add(e5);

        // now e5 is the leftmost/earliest
        Assert.assertEquals(CRAIIndex.getLeftmost(index), e5);
    }

    @Test
    public void testFindLastAlignedEntry() {
        final List<CRAIEntry> index = new ArrayList<>();
        Assert.assertEquals(-1, CRAIIndex.findLastAlignedEntry(index));

        final int unplacedRefId = ReferenceContext.UNMAPPED_UNPLACED_CONTEXT.getSerializableId();

        // Scan all allowed combinations of 10 mapped/unmapped entries and assert the found last aligned entry:
        final int indexSize = 10;
        for (int lastAligned = 0; lastAligned < indexSize; lastAligned++) {
            index.clear();
            for (int i = 0; i < indexSize; i++) {
                final int dummy = 1;
                final ReferenceContext refContext = i <= lastAligned ?
                        new ReferenceContext(0) :
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT;
                final CRAIEntry e = new CRAIEntry(refContext, i, 0, dummy, dummy, dummy);
                index.add(e);
            }
            // check expectations are correct before calling findLastAlignedEntry method:
            Assert.assertNotEquals(index.get(lastAligned).getReferenceContext(), ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
            if (lastAligned < index.size() - 1) {
                Assert.assertEquals(index.get(lastAligned + 1).getReferenceContext(), ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
            }
            // assert the the found value matches the expectation:
            Assert.assertEquals(CRAIIndex.findLastAlignedEntry(index), lastAligned);
        }
    }

}
