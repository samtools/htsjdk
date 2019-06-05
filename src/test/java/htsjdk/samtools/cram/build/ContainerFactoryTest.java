package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordQueryNameComparator;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ContainerFactoryTest extends HtsjdkTest {

    // wrapper class to suppress expansion/serialization of lists of records during test execution
    private static class RecordSupplier implements Supplier<List<SAMRecord>> {
        final List<SAMRecord> samRecords;
        public RecordSupplier(final List<SAMRecord> samRecords) { this.samRecords = samRecords; }
        @Override public List<SAMRecord> get() { return samRecords; }
    }

    @DataProvider(name="singleContainerSliceDistribution")
    private Object[][] getSingleContainerSliceDistribution() {
        final int RECORDS_PER_SLICE = 100;
        return new Object[][] {
                // samRecords, records/slice, slices/container, expected container refContext, expected slice count,
                // expected record count/slice, expected slice refContexts,
                {
                        // 1 full single-ref slice with 1 rec
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(1, 0)),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with 1 rec, but allow > 1 slices/container
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(1, 0)),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        1, Arrays.asList(1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE - 1 records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0)),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE - 1), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE, 0)),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE), Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 2 single-ref slices, one with RECORDS_PER_SLICE records, one with 1 record
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE + 1, 0)),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, 1),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // 2 single-ref slices, one with RECORDS_PER_SLICE records, one with RECORDS_PER_SLICE - 1
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped((RECORDS_PER_SLICE * 2) - 1, 0)),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE - 1),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // 2 full single-ref slices, each with RECORDS_PER_SLICE records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0)),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // 3 single-ref slices, two with RECORDS_PER_SLICE records, one with 1 record
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped((RECORDS_PER_SLICE * 2) + 1, 0)),
                        RECORDS_PER_SLICE, 3,
                        new ReferenceContext(0),
                        3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, 1),
                        Arrays.asList(
                                new ReferenceContext(0),
                                new ReferenceContext(0),
                                new ReferenceContext(0))
                },
                {
                        // 3 full single-ref slices, each with RECORDS_PER_SLICE records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 3, 0)),
                        RECORDS_PER_SLICE, 3,
                        new ReferenceContext(0),
                        3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0), new ReferenceContext(0))
                },

                // now repeat the tests, but using unmapped records
                {
                        // 1 full single-ref (unmapped) slice with 1 rec
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsUnmapped(1)),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with 1 rec, but allow > 1 slices/container
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsUnmapped(1)),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with RECORDS_PER_SLICE - 1 records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE - 1)),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(RECORDS_PER_SLICE - 1), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 1 full single-ref (unmapped) slice with RECORDS_PER_SLICE records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE)),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        1, Arrays.asList(RECORDS_PER_SLICE), Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 2 single-ref (unmapped) slices, one with RECORDS_PER_SLICE records, one with 1 record
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE + 1)),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        2, Arrays.asList(RECORDS_PER_SLICE, 1),
                        Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 2 full single-ref (unmapped) slices, each with RECORDS_PER_SLICE records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE * 2)),
                        RECORDS_PER_SLICE, 2,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },
                {
                        // 3 full single-ref (unmapped) slices, each with RECORDS_PER_SLICE records
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE * 3)),
                        RECORDS_PER_SLICE, 3,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                        3, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT)
                },

                // now use a mix of mapped and unmapped/placed
                // NOTE: a mapped reference context can contain unmapped records if they are placed, so really mapped
                // reference context would be more correctly called a "placed" reference context
                {
                        // 1 full single-ref mapped slice with RECORDS_PER_SLICE records
                        new RecordSupplier(Stream.of(
                                Collections.singletonList(CRAMStructureTestHelper.createSAMRecordUnmappedPlaced(0, 1)),
                                CRAMStructureTestHelper.createSAMRecordsMapped((RECORDS_PER_SLICE / 2) - 2, 0),
                                Collections.singletonList(CRAMStructureTestHelper.createSAMRecordUnmappedPlaced(0, 1)))
                                .flatMap(List::stream)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE / 2, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE / 2), Arrays.asList(new ReferenceContext(0))
                },

                // now queryname sorted
                {
                        // 1 full single-ref slice with RECORDS_PER_SLICE-1 records
                        new RecordSupplier(CRAMStructureTestHelper.createQueryNameSortedSAMRecords(RECORDS_PER_SLICE - 1,0)),
                        RECORDS_PER_SLICE, 1,
                        new ReferenceContext(0),
                        1, Arrays.asList(RECORDS_PER_SLICE - 1),
                        Arrays.asList(new ReferenceContext(0))
                },
                {
                        // 2 full single-ref slices, one with with RECORDS_PER_SLICE records and one record
                        new RecordSupplier(CRAMStructureTestHelper.createQueryNameSortedSAMRecords(RECORDS_PER_SLICE + 1,0)),
                        RECORDS_PER_SLICE, 2,
                        new ReferenceContext(0),
                        2, Arrays.asList(RECORDS_PER_SLICE, 1),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0))
                },
                {
                        // use a small number of records from multiple (not coordinate-ordered) reference contexts to
                        // force creation of a multi-ref container
                        new RecordSupplier(Stream.of(
                                CRAMStructureTestHelper.createQueryNameSortedSAMRecords(2,1),
                                CRAMStructureTestHelper.createQueryNameSortedSAMRecords(2,0),
                                CRAMStructureTestHelper.createQueryNameSortedSAMRecords(2,1))
                                .flatMap(List::stream)
                                // even though the individual lists in the stream are sorted, force sort the aggregate stream
                                .sorted(new SAMRecordQueryNameComparator()::compare)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE, 1,
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                        1, Arrays.asList(6),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)
                }
        };
    }

    @Test(dataProvider = "singleContainerSliceDistribution")
    public void testSingleContainerSliceDistribution(
            final Supplier<List<SAMRecord>> samRecordSupplier,
            final int readsPerSlice,
            final int slicesPerContainer,
            final ReferenceContext expectedContainerReferenceContext,
            final int expectedSliceCount,
            final List<Integer> expectedSliceRecordCounts,
            final List<ReferenceContext> expectedSliceReferenceContexts) {
        final List<SAMRecord> samRecords = samRecordSupplier.get();
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
                // expected container count, expected record count per container, expected reference context per container,
                // expected slices per container
                {
                        // this generates two containers since it has two containers worth of records mapped to a single ref
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0)),
                        RECORDS_PER_SLICE, 1,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0)),
                        Arrays.asList(1, 1)
                },
                {
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 3 + 1, 0)),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE+ 1),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0)),
                        Arrays.asList(2, 2)
                },
                {
                        new RecordSupplier(CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 4, 0)),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE * 2),
                        Arrays.asList(new ReferenceContext(0), new ReferenceContext(0)),
                        Arrays.asList(2, 2)
                },
                {
                        // this generates one container because although it has records mapped to two different reference
                        // contigs, there aren't enough records to reach the minimum single ref threshold
                        new RecordSupplier(Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createSAMRecordsMapped(1, 1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT),
                        Arrays.asList(1)
                },
                {
                        // this generates one container because it has some mapped (but not enough to reach the
                        // minimum single ref threshold), and one unmapped
                        new RecordSupplier(Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE - 1, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(1))
                                .flatMap(List::stream)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE, 1,
                        1, Arrays.asList(RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT),
                        Arrays.asList(1)
                },
                {
                        // this generates two containers since it has some mapped and one unmapped
                        new RecordSupplier(Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE * 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE * 2, RECORDS_PER_SLICE),
                        Arrays.asList(new ReferenceContext(0), ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                        Arrays.asList(2, 1)
                },
                {
                        // this generates two containers since it has some mapped, but not enough to emit a single
                        // ref container, and one unmapped, which goes into a second container
                        new RecordSupplier(Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE / 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE, 1,
                        // first container is multi ref and contains half of the records from the first set of mapped
                        // records; the remaining go into the second container
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE / 2),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                        Arrays.asList(1, 1)
                },
                {
                        // even though we allow 2 slices/container, this generates two containers since it has
                        // some mapped and one unmapped, and we won't emit a second single-ref slice into the
                        // first container once we've emitted the multi-ref slice!
                        new RecordSupplier(Stream.of(
                                CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE / 2, 0),
                                CRAMStructureTestHelper.createSAMRecordsUnmapped(RECORDS_PER_SLICE))
                                .flatMap(List::stream)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE, 2,
                        // first container is multi ref and contains the records from the first set of mapped
                        // records (which are not enough to reach the minimum single-ref threshold), plus half of
                        // the unmapped records; the unmapped remaining go into the second container
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE / 2),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, ReferenceContext.UNMAPPED_UNPLACED_CONTEXT),
                        Arrays.asList(1, 1)
                },

                // queryname sorted
                {
                        // use records from multiple (not coordinate-ordered) reference contexts to
                        // force creation of one multi-ref (after name sorting, the first container has a mix of
                        // ref 0 and ref 1), and one single ref container
                        new RecordSupplier(Stream.of(
                                CRAMStructureTestHelper.createQueryNameSortedSAMRecords(RECORDS_PER_SLICE/2,1),
                                CRAMStructureTestHelper.createQueryNameSortedSAMRecords(RECORDS_PER_SLICE/2,0),
                                CRAMStructureTestHelper.createQueryNameSortedSAMRecords(RECORDS_PER_SLICE,1))
                                .flatMap(List::stream)
                                // even though the individual lists in the stream are sorted, force sort the aggregate stream
                                .sorted(new SAMRecordQueryNameComparator()::compare)
                                .collect(Collectors.toList())),
                        RECORDS_PER_SLICE, 2,
                        2, Arrays.asList(RECORDS_PER_SLICE, RECORDS_PER_SLICE, RECORDS_PER_SLICE),
                        Arrays.asList(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, new ReferenceContext(1)),
                        Arrays.asList(1, 1)
                },
        };
    }

    @Test(dataProvider = "multipleContainerSliceDistribution")
    public void testMultipleContainerRecordsPerContainer(
            final Supplier<List<SAMRecord>> samRecordSupplier,
            final int readsPerSlice,
            final int slicesPerContainer,
            final int expectedContainerCount,
            final List<Integer> expectedContainerRecordCounts,
            final List<ReferenceContext> expectedContainerReferenceContexts,
            final List<Integer> expectedSlicesPerContainer
    ) {
        final List<SAMRecord> samRecords = samRecordSupplier.get();
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
        Assert.assertEquals(containers.size(), expectedSlicesPerContainer.size());

        for (int i = 0; i < containers.size(); i++) {
            final Container container = containers.get(i);
            Assert.assertEquals(
                    (Integer) container.getContainerHeader().getNumberOfRecords(),
                    expectedContainerRecordCounts.get(i));
            Assert.assertEquals(container.getAlignmentContext().getReferenceContext(), expectedContainerReferenceContexts.get(i));
            Assert.assertEquals(java.util.Optional.of(container.getSlices().size()).get(), expectedSlicesPerContainer.get(i));
        }
    }

}
