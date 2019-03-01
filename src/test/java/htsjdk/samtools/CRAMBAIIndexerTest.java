package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.ContainerFactory;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.CRAMStructureTestUtil;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 12/01/2016.
 */
public class CRAMBAIIndexerTest extends HtsjdkTest {
    @Test
    public void test_processMultiContainer() throws IOException {
        // 1 record with ref id 0
        // 3 records with ref id 1
        // 2 records with ref id 2

        final int expected0 = 1;
        final int expected1 = 3;
        final int expected2 = 2;

        final List<CramCompressionRecord> records1 = new ArrayList<>();
        records1.add(CRAMStructureTestUtil.createMappedRecord(0, 0, 1));
        records1.add(CRAMStructureTestUtil.createMappedRecord(1, 1, 2));
        records1.add(CRAMStructureTestUtil.createMappedRecord(2, 1, 3));

        final List<CramCompressionRecord> records2 = new ArrayList<>();
        records2.add(CRAMStructureTestUtil.createMappedRecord(3, 1, 3));
        records2.add(CRAMStructureTestUtil.createMappedRecord(4, 2, 3));
        records2.add(CRAMStructureTestUtil.createMappedRecord(5, 2, 4));

        final SAMFileHeader samFileHeader = CRAMStructureTestUtil.getSAMFileHeaderForTests();

        final int recordsPerContainer = 3;
        final ContainerFactory containerFactory = new ContainerFactory(samFileHeader, recordsPerContainer);

        final Container container1 = containerFactory.buildContainer(records1);
        Assert.assertTrue(container1.getReferenceContext().isMultiRef());

        final Container container2 = containerFactory.buildContainer(records2);
        Assert.assertTrue(container2.getReferenceContext().isMultiRef());

        byte[] indexBytes;
        try (final ByteArrayOutputStream indexBAOS = new ByteArrayOutputStream()) {
            final CRAMBAIIndexer indexer = new CRAMBAIIndexer(indexBAOS, samFileHeader);
            indexer.processContainer(container1, ValidationStringency.STRICT);
            indexer.processContainer(container2, ValidationStringency.STRICT);
            indexer.finish();
            indexBytes = indexBAOS.toByteArray();
        }

        final BAMIndex index = new CachingBAMFileIndex(new SeekableMemoryStream(indexBytes, null), samFileHeader.getSequenceDictionary());

        Assert.assertEquals(index.getMetaData(0).getAlignedRecordCount(), expected0);
        Assert.assertEquals(index.getMetaData(1).getAlignedRecordCount(), expected1);
        Assert.assertEquals(index.getMetaData(2).getAlignedRecordCount(), expected2);
    }

}
