package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Iterator;
import java.util.List;

/**
 * Companion to CRAMBAIIndexerTest, for testing CRAI indices created on cram
 * streams;
 */
public class CRAMCRAIIndexerTest extends HtsjdkTest {

    @Test
    public void testCRAIIndexerFromContainer() throws IOException {
        testCRAIIndexer(this::fromContainer);
    }

    @Test
    public void testCRAIIndexerFromStream() throws IOException {
        testCRAIIndexer(this::fromStream);
    }

    private void testCRAIIndexer(Index index) throws IOException {
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/test2.cram");

        try (final InputStream indexStream = new ByteArrayInputStream(index.getIndex(CRAMFile))) {
            final List<CRAIEntry> craiEntries = CRAMCRAIIndexer.readIndex(indexStream).getCRAIEntries();
            Assert.assertEquals(craiEntries.size(), 1);
        }
    }

    @Test
    public void testMultiRefContainer() throws IOException {
        final SAMFileHeader samFileHeader = new SAMFileHeader();
        samFileHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);

        samFileHeader.addSequence(new SAMSequenceRecord("1", 10));
        samFileHeader.addSequence(new SAMSequenceRecord("2", 10));
        samFileHeader.addSequence(new SAMSequenceRecord("3", 10));

        final ReferenceSource source = new ReferenceSource(new FakeReferenceSequenceFile(samFileHeader.getSequenceDictionary().getSequences()));

        // force the containers to be small to ensure there are 2
        int originalDefaultSize = CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE;
        CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE = 3;

        byte[] cramBytes;
        byte[] indexBytes;

        try (final ByteArrayOutputStream cramBAOS = new ByteArrayOutputStream();
             final ByteArrayOutputStream indexBAOS = new ByteArrayOutputStream()) {

            final CRAMContainerStreamWriter containerWriter = new CRAMContainerStreamWriter(cramBAOS, indexBAOS, source, samFileHeader, "test");
            containerWriter.writeHeader(samFileHeader);

            containerWriter.writeAlignment(createSAMRecord(samFileHeader, 0, 0, 1));
            containerWriter.writeAlignment(createSAMRecord(samFileHeader, 1, 1, 2));
            containerWriter.writeAlignment(createSAMRecord(samFileHeader, 2, 1, 3));

            containerWriter.writeAlignment(createSAMRecord(samFileHeader, 3, 1, 3));
            containerWriter.writeAlignment(createSAMRecord(samFileHeader, 4, 2, 3));
            containerWriter.writeAlignment(createSAMRecord(samFileHeader, 5, 2, 4));

            containerWriter.finish(true);

            cramBytes = cramBAOS.toByteArray();
            indexBytes = indexBAOS.toByteArray();
        } finally {
            // failing to reset this can cause unrelated tests to fail if this test fails
            CRAMContainerStreamWriter.DEFAULT_RECORDS_PER_SLICE = originalDefaultSize;
        }

        // These tests all fail due to https://github.com/samtools/htsjdk/issues/531
        // (metadata is incorrect after .crai->.bai conversion)
        //SeekableStream baiStream = CRAIIndex.openCraiFileAsBaiStream(
        //        new ByteArrayInputStream(indexBAOS.toByteArray()), samFileHeader.getSequenceDictionary());
        //BAMIndex index = new CachingBAMFileIndex(baiStream, samFileHeader.getSequenceDictionary());
        //final BAMIndexMetaData metaData_0 = index.getMetaData(0);
        //Assert.assertNotNull(metaData_0);
        //Assert.assertEquals(metaData_0.getAlignedRecordCount(), 1);
        //final BAMIndexMetaData metaData_1 = index.getMetaData(1);
        //Assert.assertNotNull(metaData_1);
        //Assert.assertEquals(metaData_1.getAlignedRecordCount(), 3);
        //final BAMIndexMetaData metaData_2 = index.getMetaData(2);
        //Assert.assertNotNull(metaData_2);
        //Assert.assertEquals(metaData_2.getAlignedRecordCount(), 2);

        try (final InputStream cramStream = new ByteArraySeekableStream(cramBytes);
             final SeekableStream indexStream = new ByteArraySeekableStream(indexBytes)) {

            // NOTE: this test uses the default index format created by CRAMContainerStreamWriter
            // which is currently .bai;

            CRAMFileReader cramReader = new CRAMFileReader(
                    cramStream,
                    indexStream,
                    source,
                    ValidationStringency.DEFAULT_STRINGENCY
            );
            Assert.assertTrue(cramReader.hasIndex());

            Iterator<SAMRecord> it = cramReader.query(new QueryInterval[]{new QueryInterval(0, 0, 5)}, false);
            long count = getIteratorCount(it);
            Assert.assertEquals(count, 1);

            it = cramReader.query(new QueryInterval[]{new QueryInterval(1, 0, 5)}, false);
            count = getIteratorCount(it);
            Assert.assertEquals(count, 3);

            it = cramReader.query(new QueryInterval[]{new QueryInterval(2, 0, 5)}, false);
            count = getIteratorCount(it);
            Assert.assertEquals(count, 2);
        }
    }

    @Test(expectedExceptions = SAMException.class)
    public void testRequireCoordinateSortOrder() {
        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.queryname);

        new CRAMCRAIIndexer(new ByteArrayOutputStream(), header);
    }

    private static SAMRecord createSAMRecord(SAMFileHeader header, int recordIndex, int seqId, int start) {
        byte[] bases = "AAAAA".getBytes();

        final SAMRecord record = new SAMRecord(header);
        record.setReferenceIndex(seqId);
        record.setAlignmentStart(start);
        record.setReadBases(bases);
        record.setBaseQualities(bases);
        record.setReadName(Integer.toString(recordIndex));

        return record;
    }

    private interface Index {
        byte[] getIndex(final File CRAMFile) throws IOException;
    }

    private byte[] fromContainer(final File CRAMFile) throws IOException {
        try (final FileInputStream fis = new FileInputStream(CRAMFile);
             final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            final CRAMCRAIIndexer craiIndexer = new CRAMCRAIIndexer(bos, getSamFileHeader(CRAMFile));
            final CramContainerIterator cit = new CramContainerIterator(fis);
            while (cit.hasNext()) {
                craiIndexer.processContainer(cit.next());
            }
            craiIndexer.finish();
            bos.close();
            return bos.toByteArray();
        }
    }

    private byte[] fromStream(final File CRAMFile) throws IOException {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
             final SeekableStream sfs = new SeekableFileStream(CRAMFile)) {

            CRAMCRAIIndexer.writeIndex(sfs, bos);
            return bos.toByteArray();
        }
    }

    private SAMFileHeader getSamFileHeader(final File CRAMFile) throws IOException {
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/auxf.fa");
        final File indexFile = null;
        final ReferenceSource refSource = new ReferenceSource(refFile);
        final CRAMFileReader reader = new CRAMFileReader(
                CRAMFile,
                indexFile,
                refSource,
                ValidationStringency.STRICT);
        final SAMFileHeader samHeader = reader.getFileHeader();
        reader.close();
        return samHeader;
    }

    private long getIteratorCount(Iterator<SAMRecord> it) {
        long count = 0;
        while (it.hasNext()) {
            count++;
            it.next();
        }
        return count;
    }

}
