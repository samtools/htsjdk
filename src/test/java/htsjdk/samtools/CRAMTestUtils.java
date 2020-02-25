package htsjdk.samtools;

import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;

public final class CRAMTestUtils {

    //private constructor since this is a utility class
    private CRAMTestUtils(){};

    // write the contents of an input file to the provided CRAM file using the provided encoding params,
    // returns the size of the generated file
    public static long writeToCRAMWithEncodingStrategy(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final File inputFile,
            final File tempOutputCRAM,
            final File referenceFile) throws IOException {
        return writeToCRAMWithEncodingStrategy(cramEncodingStrategy, inputFile, tempOutputCRAM, new ReferenceSource(referenceFile));
    }

    // write the contents of an input file to the provided CRAM file using the provided encoding params and
    // reference
    // returns the size of the generated file
    public static long writeToCRAMWithEncodingStrategy(
        final CRAMEncodingStrategy cramEncodingStrategy,
        final File inputFile,
        final File tempOutputCRAM,
        final ReferenceSource referenceSource) throws IOException {
        try (final SamReader reader = SamReaderFactory.makeDefault()
                .referenceSource(referenceSource)
                .validationStringency((ValidationStringency.SILENT))
                .open(inputFile);
             final FileOutputStream fos = new FileOutputStream(tempOutputCRAM)) {
            final CRAMFileWriter cramWriter = new CRAMFileWriter(
                    cramEncodingStrategy,
                    fos,
                    null,
                    true,
                    referenceSource,
                    reader.getFileHeader(),
                    tempOutputCRAM.getName());
            final SAMRecordIterator inputIterator = reader.iterator();
            while (inputIterator.hasNext()) {
                cramWriter.addAlignment(inputIterator.next());
            }
            cramWriter.close();
        }
        return Files.size(tempOutputCRAM.toPath());
    }

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
