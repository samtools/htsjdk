package htsjdk.samtools.cram;

import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

/**
 * Created by vadim on 25/08/2015.
 */
public class CRAIEntryTest extends CramRecordTestHelper {
    private static final Random RANDOM = new Random(TestUtil.RANDOM_SEED);

    private static final CompressionHeader COMPRESSION_HEADER =
            new CompressionHeaderFactory().build(Collections.EMPTY_LIST, null, true);

    @Test(dataProvider = "uninitializedCRAIParameterTestCases", dataProviderClass = CRAMStructureTestUtil.class, expectedExceptions = CRAMException.class)
    public void uninitializedSliceParameterTest(final Slice s) {
        s.getCRAIEntries(COMPRESSION_HEADER);
    }

    @Test
    public void singleRefTestGetCRAIEntries() {
        final ReferenceContext refContext = new ReferenceContext(0);
        final Slice slice1 = createSliceWithArbitraryValues(refContext);
        final Slice slice2 = createSliceWithArbitraryValues(refContext);

        final long containerByteOffset = 12345;
        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2), COMPRESSION_HEADER, containerByteOffset);
        final List<CRAIEntry> entries = container.getCRAIEntries();

        Assert.assertNotNull(entries);
        Assert.assertEquals(entries.size(), 2);

        assertEntryForSlice(entries.get(0), slice1);
        assertEntryForSlice(entries.get(1), slice2);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void multiRefExceptionTest() {
        final int dummy = 1;
        new CRAIEntry(ReferenceContext.MULTIPLE_REFERENCE_ID, dummy, dummy, dummy, dummy, dummy);
    }

    @Test
    public void multiRefTestGetCRAIEntries() {
        final int sliceAlnSpan = CRAMStructureTestUtil.READ_LENGTH_FOR_TEST_RECORDS;
        final int sliceByteSize = 500;

        final int slice1Index = 0;
        final int slice1AlnStartOffset = 10;
        final int slice1ByteOffsetFromContainer = 750;

        final int slice2Index = 1;
        final int slice2AlnStartOffset = 20;
        final int slice2ByteOffsetFromContainer = slice1ByteOffsetFromContainer + sliceByteSize;

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
        slice1.byteSize = sliceByteSize;
        slice1.byteOffsetFromContainer = slice1ByteOffsetFromContainer;

        slice2.index = slice2Index;
        slice2.byteSize = sliceByteSize;
        slice2.byteOffsetFromContainer = slice2ByteOffsetFromContainer;

        final Container container = Container.initializeFromSlices(Arrays.asList(slice1, slice2), compressionHeader, containerOffset);

        final List<CRAIEntry> entries = container.getCRAIEntries();
        Assert.assertNotNull(entries);
        Assert.assertEquals(entries.size(), 6);

        // slice 1 has index entries for refs 0, 1, 2

        assertEntryForSlice(entries.get(0), 0, slice1AlnStartOffset, sliceAlnSpan, containerOffset, slice1ByteOffsetFromContainer, sliceByteSize);
        assertEntryForSlice(entries.get(1), 1, slice1AlnStartOffset + 1, sliceAlnSpan, containerOffset, slice1ByteOffsetFromContainer, sliceByteSize);
        assertEntryForSlice(entries.get(2), 2, slice1AlnStartOffset + 2, sliceAlnSpan, containerOffset, slice1ByteOffsetFromContainer, sliceByteSize);

        // slice 2 has index entries for refs 2, 3, 4

        assertEntryForSlice(entries.get(3), 2, slice2AlnStartOffset + 3, sliceAlnSpan, containerOffset, slice2ByteOffsetFromContainer, sliceByteSize);
        assertEntryForSlice(entries.get(4), 3, slice2AlnStartOffset + 4, sliceAlnSpan, containerOffset, slice2ByteOffsetFromContainer, sliceByteSize);
        assertEntryForSlice(entries.get(5), 4, slice2AlnStartOffset + 5, sliceAlnSpan, containerOffset, slice2ByteOffsetFromContainer, sliceByteSize);
    }

    @Test
    public void testLineConstructor() {
        int counter = 1;
        final int sequenceId = counter++;
        final int alignmentStart = counter++;
        final int alignmentSpan = counter++;
        final int containerOffset = Integer.MAX_VALUE + counter++;
        final int sliceOffset = counter++;
        final int sliceSize = counter++;

        final String line = String.format("%d\t%d\t%d\t%d\t%d\t%d", sequenceId, alignmentStart, alignmentSpan, containerOffset, sliceOffset, sliceSize);
        final CRAIEntry entry = new CRAIEntry(line);
        assertEntryForSlice(entry, sequenceId, alignmentStart, alignmentSpan, containerOffset, sliceOffset, sliceSize);
    }

    @DataProvider(name = "intersectionTestCases")
    public Object[][] intersectionTestCases() {
        final CRAIEntry basic = craiEntryForIntersectionTests(1, 1, 10);
        final CRAIEntry overlapBasic = craiEntryForIntersectionTests(1, 5, 10);
        final CRAIEntry insideBasic = craiEntryForIntersectionTests(1, 3, 5);

        final CRAIEntry otherSeq1 = craiEntryForIntersectionTests(2, 1, 10);
        final CRAIEntry otherSeq2 = craiEntryForIntersectionTests(2, 2, 10);

        final CRAIEntry zerospan = craiEntryForIntersectionTests(1, 1, 0);

        // start and span values are invalid here: show that they are ignored
        final CRAIEntry unmapped = craiEntryForIntersectionTests(ReferenceContext.UNMAPPED_UNPLACED_ID, 1, 2);

        return new Object[][]{
                {basic, basic, true},
                {basic, overlapBasic, true},
                {basic, insideBasic, true},

                {basic, otherSeq1, false},
                {basic, otherSeq2, false},
                {otherSeq1, otherSeq2, true},

                {basic, zerospan, false},
                {zerospan, zerospan, false},

                // intersections with Unmapped entries are always false, even with themselves

                {basic, unmapped, false},
                {unmapped, unmapped, false},
        };
    }

    @Test(dataProvider = "intersectionTestCases")
    public void testIntersect(final CRAIEntry a, final CRAIEntry b, final boolean expectation) {
        Assert.assertEquals(CRAIEntry.intersect(a, b), expectation);
        Assert.assertEquals(CRAIEntry.intersect(b, a), expectation);
    }

    private CRAIEntry craiEntryForIntersectionTests(final int sequenceId, final int alignmentStart, final int alignmentSpan) {
        final int dummy = -1;
        return new CRAIEntry(sequenceId, alignmentStart, alignmentSpan, dummy, dummy, dummy);
    }

    // test that index entries are sorted correctly:
    // first by numerical order of reference sequence ID, except that the unmapped-unplaced sentinel value comes last
    //
    // for valid reference sequence ID (placed):
    // - sort by alignment start
    // - if alignment start is equal, sort by container offset
    // - if alignment start and container offset are equal, sort by slice offset
    //
    // for unmapped-unplaced:
    // - ignore (invalid) alignment start value
    // - sort by container offset
    // - if container offset is equal, sort by slice offset

    // alignmentSpan and sliceSize are irrelevant to index sorting
    private final int dummyValue = 1000000;

    // these unmapped CRAIEntries should sort as: 4, 3, 1, 2
    // reasoning:
    // first-order sorting within unmapped is by container offset -> [3 and 4], 1, 2
    // next-order sorting is by slice offset, so 4 comes first -> 4, 3, 1, 2

    private final CRAIEntry unmapped1 = new CRAIEntry(ReferenceContext.UNMAPPED_UNPLACED_ID, 3, dummyValue, 100, 100, dummyValue);
    private final CRAIEntry unmapped2 = new CRAIEntry(ReferenceContext.UNMAPPED_UNPLACED_ID, 2, dummyValue, 120, 200, dummyValue);
    private final CRAIEntry unmapped3 = new CRAIEntry(ReferenceContext.UNMAPPED_UNPLACED_ID, 4, dummyValue, 90, 100, dummyValue);
    private final CRAIEntry unmapped4 = new CRAIEntry(ReferenceContext.UNMAPPED_UNPLACED_ID, 5, dummyValue, 90, 50, dummyValue);

    // these placed CRAIEntries should sort per sequenceId as: 4, 2, 1, 5, 3
    // reasoning:
    // first-order sorting within sequenceId is by alignment start -> [2 and 4], 1, [3 and 5]
    // next-order sorting is by container offset, so 4 comes first -> 4, 2, 1, [3 and 5]
    // final-order sorting is by slice offset, so 5 comes first -> 4, 2, 1, 5, 3

    private CRAIEntry placed1ForId(final int sequenceId) {
        return new CRAIEntry(sequenceId, 3, dummyValue, 100, 100, dummyValue);
    }

    private CRAIEntry placed2ForId(final int sequenceId) {
        return new CRAIEntry(sequenceId, 2, dummyValue, 120, 200, dummyValue);
    }

    private CRAIEntry placed3ForId(final int sequenceId) {
        return new CRAIEntry(sequenceId, 4, dummyValue, 90, 100, dummyValue);
    }

    private CRAIEntry placed4ForId(final int sequenceId) {
        return new CRAIEntry(sequenceId, 2, dummyValue, 90, 50, dummyValue);
    }

    private CRAIEntry placed5ForId(final int sequenceId) {
        return new CRAIEntry(sequenceId, 4, dummyValue, 90, 80, dummyValue);
    }

    @Test
    public void testCompareTo() {
        final List<CRAIEntry> testEntries = new ArrayList<CRAIEntry>() {{
            add(unmapped1);
            add(unmapped2);
            add(unmapped3);
            add(unmapped4);
            add(placed1ForId(1));
            add(placed2ForId(1));
            add(placed3ForId(1));
            add(placed4ForId(1));
            add(placed5ForId(1));
            add(placed1ForId(0));
            add(placed2ForId(0));
            add(placed3ForId(0));
            add(placed4ForId(0));
            add(placed5ForId(0));
        }};

        // ref ID 0, then ref ID 1, then unmapped
        // within valid ref ID = 4, 2, 1, 5, 3 (see above)
        // within unmapped = 4, 3, 1, 2 (see above)

        final List<CRAIEntry> expected = new ArrayList<CRAIEntry>() {{
            add(placed4ForId(0));
            add(placed2ForId(0));
            add(placed1ForId(0));
            add(placed5ForId(0));
            add(placed3ForId(0));

            add(placed4ForId(1));
            add(placed2ForId(1));
            add(placed1ForId(1));
            add(placed5ForId(1));
            add(placed3ForId(1));

            add(unmapped4);
            add(unmapped3);
            add(unmapped1);
            add(unmapped2);
        }};

        Collections.sort(testEntries);
        Assert.assertEquals(testEntries, expected);
    }

    private static Slice createSliceWithArbitraryValues(final ReferenceContext refContext) {
        int counter = RANDOM.nextInt(100);

        final Slice single = new Slice(refContext);
        single.alignmentStart = counter++;
        single.alignmentSpan = counter++;
        single.containerByteOffset = counter++;
        single.byteOffsetFromContainer = counter++;
        single.byteSize = counter++;
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
                slice.alignmentStart, slice.alignmentSpan, slice.containerByteOffset, slice.byteOffsetFromContainer, slice.byteSize);
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
}
