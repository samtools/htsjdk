package htsjdk.samtools;

import htsjdk.samtools.cram.build.CramContainerHeaderIterator;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.SeekableStream;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class CRAMTestUtils {

    // private constructor since this is a utility class
    private CRAMTestUtils() {}
    ;

    // write the contents of an input file to the provided CRAM file using the provided encoding params,
    // returns the size of the generated file
    public static long writeToCRAMWithEncodingStrategy(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final Path inputFile,
            final Path tempOutputCRAM,
            final Path referenceFile)
            throws IOException {
        return writeToCRAMWithEncodingStrategy(
                cramEncodingStrategy, inputFile, tempOutputCRAM, new ReferenceSource(referenceFile));
    }

    // write the contents of an input file to the provided CRAM file using the provided encoding params and
    // reference
    // returns the size of the generated file
    public static long writeToCRAMWithEncodingStrategy(
            final CRAMEncodingStrategy cramEncodingStrategy,
            final Path inputFile,
            final Path tempOutputCRAM,
            final ReferenceSource referenceSource)
            throws IOException {
        try (final SamReader reader = SamReaderFactory.makeDefault()
                        .referenceSource(referenceSource)
                        .validationStringency((ValidationStringency.SILENT))
                        .open(inputFile);
                final OutputStream fos = Files.newOutputStream(tempOutputCRAM)) {
            final CRAMFileWriter cramWriter = new CRAMFileWriter(
                    cramEncodingStrategy,
                    fos,
                    null,
                    true,
                    referenceSource,
                    reader.getFileHeader(),
                    tempOutputCRAM.getFileName().toString());
            final SAMRecordIterator inputIterator = reader.iterator();
            while (inputIterator.hasNext()) {
                cramWriter.addAlignment(inputIterator.next());
            }
            cramWriter.close();
        }
        return Files.size(tempOutputCRAM);
    }

    /**
     * Write a collection of SAMRecords into an in memory Cram file and then open a reader over it
     * @param records a Collection of SAMRecords
     * @param ref a set of bases to use as the single reference contig named "chr1"
     * @return a CRAMFileReader reading from an in memory buffer that has had the records written into it
     */
    public static CRAMFileReader writeAndReadFromInMemoryCram(Collection<SAMRecord> records, byte[] ref)
            throws IOException {
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

    private static CRAMFileReader writeAndReadFromInMemoryCram(
            Collection<SAMRecord> records, CRAMReferenceSource source, SAMFileHeader header) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, source, header, "whatever")) {

            records.forEach(cramFileWriter::addAlignment);

            // force a flush before reading from the buffer
            cramFileWriter.close();

            return new CRAMFileReader(
                    new ByteArrayInputStream(baos.toByteArray()),
                    (SeekableStream) null,
                    source,
                    ValidationStringency.SILENT);
        }
    }

    /** The number of reads on each of the two contigs in {@link #cramWithAnInternalEofContainer()}. */
    public static final int READS_PER_CONTIG_AROUND_THE_INTERNAL_EOF = 20;

    /**
     * A coordinate-sorted CRAM in memory with {@link #READS_PER_CONTIG_AROUND_THE_INTERNAL_EOF} reads on each of
     * contigs 0 and 1, one container per contig, and an EOF container between the two, as samtools cat before
     * 1.13 wrote when concatenating CRAMs. Written against {@link #getFakeReferenceSource()}.
     */
    public static byte[] cramWithAnInternalEofContainer() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder();
        for (int contig = 0; contig < 2; contig++) {
            for (int i = 0; i < READS_PER_CONTIG_AROUND_THE_INTERNAL_EOF; i++) {
                records.addFrag("read" + contig + "_" + i, contig, 100 + i * 10, false);
            }
        }
        final CRAMEncodingStrategy oneContigPerContainer =
                new CRAMEncodingStrategy().setMinimumSingleReferenceSliceSize(1).setSlicesPerContainer(1);
        final ByteArrayOutputStream cram = new ByteArrayOutputStream();
        try (CRAMFileWriter writer = new CRAMFileWriter(
                oneContigPerContainer, cram, null, true, getFakeReferenceSource(), records.getHeader(), "eof")) {
            records.forEach(writer::addAlignment);
        }
        final byte[] bytes = cram.toByteArray();

        final List<Long> containerOffsets = new ArrayList<>();
        final CRAMVersion cramVersion;
        try (CramContainerHeaderIterator containers =
                new CramContainerHeaderIterator(new ByteArrayInputStream(bytes))) {
            cramVersion = containers.getCramHeader().getCRAMVersion();
            containers.forEachRemaining(container -> containerOffsets.add(container.getContainerByteOffset()));
        }
        if (containerOffsets.size() != 2) {
            throw new IllegalStateException("expected one container per contig, got " + containerOffsets.size());
        }
        final int secondContainer = Math.toIntExact(containerOffsets.get(1));
        final ByteArrayOutputStream spliced = new ByteArrayOutputStream();
        spliced.write(bytes, 0, secondContainer);
        CramIO.writeCramEOF(cramVersion, spliced);
        spliced.write(bytes, secondContainer, bytes.length - secondContainer);
        return spliced.toByteArray();
    }

    /**
     * return a CRAMReferenceSource that returns all A's for any sequence queried
     */
    public static CRAMReferenceSource getFakeReferenceSource() {
        return new CRAMReferenceSource() {

            @Override
            public byte[] getReferenceBases(final SAMSequenceRecord sequenceRecord, final boolean tryNameVariants) {
                byte[] bases = new byte[sequenceRecord.getSequenceLength()];
                Arrays.fill(bases, (byte) 'A');
                return bases;
            }

            @Override
            public byte[] getReferenceBasesByRegion(
                    final SAMSequenceRecord sequenceRecord, final int zeroBasedStart, final int requestedRegionLength) {
                byte[] bases = new byte[requestedRegionLength - zeroBasedStart];
                Arrays.fill(bases, (byte) 'A');
                return bases;
            }
        };
    }
}
