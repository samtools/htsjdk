package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.cram.BAIEntry;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

// TODO: add tests with half-placed records
// TODO: add tests with multi-ref once thats enabled

public class CRAMBAIIndexerTest extends HtsjdkTest {
    private static final int RECORDS_PER_SLICE = 3;

    @Test(expectedExceptions = SAMException.class)
    public void testRequireCoordinateSortOrder() {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.queryname);
        new CRAMBAIIndexer(new ByteArrayOutputStream(), header);
    }

    @Test
    public void testSingleReferenceContainer() throws IOException {
        final int mappedCount = 10;

        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final Container container = CRAMStructureTestHelper.createContainer(
                containerFactory,
                CRAMStructureTestHelper.createSAMRecordsMapped(10, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
                0);

        prepareContainerForIndexing(container);

        final byte[] indexBytes = executeCRAMBAIIndexer(container, ValidationStringency.SILENT);
        Assert.assertEquals(container.getSlices().size(), 1);
        final Slice mappedSlice = container.getSlices().get(0);
        Assert.assertEquals(
                mappedSlice.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO));
        final BinningBAMIndex index = openIndex(indexBytes);

        // mapped and unmapped reads are counted, no unmapped
        assertIndexMetadata(index, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO, mappedCount, 0);
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), 0);
    }

    // TODO: creating a multi-ref container (even with a single multi-ref slice) is disabled
    // since index queries don't work correctly
    @Test(expectedExceptions = SAMException.class, enabled = false)
    public void testMultiReferenceContainer() throws IOException {
        final int MAPPED_COUNT = 20;

        // the only way (for this implementation) to create a multi-ref container is to have a single
        // multi-ref slice; the only way to create a multi-ref slice is use a small number of records
        // (< MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD) split across two ref sequences
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final List<SAMRecord> samRecords = CRAMStructureTestHelper.createSAMRecordsMapped(
                MAPPED_COUNT / 2, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        samRecords.addAll(CRAMStructureTestHelper.createSAMRecordsMapped(
                MAPPED_COUNT / 2, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE));
        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, samRecords, 0);

        // validate that the container has the expected reference context
        Assert.assertEquals(
                container.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO));

        prepareContainerForIndexing(container);
        final byte[] indexBytes = executeCRAMBAIIndexer(container, ValidationStringency.SILENT);
        Assert.assertEquals(container.getSlices().size(), 1);

        final BinningBAMIndex index = openIndex(indexBytes);
        assertIndexMetadata(index, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO, MAPPED_COUNT, 0);
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), 0);
    }

    @Test
    public void testUnmappedContainer() throws IOException {
        final int UNMAPPED_COUNT = 20;

        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<SAMRecord> samRecords = CRAMStructureTestHelper.createSAMRecordsUnmapped(UNMAPPED_COUNT);
        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, samRecords, 0);

        //                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
        prepareContainerForIndexing(container);
        final byte[] indexBytes = executeCRAMBAIIndexer(container, ValidationStringency.SILENT);
        Assert.assertEquals(container.getSlices().size(), 1);

        final BinningBAMIndex index = openIndex(indexBytes);
        assertIndexMetadata(index, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO, 0, 0);
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), UNMAPPED_COUNT);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectContainerNotIndexable() {
        final int MAPPED_COUNT = 10;

        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<SAMRecord> samRecords = CRAMStructureTestHelper.createSAMRecordsMapped(
                MAPPED_COUNT, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        final Container container = CRAMStructureTestHelper.createContainer(containerFactory, samRecords, 99);
        //                 new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),
        // in order to actually index a container/slice, it needs to have been serialized, since thats what does
        // the landmark/offset calculations, so try to index a container that has not been serialized
        executeCRAMBAIIndexer(container, ValidationStringency.STRICT);
    }

    @Test
    private void testMultipleContainerStream() throws IOException {
        final int refId1 = 0;
        final int refId2 = 1;

        // for each ref, we alternate unmapped-placed with mapped

        final int expectedMapped = 1;
        final int expectedUnmappedPlaced = 2;

        try (final ByteArrayOutputStream contentStream = new ByteArrayOutputStream();
                final ByteArrayOutputStream indexStream = new ByteArrayOutputStream()) {
            final CRAMContainerStreamWriter cramContainerStreamWriter = new CRAMContainerStreamWriter(
                    contentStream,
                    CRAMStructureTestHelper.REFERENCE_SOURCE,
                    CRAMStructureTestHelper.SAM_FILE_HEADER,
                    "test",
                    new CRAMBAIIndexer(indexStream, CRAMStructureTestHelper.SAM_FILE_HEADER));
            cramContainerStreamWriter.writeHeader();

            CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE, refId1)
                    .forEach(r -> cramContainerStreamWriter.writeAlignment(r));
            CRAMStructureTestHelper.createSAMRecordsMapped(RECORDS_PER_SLICE, refId2)
                    .forEach(r -> cramContainerStreamWriter.writeAlignment(r));
            cramContainerStreamWriter.finish(true);

            final BinningBAMIndex index = openIndex(indexStream.toByteArray());

            assertIndexMetadata(index, refId1, RECORDS_PER_SLICE, 0);
            assertIndexMetadata(index, refId2, RECORDS_PER_SLICE, 0);
        }
    }

    private void prepareContainerForIndexing(final Container container) throws IOException {
        // this sets up the Container's landmarks, required for indexing
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // we just want the side effect so ignore the output
            container.write(CramVersions.DEFAULT_CRAM_VERSION, baos);
        }
    }

    private byte[] executeCRAMBAIIndexer(final Container container, final ValidationStringency validationStringency) {
        byte[] indexBytes;
        try (final ByteArrayOutputStream indexBAOS = new ByteArrayOutputStream()) {

            final CompressorCache compressorCache = new CompressorCache();
            final CRAMBAIIndexer indexer = new CRAMBAIIndexer(indexBAOS, CRAMStructureTestHelper.SAM_FILE_HEADER);
            indexer.processContainer(container, validationStringency);
            indexer.finish();
            indexBytes = indexBAOS.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return indexBytes;
    }

    private void assertIndexMetadata(
            final BinningBAMIndex index,
            final int referenceSequence,
            final int mappedReadsCount,
            final int unmappedPlacedReadsCount) {
        final BAMIndexMetaData meta = index.getMetaData(referenceSequence);
        Assert.assertEquals(meta.getAlignedRecordCount(), mappedReadsCount);
        Assert.assertEquals(meta.getUnalignedRecordCount(), unmappedPlacedReadsCount);
    }

    private BinningBAMIndex openIndex(final byte[] indexBytes) {
        try (final SeekableMemoryStream ss = new SeekableMemoryStream(indexBytes, null)) {
            return BinningBAMIndex.open(ss, IndexLoading.AUTO);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private static SAMFileHeader headerWithOneSequence(final int length) {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", length));
        return header;
    }

    private static BAIEntry mappedEntry(final int start, final int span, final long containerOffset) {
        return new BAIEntry(new ReferenceContext(0), new AlignmentSpan(start, span, 10, 2, 0), containerOffset, 100, 0);
    }

    private static BinningIndex readBai(final ByteArrayOutputStream bai) {
        final BinaryCodec codec = new BinaryCodec(new ByteArrayInputStream(bai.toByteArray()));
        codec.readBytes(new byte[4]);
        return BinningIndex.readBaiLayout(codec, codec.readInt(), BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
    }

    @Test
    public void testSliceCountsAndContainerOffsetsReachTheIndex() {
        final ByteArrayOutputStream bai = new ByteArrayOutputStream();
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(bai, headerWithOneSequence(1_000_000));
        indexer.processBAIEntry(mappedEntry(1_000, 5_000, 4_096));
        indexer.processBAIEntry(mappedEntry(40_000, 5_000, 90_000));
        indexer.finish();

        final ReferenceBins.Metadata metadata =
                readBai(bai).getReference(0).getMetadata().orElseThrow();
        Assert.assertEquals(metadata.mappedCount(), 20);
        Assert.assertEquals(metadata.unmappedCount(), 4);
        Assert.assertEquals(metadata.firstOffset(), 4_096L << 16);
        Assert.assertEquals(metadata.lastOffset(), (90_000L << 16) + 1);
    }

    @Test
    public void testSliceEndingOnAWindowBoundaryDoesNotReachIntoTheNextWindow() {
        final ByteArrayOutputStream bai = new ByteArrayOutputStream();
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(bai, headerWithOneSequence(1_000_000));
        indexer.processBAIEntry(mappedEntry(1, 16_384, 4_096)); // exactly the first 16 kb window
        indexer.finish();

        Assert.assertEquals(readBai(bai).getReference(0).getLinearIndex(), new long[] {4_096L << 16});
    }

    @Test
    public void testMappedSliceWithoutAStartIsFiledAtTheFirstBase() {
        final ByteArrayOutputStream bai = new ByteArrayOutputStream();
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(bai, headerWithOneSequence(1_000_000));
        indexer.processBAIEntry(mappedEntry(0, 0, 4_096));
        indexer.finish();

        Assert.assertEquals(readBai(bai).getReference(0).getBinNumber(0), 4681); // the first 16 kb bin
    }

    @Test
    public void testSliceBeyondTheBaiLimitFailsPointingAtCrai() {
        final CRAMBAIIndexer indexer =
                new CRAMBAIIndexer(new ByteArrayOutputStream(), headerWithOneSequence(600_000_000));
        try {
            indexer.processBAIEntry(mappedEntry(550_000_000, 5_000, 4_096));
            Assert.fail("a slice beyond 2^29 was indexed in a BAI");
        } catch (final SAMException e) {
            Assert.assertTrue(e.getMessage().contains("CRAI"), e.getMessage());
        }
    }

    @Test
    public void testSliceWhoseEndOverflowsAnIntIsRejectedNotFiledAsOneBase() {
        final CRAMBAIIndexer indexer =
                new CRAMBAIIndexer(new ByteArrayOutputStream(), headerWithOneSequence(1_000_000));
        try {
            indexer.processBAIEntry(mappedEntry(1_000, Integer.MAX_VALUE, 4_096));
            Assert.fail("a slice whose end overflows an int was indexed");
        } catch (final SAMException e) {
            Assert.assertTrue(e.getMessage().contains("CRAI"), e.getMessage());
        }
    }

    @Test
    public void testOutputIsClosedWhenTheIndexCannotBeBuilt() {
        final boolean[] closed = {false};
        final ByteArrayOutputStream bai = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        final CRAMBAIIndexer indexer = new CRAMBAIIndexer(bai, headerWithOneSequence(1_000_000));
        // A slice on a reference the header does not have, which only comes to light when the index is built.
        indexer.processBAIEntry(
                new BAIEntry(new ReferenceContext(1), new AlignmentSpan(1_000, 5_000, 10, 2, 0), 4_096, 100, 0));

        Assert.assertThrows(IllegalArgumentException.class, indexer::finish);
        Assert.assertTrue(closed[0], "the output should be closed even though nothing could be written");
    }
}
