package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Ref;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ContainerFactoryTest extends HtsjdkTest {

    @DataProvider(name="singleContainerSliceDistribution")
    private Object[][] getSingleContainerSliceDistribution() {
        final int RECORDS_PER_SLICE = 100;
        return new Object[][] {
                // samRecords, records/slice, slices/container, expected refContext id, expected slice count,
                // expected slice refContexts, expected record count/slice
                {
                        // 1 full single-ref slice with 1 rec
                        CRAMStructureTestHelper.createSAMRecordsMapped(1, 0),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with 1 rec, but allow > 1 slices/container
                        CRAMStructureTestHelper.createSAMRecordsMapped(1, 0),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        1, Arrays.asList(1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE - 1 records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE - 1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE, 0),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 2 single-ref slices, one with RECORDS_PER_SLICE records, one with 1 record
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE + 1, 0),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, 1),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // 2 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // 3 full single-ref slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 3, 0),
                        RECORDS_PER_SLICE, 3,
                        new ReferenceContext(0),
                        3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0), new ReferenceContext(0))
                },

                // now repeat the tests, but using unmapped records
                {
                        // 1 full single-ref (unmapped) slice with 1 rec
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(1),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with 1 rec, but allow > 1 slices/container
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(1),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with RECORDS_PER_SLICE - 1 records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE - 1),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(RECORDS_PER_SLICE - 1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(RECORDS_PER_SLICE), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 2 single-ref (unmapped) slices, one with RECORDS_PER_SLICE records, one with 1 record
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE + 1),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        2, Arrays.asList(RECORDS_PER_SLICE, 1),
                        Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 2 full single-ref (unmapped) slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE * 2),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 3 full single-ref (unmapped) slices, each with RECORDS_PER_SLICE records
                        CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE * 3),
                        RECORDS_PER_SLICE, 3,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
        };
    }

    @Test(dataProvider = "singleContainerSliceDistribution")
    public void testSingleContainerSliceDistribution(
            final List<SAMRecord> samRecords,
            final int readsPerSlice,
            final int slicesPerContainer,
            final ReferenceContext expectedContainerReferenceContext,
            final int expectedSliceCount,
            final List<Integer> expectedSliceRecordCounts,
            final List<ReferenceContext> expectedSliceReferenceContexts) {
        final CRAMEncodingStrategy cramEncodingStrategy =
                new CRAMEncodingStrategy()
                        .setMinimumSingleReferenceSliceSize(readsPerSlice)
                        .setReadsPerSlice(readsPerSlice)
                        .setSlicesPerContainer(slicesPerContainer);
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                cramEncodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, samRecords, 0);
        Assert.assertNotNull(container);
        Assert.assertEquals(container.getAlignmentContext().getReferenceContext(), expectedContainerReferenceContext);

        final List<Slice> slices = container.getSlices();
        Assert.assertEquals(slices.size(), expectedSliceCount);
        for (int i = 0; i < slices.size(); i++) {
            Assert.assertEquals(
                    (Integer) slices.get(i).getNumberOfRecords(),
                    expectedSliceRecordCounts.get(i));
            Assert.assertEquals(
                    slices.get(i).getAlignmentContext().getReferenceContext(),
                    expectedSliceReferenceContexts.get(i));
        }
    }

    @DataProvider(name="multipleContainerSliceDistribution")
    private Object[][] getMultipleContainerSliceDistribution() {
        final int RECORDS_PER_SLICE = 100;
        return new Object[][] {
                // input samRecords, records/slice, slices/container,
                // expected container count, expected record count per container, expected reference context per container
                {
                        // this generates two containers since it has two containers worth of records mapped to a single ref
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0),
                        RECORDS_PER_SLICE, 1,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 3 + 1, 0),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE+ 1),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 4, 0),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE * 2),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // this generates one container because although it has records mapped to two different reference
                        // contigs, there aren't enough records to reach the minimum single ref threshold
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createSAMRecordsMapped(1, 1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)
                },
                {
                        // this generates one container because it has some mapped (but not enough to reach the
                        // minimum single ref threshold), and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // this generates two containers since it has some mapped, but not enough to emit a single
                        // ref container, and one unmapped, which goes into a second container
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE / 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 1,
                        // first container is multi ref and contains half of the records from the first set of mapped
                        // records; the remaining go into the second container
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE / 2),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // even though we allow 2 slices/container, this generates two containers since it has
                        // some mapped and one unmapped, and we won't emit a second single-ref slice into the
                        // first container once we've emitted the multi-ref slice!
                        Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE / 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList()),
                        RECORDS_PER_SLICE, 2,
                        // first container is multi ref and contains the records from the first set of mapped
                        // records (which are not enough to reach the minimum single-ref threshold), plus half of
                        // the unmapped records; the unmapped remaining go into the second container
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE / 2),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
        };
    }

    @Test(dataProvider = "multipleContainerSliceDistribution")
    public void testMultipleContainerRecordsPerContainer(
        final List<SAMRecord> samRecords,
        final int readsPerSlice,
        final int slicesPerContainer,
        final int expectedContainerCount,
        final List<Integer> expectedContainerRecordCounts,
        final List<ReferenceContext> expectedContainerReferenceContexts
    ) {
        final CRAMEncodingStrategy cramEncodingStrategy =
                new CRAMEncodingStrategy()
                    .setMinimumSingleReferenceSliceSize(readsPerSlice)
                    .setReadsPerSlice(readsPerSlice)
                    .setSlicesPerContainer(slicesPerContainer);
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                cramEncodingStrategy,
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<Container> containers = CRAMStructureTestHelper.createContainers(containerFactory, samRecords);

        Assert.assertEquals(containers.size(), expectedContainerCount);
        Assert.assertEquals(containers.size(), expectedContainerReferenceContexts.size());

        for (int i = 0; i < containers.size(); i++) {
            final Container container = containers.get(i);
            Assert.assertEquals(
                    (Integer) container.getContainerHeader().getNumberOfRecords(),
                    expectedContainerRecordCounts.get(i));
            Assert.assertEquals(container.getAlignmentContext().getReferenceContext(), expectedContainerReferenceContexts.get(i));
        }
    }

}
