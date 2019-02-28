package htsjdk.samtools.cram.index;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CramRecordTestHelper;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.*;

/**
 * Created by vadim on 25/08/2015.
 */
public class CRAIEntryTest extends CramRecordTestHelper {

    @Test
    public void singleRefTestGetCRAIEntries() {
        final Slice slice1 = createSingleRefSlice(0);
        final Slice slice2 = createSingleRefSlice(0);

        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2));
        final List<CRAIEntry> entries = container.getCRAIEntries();

        Assert.assertNotNull(entries);
        Assert.assertEquals(entries.size(), 2);

        assertEntryForSlice(entries.get(0), slice1);
        assertEntryForSlice(entries.get(1), slice2);
    }

    @Test
    public void multiRefTestGetCRAIEntries() {
        final Slice slice1 = new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT);
        final Slice slice2 = new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT);

        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2));
        final List<CRAIEntry> entries = container.getCRAIEntries();

        Assert.assertNotNull(entries);
        Assert.assertEquals(entries.size(), 2);

        assertEntryForSlice(entries.get(0), slice1);
        assertEntryForSlice(entries.get(1), slice2);
    }

    // requirement for getCRAIEntriesSplittingMultiRef():
    // a Container built from Slices which were in turn built from records

    @Test
    public void testGetCRAIEntriesSplittingMultiRef() {
        final int[] landmarks = new int[] { 100, 200 };

        // the indices of the above landmarks array
        final int slice1Index = 0;
        final int slice2Index = 1;

        final int slice1AlnStartOffset = 10;
        final int slice2AlnStartOffset = 20;
        final int sliceAlnSpan = CRAMStructureTestUtil.READ_LENGTH_FOR_TEST_RECORDS;
        final int sliceByteSize = 500;

        final int containerOffset = 1000;

        // build two multi-ref slices, spanning 5 sequences with the middle one split
        // this will create 6 CRAI Entries

        int indexStart = 0;
        final List<CramCompressionRecord> records1 = createMultiRefRecords(indexStart, slice1AlnStartOffset, 0, 1, 2);
        indexStart += records1.size();
        final List<CramCompressionRecord> records2 = createMultiRefRecords(indexStart, slice2AlnStartOffset, 2, 3, 4);
        final List<CramCompressionRecord> allRecords = new ArrayList<CramCompressionRecord>() {{
            addAll(records1);
            addAll(records2);
        }};

        final CompressionHeader compressionHeader = new CompressionHeaderFactory().build(allRecords, null, true);
        final Slice slice1 = Slice.buildSlice(records1, compressionHeader);
        final Slice slice2 = Slice.buildSlice(records2, compressionHeader);

        slice1.index = slice1Index;
        slice1.size = sliceByteSize;

        slice2.index = slice2Index;
        slice2.size = sliceByteSize;

        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2));
        container.compressionHeader = compressionHeader;
        container.landmarks = landmarks;
        container.offset = containerOffset;

        final List<CRAIEntry> entries = container.getCRAIEntriesSplittingMultiRef();
        Assert.assertNotNull(entries);
        Assert.assertEquals(entries.size(), 6);

        // slice 1 has index entries for refs 0, 1, 2

        assertEntryForSlice(entries.get(0), 0, slice1AlnStartOffset, sliceAlnSpan, containerOffset, landmarks[0], sliceByteSize);
        assertEntryForSlice(entries.get(1), 1, slice1AlnStartOffset + 1, sliceAlnSpan, containerOffset, landmarks[0], sliceByteSize);
        assertEntryForSlice(entries.get(2), 2, slice1AlnStartOffset + 2, sliceAlnSpan, containerOffset, landmarks[0], sliceByteSize);

        // slice 2 has index entries for refs 2, 3, 4

        assertEntryForSlice(entries.get(3), 2, slice2AlnStartOffset + 3, sliceAlnSpan, containerOffset, landmarks[1], sliceByteSize);
        assertEntryForSlice(entries.get(4), 3, slice2AlnStartOffset + 4, sliceAlnSpan, containerOffset, landmarks[1], sliceByteSize);
        assertEntryForSlice(entries.get(5), 4, slice2AlnStartOffset + 5, sliceAlnSpan, containerOffset, landmarks[1], sliceByteSize);
    }

    @Test
    public void testFromIndexLine() {
        int counter = 1;
        final int sequenceId = counter++;
        final int alignmentStart = counter++;
        final int alignmentSpan = counter++;
        final int containerOffset = Integer.MAX_VALUE + counter++;
        final int sliceOffset = counter++;
        final int sliceSize = counter++;

        final String line = String.format("%d\t%d\t%d\t%d\t%d\t%d", sequenceId, alignmentStart, alignmentSpan, containerOffset, sliceOffset, sliceSize);
        final CRAIEntry entry = CRAIEntry.fromIndexLine(line);
        assertEntryForSlice(entry, sequenceId, alignmentStart, alignmentSpan, containerOffset, sliceOffset, sliceSize);
    }

    @Test
    public void testCompareTo() {
        testComparator(CRAIEntry::compareTo,
                this::basicSequenceIdSubTest,
                this::basicAlignmentStartSubTest,
                this::unsortedSubTest,  // does not sort by Alignment End
                this::basicContainerStartSubTest);
    }

    // same as base comparison type

    @Test
    public void testByStartComparator() {
        testComparator(CRAIEntry.BY_START,
                this::basicSequenceIdSubTest,
                this::basicAlignmentStartSubTest,
                this::unsortedSubTest,  // does not sort by Alignment End
                this::basicContainerStartSubTest);
    }

    // same as above, but sequence ID has unmapped sorted last

    @Test
    public void testUnmappedLastComparator () {
        testComparator(CRAIEntry.UNMAPPED_LAST,
                this::unmappedLastSequenceIdSubTest,
                this::basicAlignmentStartSubTest,
                this::unsortedSubTest,  // does not sort by Alignment End
                this::basicContainerStartSubTest);
    }

    // sorts by alignment end, ignores alignment start

    @Test
    public void testByEndComparator () {
        testComparator(CRAIEntry.BY_END,
                this::basicSequenceIdSubTest,
                this::unsortedSubTest,  // does not sort by Alignment Start
                this::basicAlignmentEndSubTest,
                this::basicContainerStartSubTest);
    }

    private void testComparator(final Comparator<CRAIEntry> comparator,
                                final ComparatorSubTest sequenceIdTest,
                                final ComparatorSubTest alignmentStartTest,
                                final ComparatorSubTest alignmentEndTest,
                                final ComparatorSubTest containerStartTest) {

        final List<CRAIEntry> seqIdList = new ArrayList<>();
        seqIdList.add(newEntry(150, 0, 0));
        seqIdList.add(newEntry(200, 0, 0));
        seqIdList.add(newEntry(100, 0, 0));
        seqIdList.add(newEntry(300, 0, 0));
        seqIdList.add(newEntry(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 0, 0));
        seqIdList.sort(comparator);
        sequenceIdTest.test(seqIdList);

        final List<CRAIEntry> alignmentStartList = new ArrayList<>();
        alignmentStartList.add(newEntry(1, 300, 0));
        alignmentStartList.add(newEntry(1, 200, 0));
        alignmentStartList.add(newEntry(1, 100, 0));
        alignmentStartList.add(newEntry(1, 400, 0));
        alignmentStartList.sort(comparator);
        alignmentStartTest.test(alignmentStartList);

        final List<CRAIEntry> alignmentEndList = new ArrayList<>();
        alignmentEndList.add(newEntry(1, 300, 0));
        alignmentEndList.add(newEntry(1, 200, 50));
        alignmentEndList.add(newEntry(1, 200, 30));
        alignmentEndList.add(newEntry(1, 200, 0));
        alignmentEndList.add(newEntry(1, 100, 0));
        alignmentEndList.add(newEntry(1, 100, 20));
        alignmentEndList.add(newEntry(1, 100, 70));
        alignmentEndList.add(newEntry(1, 400, 0));
        alignmentEndList.sort(comparator);
        alignmentEndTest.test(alignmentEndList);

        final List<CRAIEntry> containerStartList = new ArrayList<>();
        containerStartList.add(newEntryContOffset(200));
        containerStartList.add(newEntryContOffset(100));
        containerStartList.add(newEntryContOffset(50));
        containerStartList.add(newEntryContOffset(300));
        containerStartList.sort(comparator);
        containerStartTest.test(containerStartList);
    }

    private interface ComparatorSubTest {
        void test(final List<CRAIEntry> list);
    }

    // no-op.  comparator does not sort in this way.
    private void unsortedSubTest(final List<CRAIEntry> list) { }

    private void basicSequenceIdSubTest(final List<CRAIEntry> list) {
        // NO_ALIGNMENT -1 is sorted first
        Assert.assertEquals(list.get(0).getSequenceId(), SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);

        for (int index = 1; index < list.size() - 1; index++) {
            Assert.assertTrue(list.get(index).getSequenceId() < list.get(index + 1).getSequenceId());
        }
    }

    private void unmappedLastSequenceIdSubTest(final List<CRAIEntry> list) {
        for (int index = 0; index < list.size() - 2; index++) {
            Assert.assertTrue(list.get(index).getSequenceId() < list.get(index + 1).getSequenceId());
        }

        // NO_ALIGNMENT -1 is sorted last
        Assert.assertEquals(list.get(list.size() - 1).getSequenceId(), SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
    }

    private void basicAlignmentStartSubTest(final List<CRAIEntry> list) {
        for (int index = 0; index < list.size() - 1; index++) {
            Assert.assertTrue(list.get(index).getAlignmentStart() < list.get(index + 1).getAlignmentStart());
        }
    }

    private void basicAlignmentEndSubTest(final List<CRAIEntry> list) {
        for (int index = 0; index < list.size() - 1; index++) {
            Assert.assertTrue(list.get(index).getAlignmentStart() + list.get(index).getAlignmentSpan()
                    < list.get(index + 1).getAlignmentStart() + list.get(index + 1).getAlignmentSpan());
        }
    }

    private void basicContainerStartSubTest(final List<CRAIEntry> list) {
        for (int index = 0; index < list.size() - 1; index++) {
            Assert.assertTrue(list.get(index).getContainerStartByteOffset() < list.get(index + 1).getContainerStartByteOffset());
        }
    }

    private static Slice createSingleRefSlice(final int sequenceId) {
        int counter = sequenceId;

        final Slice single = new Slice(new ReferenceContext(sequenceId));
        single.alignmentStart = counter++;
        single.alignmentSpan = counter++;
        single.containerOffset = counter++;
        single.offset = counter++;
        single.size = counter++;
        return single;
    }

    private List<CramCompressionRecord> createMultiRefRecords(final int indexStart,
                                                              final int alignmentStartOffset,
                                                              final Integer... refs) {
        int index = indexStart;
        final List<CramCompressionRecord> records = new ArrayList<>();
        for (final int ref : refs) {
            records.add(CRAMStructureTestUtil.createMappedRecord(index, ref, alignmentStartOffset + index));
            index++;
        }
        return records;
    }

    private void assertEntryForSlice(final CRAIEntry entry, final Slice slice) {
        assertEntryForSlice(entry, slice.getReferenceContext().getSerializableId(),
                slice.alignmentStart, slice.alignmentSpan, slice.containerOffset, slice.offset, slice.size);
     }

    private void assertEntryForSlice(final CRAIEntry entry,
                                     final int sequenceId,
                                     final int alignmentStart,
                                     final int alignmentSpan,
                                     final long containerOffset,
                                     final int sliceByteOffset,
                                     final int sliceByteSize) {
        Assert.assertEquals(entry.getSequenceId(), sequenceId);
        Assert.assertEquals(entry.getAlignmentStart(), alignmentStart);
        Assert.assertEquals(entry.getAlignmentSpan(), alignmentSpan);
        Assert.assertEquals(entry.getContainerStartByteOffset(), containerOffset);
        Assert.assertEquals(entry.getSliceByteOffset(), sliceByteOffset);
        Assert.assertEquals(entry.getSliceByteSize(), sliceByteSize);
    }

    public static CRAIEntry newEntry(final int seqId, final int start, final int span) {
        return new CRAIEntry(seqId, start, span, 0, 0, 0);
    }

    public static CRAIEntry newEntryContOffset(final int containerStartOffset) {
        return new CRAIEntry(1, 0, 0, containerStartOffset, 0, 0);
    }
}
