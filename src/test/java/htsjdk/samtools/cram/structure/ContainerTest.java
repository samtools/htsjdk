package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.build.CRAMReferenceRegion;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.ReferenceContext;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class ContainerTest extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 2000;
    private static final long CONTAINER_BYTE_OFFSET = 536635;

    @DataProvider(name = "singleContainerAlignmentContextData")
    private Object[][] singleContainerAlignmentContextData() {
        return new Object[][]{
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(
                                TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        new AlignmentContext(
                                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO), 1,
                                TEST_RECORD_COUNT + CRAMStructureTestHelper.READ_LENGTH - 1)
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(
                                TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE),
                        new AlignmentContext(
                                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE), 1,
                                TEST_RECORD_COUNT + CRAMStructureTestHelper.READ_LENGTH - 1)
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(TEST_RECORD_COUNT),
                        AlignmentContext.UNMAPPED_UNPLACED_CONTEXT
                },
        };
    }

    @Test(dataProvider = "singleContainerAlignmentContextData")
    public void testSingleContainerAlignmentContext(
            final List<SAMRecord> samRecords,
            final AlignmentContext expectedAlignmentContext) {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy()
                // in order to set reads/slice to a small number, we must do the same for minimumSingleReferenceSliceSize
                .setMinimumSingleReferenceSliceSize(samRecords.size())
                .setReadsPerSlice(samRecords.size());
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                encodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, samRecords, CONTAINER_BYTE_OFFSET);
        CRAMStructureTestHelper.assertContainerState(container, expectedAlignmentContext, CONTAINER_BYTE_OFFSET);
    }

    @DataProvider(name = "multiContainerAlignmentContextData")
    private Object[][] multiContainerAlignmentContextData() {

        final List<SAMRecord> bothReferenceSequenceRecords = new ArrayList<>();
        bothReferenceSequenceRecords.addAll(
                CRAMStructureTestHelper.createSAMRecordsMapped(
                        TEST_RECORD_COUNT,
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO)
        );
        bothReferenceSequenceRecords.addAll(
                CRAMStructureTestHelper.createSAMRecordsMapped(
                        TEST_RECORD_COUNT,
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE)
        );

        final List<SAMRecord> allRecords = new ArrayList<>();
        allRecords.addAll(bothReferenceSequenceRecords);
        allRecords.addAll(CRAMStructureTestHelper.createSAMRecordsUnmapped(TEST_RECORD_COUNT));

        return new Object[][]{
                { bothReferenceSequenceRecords,
                        Arrays.asList(
                            CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                            CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE) },
                { allRecords,
                        Arrays.asList(
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE,
                                ReferenceContext.UNMAPPED_UNPLACED_ID) },
        };
    }

    @Test(dataProvider = "multiContainerAlignmentContextData")
    public void testMultiContainerAlignmentContext(
            final List<SAMRecord> samRecords,
            final List<ReferenceContext> referenceContexts) {
        final CRAMEncodingStrategy encodingStrategy = new CRAMEncodingStrategy()
                .setSlicesPerContainer(1)
                .setReadsPerSlice(samRecords.size());
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                encodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<Container> containers = CRAMStructureTestHelper.createContainers(containerFactory, samRecords);
        Assert.assertEquals(containers.size(), referenceContexts.size());
        for (int i = 0; i < referenceContexts.size(); i++) {
            Assert.assertEquals(
                    containers.get(i).getAlignmentContext().getReferenceContext().getReferenceContextID(),
                    referenceContexts.get(i));
        }
    }

    @DataProvider(name = "cigarWithNExtents")
    private Object[][] getCigarWithNExtents() {
        return new Object[][] {
                // start, readLength, cigar, extents
                { 1, 20, "10M1N10M", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        1,
                        21) },
                { 5, 20, "10M1N10M", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        5,
                        21) },
                { 5, 20, "10M1N10M", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        5,
                        21) },
                { 1, 1, "1N", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        1,
                        2) },
                { 10, 1, "1N", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        10,
                        2) },
                { 5, 10, "1N10M10N", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        5,
                        21) },
                { 5, 10, "10M10N", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        5,
                        20) },
                { 5, 20, "5N20M", new AlignmentContext(
                        new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                        5,
                        25) },
        };
    }

    @Test(dataProvider = "cigarWithNExtents")
    public void testExtentsForNCigarOperator(
            final int alignmentStart,
            final int readLength,
            final String cigarString,
            final AlignmentContext expectedAlignmentContext) {
        // test for https://github.com/samtools/htsjdk/issues/1088
        final SAMRecord samRecord = new SAMRecord(CRAMStructureTestHelper.SAM_FILE_HEADER);
        samRecord.setReferenceIndex(expectedAlignmentContext.getReferenceContext().getReferenceSequenceID());
        samRecord.setAlignmentStart(alignmentStart);
        samRecord.setReadName("testRead");
        CRAMStructureTestHelper.addBasesAndQualities(samRecord, readLength);
        samRecord.setCigarString(cigarString);

        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, Collections.singletonList(samRecord), 10);
        Assert.assertEquals(
                container.getAlignmentContext(),
                expectedAlignmentContext);
    }

    @DataProvider(name = "cramVersions")
    private Object[][] cramVersions() {
        return new Object[][] {
                {CramVersions.CRAM_v2_1},
                {CramVersions.CRAM_v3}
        };
    }

    @Test(dataProvider = "cramVersions")
    public void testEOF(final CRAMVersion cramVersion) throws IOException {
        byte[] eofBytes;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CramIO.writeCramEOF(cramVersion, baos);
            eofBytes = baos.toByteArray();
        }

        try (final ByteArrayInputStream bais = new ByteArrayInputStream(eofBytes);
             final CountingInputStream inputStream = new CountingInputStream(bais)) {
            final Container container = new Container(cramVersion, inputStream, inputStream.getCount());
            Assert.assertTrue(container.isEOF());
        }
    }

    @DataProvider(name = "getRecordsTestCases")
    private Object[][] getRecordsTestCases() {

        return new Object[][]{
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(TEST_RECORD_COUNT,
                                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(TEST_RECORD_COUNT),
                },

                // The records in these next two tests are unmapped but only "half" placed: they have either
                // a valid reference index, or a valid start position, but not both.
                // The first kind (valid reference but no start) lose their reference index when round-tripping;
                // the second kind lose their alignment start.
                //{
                //        CRAMStructureTestHelper.createSAMRecordsUnmappedWithReferenceIndex(
                //                TEST_RECORD_COUNT,
                //                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                //},
                //{
                //        CRAMStructureTestHelper.createSAMRecordsUnmappedWithAlignmentStart(TEST_RECORD_COUNT),
                //},
        };
    }

    @Test(dataProvider = "getRecordsTestCases")
    public void getRecordsTest(final List<SAMRecord> originalRecords) {
        final long dummyByteOffset = 0;
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, originalRecords, dummyByteOffset);

        final List<SAMRecord> roundTripRecords = container.getSAMRecords(
                ValidationStringency.STRICT,
                new CRAMReferenceRegion(CRAMStructureTestHelper.REFERENCE_SOURCE, CRAMStructureTestHelper.SAM_FILE_HEADER),
                new CompressorCache(),
                CRAMStructureTestHelper.SAM_FILE_HEADER
        );
        Assert.assertEquals(roundTripRecords.size(), TEST_RECORD_COUNT);

        // SAMRecords model referenceIndex and mateReferenceIndex using boxed integers. Semantically, null and -1
        // are equivalent, but SAMRecord.equals treats them as different, so we need to normalize the records
        // before we can compare them.
        for (final SAMRecord samRecord: originalRecords) {
            samRecord.setMateReferenceIndex(samRecord.getMateReferenceIndex());
        }

        // Container round-trips CRAM records,so perhaps these tests should use CRAM records, and
        // there should be a CRAMNormalizer test for round-tripping SAMRecords
        Assert.assertEquals(roundTripRecords, originalRecords);
    }

}
