package htsjdk.samtools;

import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public final class CRAMTestUtils {

    //private constructor since this is a utility class
    private CRAMTestUtils(){};

    /**
     * Write a collection of SAMRecords into an in memory Cram file and then open a reader over it
     * @param records a Collection of SAMRecords
     * @param ref a set of bases to use as the single reference contig named "chr1"
     * @return a CRAMFileReader reading from an in memory buffer that has had the records written into it
     */
    public static CRAMFileReader writeAndReadFromInMemoryCram(Collection<SAMRecord> records, byte[] ref) throws IOException {
        InMemoryReferenceSequenceFile refFile = new InMemoryReferenceSequenceFile();
        refFile.add("chr1", ref);
        ReferenceSource source = new ReferenceSource(refFile);
        final SAMFileHeader header = records.iterator().next().getHeader();
        return writeAndReadFromInMemoryCram(records, source, header);
    }

    /**
     * Write a collection of SAMRecords into an in memory Cram file and then open a reader over it
     * @param records a SAMRecordSetBuilder which has been initialized with records
     * @return a CRAMFileReader reading from an in memory buffer that has had the records written into it, uses a fake reference with all A's
     */
    public static CRAMFileReader writeAndReadFromInMemoryCram(SAMRecordSetBuilder records) throws IOException {
        return writeAndReadFromInMemoryCram(records.getRecords(), getFakeReferenceSource(), records.getHeader());
    }

    private static CRAMFileReader writeAndReadFromInMemoryCram(Collection<SAMRecord> records, CRAMReferenceSource source, SAMFileHeader header) throws IOException {
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, source, header, "whatever")){

            records.forEach(cramFileWriter::addAlignment);

            //force a flush before reading from the buffer
            cramFileWriter.close();

            return new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), (SeekableStream) null, source, ValidationStringency.SILENT);
        }
    }

    /**
     * return a CRAMReferenceSource that returns all A's for any sequence queried
     */
    public static CRAMReferenceSource getFakeReferenceSource() {
        return (sequenceRecord, tryNameVariants) -> {
            byte[] bases = new byte[sequenceRecord.getSequenceLength()];
            Arrays.fill(bases, (byte)'A');
            return bases;
        };
    }
}
