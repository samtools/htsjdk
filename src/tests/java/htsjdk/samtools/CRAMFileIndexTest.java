package htsjdk.samtools;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;

/**
 * Created by Vadim on 14/03/2015.
 */
public class CRAMFileIndexTest {
    private final File BAM_FILE = new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    File cramFile = new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.cram");
    File indexFile = new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.cram.bai");
    private byte[] cramBytes;
    private byte[] baiBytes;
    private ReferenceSource source;

    @Test
    public void test() throws IOException {
//        File cramFile = File.createTempFile("cram", "tmp");
//        cramFile.deleteOnExit();
//        FileOutputStream fos = new FileOutputStream(cramFile);
//        fos.write(cramBytes);
//        fos.close();


//        File indexFile = File.createTempFile("crai", "tmp");
//        indexFile.deleteOnExit();
//        FileOutputStream fos = new FileOutputStream(indexFile);
//        fos.write(baiBytes);
//        fos.close();


        CRAMFileReader reader = new CRAMFileReader(cramFile, indexFile, source);
        CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1500);
        Assert.assertTrue(iterator.hasNext());
        SAMRecord record = iterator.next();
        System.out.println(record.getSAMString());
    }

    @BeforeTest
    public void prepare() throws IOException {
        Log.setGlobalLogLevel(Log.LogLevel.INFO);
        source = new ReferenceSource(new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.fa"));
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
//        CRAMFileWriter writer = new CRAMFileWriter(baos, source, samReader.getFileHeader(), BAM_FILE.getName());
//        SAMRecordIterator iterator = samReader.iterator();
//        while (iterator.hasNext()) {
//            SAMRecord samRecord = iterator.next();
//            writer.addAlignment(samRecord);
//        }
//        writer.finish();
//        writer.close();
//        cramBytes = baos.toByteArray();

        baiBytes = bai(cramFile);
        FileOutputStream fos = new FileOutputStream(indexFile);
        fos.write(baiBytes);
        fos.close();
    }

    private static byte[] bai(File cramFile) throws FileNotFoundException {
        CountingInputStream is = new CountingInputStream(new FileInputStream(cramFile));
        CramHeader cramHeader = null;
        try {
            cramHeader = CramIO.readCramHeader(is);
        } catch (IOException e) {
            Assert.fail("Failed to create bai index", e);
        }

        ByteArrayOutputStream indexBAOS = new ByteArrayOutputStream();
        CRAMIndexer indexer = new CRAMIndexer(indexBAOS, cramHeader.getSamFileHeader());

        Container container = null;
        do {
            try {
                long offset = is.getCount();
                Container c = ContainerIO.readContainer(cramHeader.getVersion(), is);
                if (c == null)
                    break;
                c.offset = offset;

                int i = 0;
                for (Slice slice : c.slices) {
                    slice.containerOffset = offset;
                    slice.index = i++;
                    indexer.processAlignment(slice);
                }

            } catch (IOException e) {
                Assert.fail("Failed to read cram container", e);
            }

        } while (container != null && !container.isEOF());

        indexer.finish();

        return indexBAOS.toByteArray();
    }
}
