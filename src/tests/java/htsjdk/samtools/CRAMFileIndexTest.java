package htsjdk.samtools;

import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.FakeReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

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
    public void test_chrmM_1500() throws IOException {
        CRAMFileReader reader = new CRAMFileReader(cramFile, indexFile, source);
        reader.setValidationStringency(ValidationStringency.SILENT);
        CloseableIterator<SAMRecord> iterator = reader.queryAlignmentStart("chrM", 1500);
        Assert.assertTrue(iterator.hasNext());
        SAMRecord record = iterator.next();

        Assert.assertEquals(record.getReferenceName(), "chrM");
        Assert.assertTrue(record.getAlignmentStart() >= 1500);
    }

    @Test
    public void scanAllMappedReads() throws IOException {
        SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
        SAMRecordIterator samRecordIterator = samReader.iterator();
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes, null), new ByteArraySeekableStream(baiBytes, null), source, ValidationStringency.SILENT);
        reader.setValidationStringency(ValidationStringency.SILENT);

        int counter = 0;
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
        reader.close();

        Assert.assertEquals(counter, 9721);
    }

    @Test
    public void scanAllUnmappedReads() throws IOException {
        SamReader samReader = SamReaderFactory.makeDefault().open(BAM_FILE);
        CRAMFileReader reader = new CRAMFileReader(new ByteArraySeekableStream(cramBytes, null), new ByteArraySeekableStream(baiBytes, null), source, ValidationStringency.SILENT);
        reader.setValidationStringency(ValidationStringency.SILENT);
        long ms = System.currentTimeMillis();
        int counter = 0;

        SAMRecordIterator unmappedSamIterator = samReader.queryUnmapped();
        CloseableIterator<SAMRecord> unmappedCramIterator = reader.queryUnmapped();
        while (unmappedSamIterator.hasNext()) {
            Assert.assertTrue(unmappedCramIterator.hasNext());
            SAMRecord r1 = unmappedSamIterator.next();
            SAMRecord r2 = unmappedCramIterator.next();
            Assert.assertEquals(r1.getReadName(), r2.getReadName());
            Assert.assertEquals(r1.getBaseQualityString(), r2.getBaseQualityString());

            counter++;
            if ((System.currentTimeMillis() - ms) > 10000) {
                System.out.println(counter);
                ms = System.currentTimeMillis();
            }
        }
        Assert.assertFalse(unmappedCramIterator.hasNext());
        Assert.assertEquals(counter, 279);

        reader.close();
    }

    private static class ByteArraySeekableStream extends SeekableStream {
        private byte[] bytes;
        private String source;
        private long position = 0;

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
        cramBytes = readFile(cramFile);

        CRAMIndexer.createIndex(new SeekableFileStream(cramFile), indexFile, null);
        baiBytes = readFile(indexFile);
    }

    private static byte[] readFile(File file) throws FileNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtil.copyStream(fis, baos);
        return baos.toByteArray();
    }
}
