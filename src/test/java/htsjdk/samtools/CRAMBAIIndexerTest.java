package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CRAMStructureTestUtil;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by vadim on 12/01/2016.
 */
public class CRAMBAIIndexerTest extends HtsjdkTest {
    private static final int RECORDS_PER_SLICE = 3;
    private static final SAMFileHeader SAM_FILE_HEADER = CRAMStructureTestUtil.getSAMFileHeaderForTests();
    private static final ContainerFactory FACTORY = new ContainerFactory(SAM_FILE_HEADER, RECORDS_PER_SLICE);

    @Test
    public void test_processSingleRefMappedSlice() {
        final int refId = 0;
        final Slice mapped = new Slice(new ReferenceContext(refId));
        mapped.mappedReadsCount = 5;
        mapped.unmappedReadsCount = 10;

        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexSingleRefSlice(mapped));

        // mapped and unmapped reads are counted
        assertIndexMetadata(index, refId, mapped.mappedReadsCount, mapped.unmappedReadsCount);

        // none are unplaced
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), 0);
    }

    @Test
    public void test_processSingleRefUnmappedSlice() {
        final Slice unmapped = new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);
        unmapped.unplacedReadsCount = 20;

        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexSingleRefSlice(unmapped));
        Assert.assertEquals(index.getNoCoordinateCount().longValue(), unmapped.unplacedReadsCount);
    }

    @Test(expectedExceptions = SAMException.class)
    public void test_processMultiSlice() {
        final Slice multi = new Slice(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT);
        indexSingleRefSlice(multi);
    }

    @Test
    public void test_processSingleRefContainers() {
        singleRefContainers(this::indexContainers);
    }

    @Test
    public void test_processSingleRefContainersAsSlices() {
        singleRefContainers(this::indexContainersAsSingleRefSlices);
    }

    private void singleRefContainers(final IndexContainers indexMethod) {
        final int refId1 = 0;
        final int refId2 = 1;

        // for each ref, we alternate unmapped-placed with mapped

        final int expectedMapped = 1;
        final int expectedUnmappedPlaced = 2;

        final long dummyByteOffset = 0;
        final Container container1 = FACTORY.buildContainer(CRAMStructureTestUtil.getSingleRefRecords(RECORDS_PER_SLICE, refId1), dummyByteOffset);
        final Container container2 = FACTORY.buildContainer(CRAMStructureTestUtil.getSingleRefRecords(RECORDS_PER_SLICE, refId2), dummyByteOffset);

        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexMethod.index(container1, container2));

        assertIndexMetadata(index, refId1, expectedMapped, expectedUnmappedPlaced);
        assertIndexMetadata(index, refId2, expectedMapped, expectedUnmappedPlaced);
    }

    @Test
    public void test_processMultiRefContainers() {
        multiRefContainers(this::indexContainers);
    }

    // they're not single-ref slices
    @Test(expectedExceptions = SAMException.class)
    public void test_processMultiRefContainersAsSlices() {
        multiRefContainers(this::indexContainersAsSingleRefSlices);
    }

    public void multiRefContainers(final IndexContainers indexMethod) {
        // we alternate unmapped-placed with mapped, with the last one unplaced

        final int expectedMapped0 = 0;
        final int expectedUnmapped0 = 1;

        final int expectedMapped1 = 1;
        final int expectedUnmapped1 = 0;

        final int expectedMapped2 = 0;
        final int expectedUnmapped2 = 1;

        final int expectedMapped3 = 1;
        final int expectedUnmapped3 = 0;

        final int expectedMapped4 = 0;
        final int expectedUnmapped4 = 1;

        final int expectedUnplaced = 1;

        final List<CramCompressionRecord> records = CRAMStructureTestUtil.getMultiRefRecordsWithOneUnmapped(RECORDS_PER_SLICE * 2);

        final long dummyByteOffset = 0;
        final Container container1 = FACTORY.buildContainer(records.subList(0, RECORDS_PER_SLICE), dummyByteOffset);
        Assert.assertTrue(container1.getReferenceContext().isMultiRef());

        final Container container2 = FACTORY.buildContainer(records.subList(RECORDS_PER_SLICE, RECORDS_PER_SLICE * 2), dummyByteOffset);
        Assert.assertTrue(container2.getReferenceContext().isMultiRef());

        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexMethod.index(container1, container2));

        assertIndexMetadata(index, 0, expectedMapped0, expectedUnmapped0);
        assertIndexMetadata(index, 1, expectedMapped1, expectedUnmapped1);
        assertIndexMetadata(index, 2, expectedMapped2, expectedUnmapped2);
        assertIndexMetadata(index, 3, expectedMapped3, expectedUnmapped3);
        assertIndexMetadata(index, 4, expectedMapped4, expectedUnmapped4);

        Assert.assertEquals(index.getNoCoordinateCount().longValue(), expectedUnplaced);
    }

    @Test
    public void test_processUnplacedContainers() {
        unplacedContainers(this::indexContainers);
    }

    @Test
    public void test_processUnplacedContainersAsSlices() {
        unplacedContainers(this::indexContainersAsSingleRefSlices);
    }

    public void unplacedContainers(final IndexContainers indexMethod) {
        final long dummyByteOffset = 0;
        final Container unplacedContainer = FACTORY.buildContainer(CRAMStructureTestUtil.getUnplacedRecords(RECORDS_PER_SLICE), dummyByteOffset);
        Assert.assertTrue(unplacedContainer.getReferenceContext().isUnmappedUnplaced());

        // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
        // but not both.  We normally treat these weird edge cases as unplaced, but for BAM indexing we only check start position
        // in order to match BAMIndexMetadata.recordMetaData(SAMRecord)

        // these will be considered unplaced by CRAMBAIIndexer

        final Container halfUnplacedNoStartContainer = FACTORY.buildContainer(
                CRAMStructureTestUtil.getHalfUnplacedNoStartRecords(RECORDS_PER_SLICE, 0),
                dummyByteOffset);
        Assert.assertTrue(halfUnplacedNoStartContainer.getReferenceContext().isUnmappedUnplaced());

        // these will NOT be considered unplaced by CRAMBAIIndexer

        final Container halfUnplacedNoRefContainer = FACTORY.buildContainer(
                CRAMStructureTestUtil.getHalfUnplacedNoRefRecords(RECORDS_PER_SLICE),
                dummyByteOffset);
        Assert.assertTrue(halfUnplacedNoRefContainer.getReferenceContext().isUnmappedUnplaced());

        final AbstractBAMFileIndex index = getAbstractBAMFileIndex(indexMethod.index(
                unplacedContainer, halfUnplacedNoStartContainer, halfUnplacedNoRefContainer));

        // unplacedContainer and halfUnplacedNoStartContainer
        final int expectedRecords = RECORDS_PER_SLICE * 2;

        Assert.assertEquals(index.getNoCoordinateCount().longValue(), expectedRecords);
    }

    @Test(expectedExceptions = SAMException.class)
    public void testRequireCoordinateSortOrder() {
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.queryname);
        new CRAMBAIIndexer(new ByteArrayOutputStream(), header);
    }

    private interface IndexContainers {
        byte[] index(final Container... containers);
    }

    private byte[] indexSingleRefSlice(final Slice slice) {
        byte[] indexBytes;
        try (final ByteArrayOutputStream indexBAOS = new ByteArrayOutputStream()) {

            final CRAMBAIIndexer indexer = new CRAMBAIIndexer(indexBAOS, SAM_FILE_HEADER);
            indexer.processAsSingleReferenceSlice(slice);
            indexer.finish();

            indexBytes = indexBAOS.toByteArray();
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return indexBytes;
    }

    private byte[] indexContainers(final Container... containers) {
        byte[] indexBytes;
        try (final ByteArrayOutputStream indexBAOS = new ByteArrayOutputStream()) {

            final CRAMBAIIndexer indexer = new CRAMBAIIndexer(indexBAOS, SAM_FILE_HEADER);
            for (final Container container : containers) {
                indexer.processContainer(container, ValidationStringency.STRICT);
            }
            indexer.finish();

            indexBytes = indexBAOS.toByteArray();
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return indexBytes;
    }

    private byte[] indexContainersAsSingleRefSlices(final Container... containers) {
        byte[] indexBytes;
        try (final ByteArrayOutputStream indexBAOS = new ByteArrayOutputStream()) {

            final CRAMBAIIndexer indexer = new CRAMBAIIndexer(indexBAOS, SAM_FILE_HEADER);
            for (final Container container : containers) {
                indexer.processAsSingleReferenceSlice(container.getSlices()[0]);
            }
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
            return new CachingBAMFileIndex(ss, SAM_FILE_HEADER.getSequenceDictionary());
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
