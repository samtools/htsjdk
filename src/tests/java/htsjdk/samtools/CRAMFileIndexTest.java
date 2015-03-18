package htsjdk.samtools;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.cram.structure.Slice;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;
import java.util.Arrays;

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

    //    @Test
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

    @Test
    public void scan() throws IOException {
        SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
        SAMRecordIterator samRecordIterator = samReader.iterator();
        int counter = 0;
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes, null), new ByteArraySeekableStream(baiBytes, null), source);
        long ms = System.currentTimeMillis();
        while (samRecordIterator.hasNext()) {
            SAMRecord samRecord = samRecordIterator.next();
            if (samRecord.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) break;
            String s1 = samRecord.getSAMString();

            CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart(samRecord.getReferenceName(), samRecord.getAlignmentStart());
            Assert.assertTrue(iterator.hasNext(), counter + ": " + s1);
            SAMRecord cramRecord = iterator.next();

            String s2 = cramRecord.getSAMString();

            Assert.assertEquals(samRecord.getReferenceName(), cramRecord.getReferenceName(), s1 + "\n" + s2);
            Assert.assertEquals(samRecord.getAlignmentStart(), cramRecord.getAlignmentStart(), s1 + "\n" + s2);

            counter++;
            if ((System.currentTimeMillis() - ms) > 10000) {
                System.out.println(counter);
                ms = System.currentTimeMillis();
            }
        }
        samRecordIterator.close();

        // test unmapped:
         SAMRecordIterator unmappedSamIterator = samReader.queryUnmapped();
         CloseableIterator<SAMRecord> unmappedCramIterator = reader.queryUnmapped();
        while (unmappedSamIterator.hasNext()) {
            Assert.assertTrue(unmappedCramIterator.hasNext());
            SAMRecord r1 = unmappedSamIterator.next() ;
            SAMRecord r2 = unmappedCramIterator.next() ;
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString()) ;
        }
        Assert.assertFalse(unmappedCramIterator.hasNext());

        reader.close();
    }

    private static class ByteArraySeekableStream extends SeekableStream {
        private byte[] bytes;
        private String source;
        private long position=0;

        public ByteArraySeekableStream(byte[] bytes, String source) {
            this.bytes = bytes;
            this.source = source;
        }

        @Override
        public long length() {
            return bytes.length;
        }

        @Override
        public long position() throws IOException {
            return position;
        }

        @Override
        public void seek(long position) throws IOException {
            this.position = position;
        }

        @Override
        public int read() throws IOException {
            if (position < bytes.length)
                return 0xFF & bytes[((int) position++)];
            else return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            if (position >= bytes.length) {
                return -1;
            }
            if (position + len > bytes.length) {
                len = (int) (bytes.length - position);
            }
            if (len <= 0) {
                return 0;
            }
            System.arraycopy(bytes, (int) position, b, off, len);
            position += len;
            return len;
        }

        @Override
        public void close() throws IOException {
            bytes = null;
        }

        @Override
        public boolean eof() throws IOException {
            return position >= bytes.length;
        }

        @Override
        public String getSource() {
            return source;
        }
    }

    @BeforeTest
    public void prepare() throws IOException {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
        source = new ReferenceSource(new FakeReferenceSequenceFile(SamReaderFactory.makeDefault().getFileHeader(BAM_FILE).getSequenceDictionary().getSequences()));
        FileInputStream fis = new FileInputStream(cramFile);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtil.copyStream(fis, baos);
        cramBytes = baos.toByteArray();

        ByteArraySeekableStream bass = new ByteArraySeekableStream(cramBytes, null) ;
        for (int i=0; i<cramBytes.length; i++) {
            int read = bass.read() ;
            if ((0xFF & cramBytes[i]) != read) throw new RuntimeException(String.format("%d: %d x %d\n", i, cramBytes[i], read)) ;
        }
        if (bass.read() != -1) throw new RuntimeException("Incomplete");

        bass = new ByteArraySeekableStream(cramBytes, null) ;
        ByteArrayInputStream bais = new ByteArrayInputStream(cramBytes) ;
        int len = 38;
        byte[] buf1 = new byte[len] ;
        byte[] buf2 = new byte[len] ;
        for (int i=0; i<cramBytes.length; i++) {
            int read1 = bais.read(buf1, 0, buf1.length) ;
            int read2 = bass.read(buf2, 0, buf2.length) ;

            if (read1 != read2) throw new RuntimeException(String.format("%d: %d x %d\n", i, read1, read2)) ;
            if (!Arrays.equals(buf1, buf2)) throw new RuntimeException(String.format("%d: %s x %s\n", i, Arrays.toString(buf1), Arrays.toString(buf2))) ;
        }

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
                container = ContainerIO.readContainer(cramHeader.getVersion(), is);
                if (container == null || container.isEOF())
                    break;
                container.offset = offset;

                int i = 0;
                for (Slice slice : container.slices) {
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
