package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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
                new CRAMEncodingStrategy(), CRAMStructureTestHelper.REFERENCE_SOURCE);
        final Container container = CRAMStructureTestHelper.createContainer(
                containerFactory,
                CRAMStructureTestHelper.createSAMRecordsMapped(10, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO),0);

        prepareContainerForIndexing(container);

        final byte[] indexBytes = executeCRAMBAIIndexer(container, ValidationStringency.SILENT);
        Assert.assertEquals(container.getSlices().size(), 1);
        final Slice mappedSlice = container.getSlices().get(0);
        Assert.assertEquals(
                mappedSlice.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO));
        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexBytes);

        // mapped and unmapped reads are counted, no unmapped
        assertIndexMetadata(index, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO, mappedCount, 0);
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), 0);
    }

    //TODO: creating a multi-ref container (even with a single multi-ref slice) is disabled
    // since index queries don't work correctly
    @Test(expectedExceptions = SAMException.class,
            enabled = false)
    public void testMultiReferenceContainer() throws IOException {
        final int MAPPED_COUNT = 20;

        // the only way (for this implementation) to create a multi-ref container is to have a single
        // multi-ref slice; the only way to create a multi-ref slice is use a small number of records
        // (< MINIMUM_SINGLE_REFERENCE_SLICE_THRESHOLD) split across two ref sequences
        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(),
                CRAMStructureTestHelper.REFERENCE_SOURCE);

        final List<SAMRecord> samRecords =
                CRAMStructureTestHelper.createSAMRecordsMapped(
                        MAPPED_COUNT / 2,
                        CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        samRecords.addAll(CRAMStructureTestHelper.createSAMRecordsMapped(
                MAPPED_COUNT / 2,
                CRAMStructureTestHelper.REFERENCE_SEQUENCE_ONE));
        final Container container = CRAMStructureTestHelper.createContainer(
                containerFactory,
                samRecords,0);

        //validate that the container has the expected reference context
        Assert.assertEquals(
                container.getAlignmentContext().getReferenceContext(),
                new ReferenceContext(CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO));

        prepareContainerForIndexing(container);
        final byte[] indexBytes = executeCRAMBAIIndexer(container, ValidationStringency.SILENT);
        Assert.assertEquals(container.getSlices().size(), 1);

        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexBytes);
        assertIndexMetadata(index, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO, MAPPED_COUNT, 0);
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), 0);
    }

    @Test
    public void testUnmappedContainer() throws IOException {
        final int UNMAPPED_COUNT = 20;

        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(), CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<SAMRecord> samRecords =
                CRAMStructureTestHelper.createSAMRecordsUnmapped(UNMAPPED_COUNT);
        final Container container = CRAMStructureTestHelper.createContainer(
                containerFactory,
                samRecords,0);

//                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
        prepareContainerForIndexing(container);
        final byte[] indexBytes = executeCRAMBAIIndexer(container, ValidationStringency.SILENT);
        Assert.assertEquals(container.getSlices().size(), 1);

        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexBytes);
        assertIndexMetadata(index, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO, 0, 0);
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), UNMAPPED_COUNT);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectContainerNotIndexable() {
        final int MAPPED_COUNT = 10;

        final ContainerFactory containerFactory = new ContainerFactory(
                CRAMStructureTestHelper.SAM_FILE_HEADER,
                new CRAMEncodingStrategy(), CRAMStructureTestHelper.REFERENCE_SOURCE);
        final List<SAMRecord> samRecords =
                CRAMStructureTestHelper.createSAMRecordsMapped(MAPPED_COUNT, CRAMStructureTestHelper.REFERENCE_SEQUENCE_ZERO);
        final Container container = CRAMStructureTestHelper.createContainer(
                containerFactory,
                samRecords, 99);
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

            final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexStream.toByteArray());

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
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return indexBytes;
    }

    private void assertIndexMetadata(final AbstractBAMFileIndex index,
                                     final int referenceSequence,
                                     final int mappedReadsCount,
                                     final int unmappedPlacedReadsCount) {
        final BAMIndexMetaData meta = index.getMetaData(referenceSequence);
        Assert.assertEquals(meta.getAlignedRecordCount(), mappedReadsCount);
        Assert.assertEquals(meta.getUnalignedRecordCount(), unmappedPlacedReadsCount);
    }

    private AbstractBAMFileIndex getAbstractBAMFileIndex(final byte[] indexBytes) {
        try (final SeekableMemoryStream ss = new SeekableMemoryStream(indexBytes, null)) {
            return new CachingBAMFileIndex(ss, CRAMStructureTestHelper.SAM_FILE_HEADER.getSequenceDictionary());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
