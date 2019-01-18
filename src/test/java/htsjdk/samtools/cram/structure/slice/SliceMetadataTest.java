package htsjdk.samtools.cram.structure.slice;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.structure.CompressionHeader;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

public class SliceMetadataTest extends HtsjdkTest {
    @Test
    public void addMappedTest() {
        final MappedSliceMetadata a = new MappedSliceMetadata(1, 10); // end = 10
        final MappedSliceMetadata b = new MappedSliceMetadata(10, 10, 2); // end = 19
        final MappedSliceMetadata c = a.add(b);

        Assert.assertEquals(c.getAlignmentStart(), 1);
        Assert.assertEquals(c.getAlignmentSpan(), 19);
        Assert.assertEquals(c.getRecordCount(), 3);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void addMappedFailTest() {
        final UnmappedSliceMetadata a = new UnmappedSliceMetadata(2638);
        final MappedSliceMetadata b = new MappedSliceMetadata(1, 10);
        a.add(b);
    }

    @Test
    public void addUnmappedTest() {
        final UnmappedSliceMetadata a = new UnmappedSliceMetadata(456);
        final UnmappedSliceMetadata b = new UnmappedSliceMetadata(2638);
        final UnmappedSliceMetadata c = a.add(b);

        Assert.assertEquals(c.getRecordCount(), 456 + 2638);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void addUnmappedFailTest() {
        final MappedSliceMetadata a = new MappedSliceMetadata(1, 10);
        final UnmappedSliceMetadata b = new UnmappedSliceMetadata(2638);
        a.add(b);
    }

    @Test
    public void combineTest() {
        final SliceMetadata a = new MappedSliceMetadata(1, 10); // end = 10
        final SliceMetadata b = new MappedSliceMetadata(10, 10, 2); // end = 19
        final SliceMetadata c = SliceMetadata.combine(a, b);

        Assert.assertEquals(((MappedSliceMetadata)c).getAlignmentStart(), 1);
        Assert.assertEquals(((MappedSliceMetadata)c).getAlignmentSpan(), 19);
        Assert.assertEquals(c.getRecordCount(), 3);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void combineFailTest() {
        final SliceMetadata a = new MappedSliceMetadata(1, 10);
        final SliceMetadata b = new UnmappedSliceMetadata(2638);
        SliceMetadata.combine(a, b);
    }

    private static Slice newSingleSlice(final int refId,
                                        final int alignmentStart,
                                        final int alignmentSpan,
                                        final int recordCount) {
        final Slice s = new Slice();
        s.sequenceId = refId;
        s.alignmentStart = alignmentStart;
        s.alignmentSpan = alignmentSpan;
        s.nofRecords = recordCount;
        return s;
    }

    private static Container newMultiRefContainer() {
        final SAMFileHeader samHeader = new SAMFileHeader();
        samHeader.addSequence(new SAMSequenceRecord("1", 100));
        samHeader.addSequence(new SAMSequenceRecord("2", 200));

         final ContainerFactory factory = new ContainerFactory(samHeader, Integer.MAX_VALUE);

        final byte[] bases = "AAAAA".getBytes();
        final int readLength = bases.length;    // 5

        final List<CramCompressionRecord> records = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final CramCompressionRecord record = new CramCompressionRecord();
            record.setSegmentUnmapped(false);
            record.sequenceId = i % 2;
            record.alignmentStart = 1 + i;
            record.readLength = readLength;
            // the following are not used by this test but required to avoid NPE
            record.readBases = record.qualityScores = bases;
            record.readName = Integer.toString(i);
            record.readFeatures = Collections.emptyList();
            records.add(record);
        }

        final Container container = factory.buildContainer(records);
        Assert.assertNotNull(container);
        Assert.assertEquals(container.nofRecords, records.size());
        Assert.assertEquals(container.sequenceId, Slice.MULTI_REFERENCE);
        Assert.assertEquals(container.slices.length, 1);
        Assert.assertEquals(container.slices[0].sequenceId, Slice.MULTI_REFERENCE);
        return container;
    }

    @DataProvider(name = "sliceTest")
    private static Object[][] sliceTestData() {
        final Container multiRefContainer = newMultiRefContainer();
        final Slice multiRefSlice = multiRefContainer.slices[0];

        return new Object[][] {
                {
                        newSingleSlice(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, 123, 456, 10),
                        null, // don't need compression header for single-ref test
                        1, // single ref
                        new HashMap<Integer, SliceMetadata>() {{
                            put(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX, new UnmappedSliceMetadata(10));
                        }}
                },
                {
                        newSingleSlice(5, 789, 1234, 3),
                        null, // don't need compression header for single-ref test
                        1, // single ref
                        new HashMap<Integer, SliceMetadata>() {{
                            put(5,  new MappedSliceMetadata(789, 1234, 3));
                        }}
                },
                {
                        multiRefSlice,
                        multiRefContainer.header,
                        2,
                        new HashMap<Integer, SliceMetadata>() {{
                            // two records for sequence 0: 1-5 and 3-7
                            put(0, new MappedSliceMetadata(1, 7, 2));
                            // two records for sequence 1: 2-6 and 4-8
                            put(1, new MappedSliceMetadata(2, 7, 2));
                        }}
                }
        };
    }

    @Test(dataProvider = "sliceTest")
    public void getTest(final Slice slice,
                        final CompressionHeader compressionHeader,
                        final int referenceCount,
                        final Map<Integer, SliceMetadata> metadata) {

        final Map<Integer, SliceMetadata> metadataMap = slice.getAlignmentMetadata(compressionHeader, ValidationStringency.SILENT);
        Assert.assertEquals(metadataMap.size(), referenceCount);
        Assert.assertEqualsDeep(metadataMap, metadata);
    }

    @Test
    public void testSingleRefContainer() {
        SAMFileHeader samFileHeader = new SAMFileHeader();
        ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        List<CramCompressionRecord> records = new ArrayList<>();
        for (int i=0; i<10; i++) {
            CramCompressionRecord record = new CramCompressionRecord();
            record.readBases="AAA".getBytes();
            record.qualityScores="!!!".getBytes();
            record.setSegmentUnmapped(false);
            record.readName=""+i;
            record.sequenceId=0;
            record.setLastSegment(true);
            record.readFeatures = Collections.emptyList();

            records.add(record);
        }

        Container container = factory.buildContainer(records);
        Assert.assertEquals(container.nofRecords, 10);
        Assert.assertEquals(container.sequenceId, 0);

        final Map<Integer, SliceMetadata> referenceMap = container.getSliceMetadata(ValidationStringency.STRICT);
        Assert.assertNotNull(referenceMap);
        Assert.assertEquals(referenceMap.size(), 1);
        Assert.assertTrue(referenceMap.containsKey(0));
    }

    @Test
    public void testUnmappedContainer() {
        SAMFileHeader samFileHeader = new SAMFileHeader();
        ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        List<CramCompressionRecord> records = new ArrayList<>();
        for (int i=0; i<10; i++) {
            CramCompressionRecord record = new CramCompressionRecord();
            record.readBases="AAA".getBytes();
            record.qualityScores="!!!".getBytes();
            record.setSegmentUnmapped(true);
            record.readName=""+i;
            record.sequenceId= SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
            record.setLastSegment(true);

            records.add(record);
        }

        Container container = factory.buildContainer(records);
        Assert.assertEquals(container.nofRecords, 10);
        Assert.assertEquals(container.sequenceId, SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);

        final Map<Integer, SliceMetadata> referenceMap = container.getSliceMetadata(ValidationStringency.STRICT);
        Assert.assertNotNull(referenceMap);
        Assert.assertEquals(referenceMap.size(), 1);
        Assert.assertTrue(referenceMap.containsKey(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX));
    }

    @Test
    public void testMultirefContainer() {
        SAMFileHeader samFileHeader = new SAMFileHeader();
        ContainerFactory factory = new ContainerFactory(samFileHeader, 10);
        List<CramCompressionRecord> records = new ArrayList<>();
        for (int i=0; i<10; i++) {
            CramCompressionRecord record = new CramCompressionRecord();
            record.readBases = "AAA".getBytes();
            record.qualityScores = "!!!".getBytes();
            record.readName = "" + i;
            record.alignmentStart = i + 1;
            record.readLength = 3;

            record.setMultiFragment(false);
            record.sequenceId = i;
            record.readFeatures = Collections.emptyList();
            record.setSegmentUnmapped(false);
            records.add(record);
        }

        Container container = factory.buildContainer(records);
        Assert.assertEquals(container.nofRecords, 10);
        Assert.assertEquals(container.sequenceId, Slice.MULTI_REFERENCE);

        final Map<Integer, SliceMetadata> referenceMap = container.getSliceMetadata(ValidationStringency.STRICT);
        Assert.assertNotNull(referenceMap);
        Assert.assertEquals(referenceMap.size(), 10);
        for (int i=0; i<10; i++) {
            Assert.assertTrue(referenceMap.containsKey(i));
            final SliceMetadata tempMetadata = referenceMap.get(i);
            Assert.assertTrue(tempMetadata instanceof MappedSliceMetadata);
            final MappedSliceMetadata metadata = (MappedSliceMetadata) tempMetadata;
            Assert.assertEquals(metadata.getRecordCount(), 1);
            Assert.assertEquals(metadata.getAlignmentStart(), i+1);
            Assert.assertEquals(metadata.getAlignmentSpan(), 3);
        }
    }
}
