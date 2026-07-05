package htsjdk.samtools;

import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.CloseableIterator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;

public class CRAMIndexTestHelper {

    private static final byte[] BAI_MAGIC = "BAI\1".getBytes();
    // CRAI is gzipped text, so it's magic is same as {@link java.util.zip.GZIPInputStream.GZIP_MAGIC}
    private static final byte[] CRAI_MAGIC = new byte[] {(byte) 0x1f, (byte) 0x8b};

    // Given an input SAM/BAM/CRAM, using the supplied CRAMEncodingStrategy to create and return a temporary CRAM
    // with the same content as the input SAM/BAM/CRAM but created using the supplied CRAMEncodingStrategy, along
    // with an accompanying temporary companion BAI index file.
    public static Path createCRAMWithBAIForEncodingStrategy(
            final Path sourceFile,
            final CRAMReferenceSource referenceSource,
            final CRAMEncodingStrategy cramEncodingStrategy)
            throws IOException {
        final Path temporaryCRAM = createCRAMAndIndexFiles(".bai");
        final Path temporaryBAI = temporaryCRAM.resolveSibling(temporaryCRAM.getFileName() + ".bai");

        final SamReaderFactory samReadFactory =
                SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);
        try (final SamReader samReader = samReadFactory.open(sourceFile);
                final OutputStream cramFileOutputStream = Files.newOutputStream(temporaryCRAM);
                final OutputStream cramIndexOutputStream = Files.newOutputStream(temporaryBAI);
                final CRAMFileWriter cramWriter = new CRAMFileWriter(
                        cramEncodingStrategy,
                        cramFileOutputStream,
                        cramIndexOutputStream,
                        true,
                        referenceSource,
                        samReader.getFileHeader(),
                        temporaryCRAM.toAbsolutePath().toString());
                final CloseableIterator<SAMRecord> samIterator = samReader.iterator()) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (samIterator.hasNext()) {
                cramWriter.addAlignment(samIterator.next());
            }
        }

        // make sure we're actually using a BAI index
        assertIsBAIFile(temporaryBAI);
        return temporaryCRAM;
    }

    // Given an input SAM/BAM/CRAM, using the supplied CRAMEncodingStrategy to create and return a temporary CRAM
    // with the same content as the input, along with an accompanying temporary companion CRAI index file
    public static Path createCRAMWithCRAIForEncodingStrategy(
            final Path sourceFile,
            final CRAMReferenceSource referenceSource,
            final CRAMEncodingStrategy cramEncodingStrategy)
            throws IOException {
        final Path temporaryCRAM = createCRAMAndIndexFiles(".crai");
        final Path temporaryCRAI = temporaryCRAM.resolveSibling(temporaryCRAM.getFileName() + ".crai");

        final SamReaderFactory samReadFactory = SamReaderFactory.makeDefault()
                .referenceSource(referenceSource)
                .validationStringency(ValidationStringency.STRICT);
        try (final SamReader samReader = samReadFactory.open(sourceFile);
                final OutputStream cramFileOutputStream = Files.newOutputStream(temporaryCRAM);
                final CRAMFileWriter cramWriter = new CRAMFileWriter(
                        cramEncodingStrategy,
                        cramFileOutputStream,
                        null, // suppress BAI index creation and manually create CRAI after the fact (see below)
                        true,
                        referenceSource,
                        samReader.getFileHeader(),
                        temporaryCRAM.toAbsolutePath().toString());
                final CloseableIterator<SAMRecord> samIterator = samReader.iterator()) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (samIterator.hasNext()) {
                cramWriter.addAlignment(samIterator.next());
            }
        }
        // since CRAMFileWriter creates a BAI by default if an index is requested, make sure some codepath
        // didn't accidentally request one, since we want to ensure we use a .crai
        Assert.assertFalse(Files.exists(temporaryCRAM.resolveSibling(temporaryCRAM.getFileName() + ".bai")));

        // now manually create the CRAI
        try (final OutputStream bos = Files.newOutputStream(temporaryCRAI)) {
            CRAMCRAIIndexer.writeIndex(new SeekableFileStream(temporaryCRAM), bos);
        }

        // finally, make sure the contents are actually a CRAI
        assertIsCRAIFile(temporaryCRAI);
        return temporaryCRAM;
    }

    public static Path createCRAMAndIndexFiles(final String indexExtension) throws IOException {
        final Path temporaryCRAMDir = Files.createTempDirectory("tempCRAMWithIndex");
        temporaryCRAMDir.toFile().deleteOnExit();

        final Path temporaryCRAM = temporaryCRAMDir.resolve("tempCRAMWithIndex.cram");
        temporaryCRAM.toFile().deleteOnExit();
        final Path temporaryIndex = temporaryCRAM.resolveSibling(temporaryCRAM.getFileName() + indexExtension);
        temporaryIndex.toFile().deleteOnExit();

        return temporaryCRAM;
    }

    // SamFiles.findIndex(tempCRAM)
    public static Path createBAIForCRAIAsText(
            final Path cramFile, final CRAMReferenceSource referenceSource, final Path craiFile) throws IOException {
        final SAMSequenceDictionary dictionary = SamReaderFactory.makeDefault()
                .referenceSource(referenceSource)
                .open(cramFile)
                .getFileHeader()
                .getSequenceDictionary();

        // first, convert the crai to bai and write that out
        final InputStream is = SamIndexes.openIndexFileAsBaiOrNull(craiFile, dictionary);
        final Path baiOutputFile = craiFile.resolveSibling(craiFile.getFileName() + ".bai");
        baiOutputFile.toFile().deleteOnExit();
        try (final OutputStream fos = Files.newOutputStream(baiOutputFile)) {
            byte b[] = new byte[1];
            while (is.read(b, 0, 1) >= 0) {
                fos.write(b);
            }
        }

        // now, convert the bai to text and write that out
        final Path baiOutputTextFile = craiFile.resolveSibling(craiFile.getFileName() + ".bai.txt");
        baiOutputTextFile.toFile().deleteOnExit();
        BAMIndexer.createAndWriteIndex(baiOutputFile, baiOutputTextFile, true);

        return baiOutputTextFile;
    }

    // SamFiles.findIndex(tempCRAM)
    public static Path createCRAIAsText(final Path craiFile) throws IOException {
        try (final InputStream fis = Files.newInputStream(craiFile)) {
            final CRAIIndex craiIndex = CRAMCRAIIndexer.readIndex(fis);

            final Path temporaryCRAIText = craiFile.resolveSibling(craiFile.getFileName() + ".txt");
            temporaryCRAIText.toFile().deleteOnExit();

            try (final OutputStream fos = Files.newOutputStream(temporaryCRAIText)) {
                fos.write("\nSeqId AlignmentStart AlignmentSpan ContainerOffset SliceOffset SliceSize\n".getBytes());
                for (final CRAIEntry e : craiIndex.getCRAIEntries()) {
                    fos.write(String.format("%s\n", e.toString()).getBytes());
                }
            }
            return temporaryCRAIText;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final List<String> getCRAMResultsForQueryIntervals(
            final Path targetCRAMFile,
            final Path targetIndexFile, // .bai or .crai
            final CRAMReferenceSource referenceSource,
            final QueryInterval[] queryIntervals)
            throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        try (final CRAMFileReader cramReader = new CRAMFileReader(
                        targetCRAMFile, targetIndexFile, referenceSource, ValidationStringency.STRICT);
                final CloseableIterator<SAMRecord> cramIterator = cramReader.query(queryIntervals, true)) {
            Assert.assertEquals(cramReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (cramIterator.hasNext()) {
                queryResults.add(cramIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    public static final List<String> getBAMResultsForQueryIntervals(
            final Path targetBAMFile, final QueryInterval[] queryIntervals) throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        final SamReaderFactory samReadFactory =
                SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);

        try (final SamReader samReader = samReadFactory.open(targetBAMFile);
                final CloseableIterator<SAMRecord> samIterator = samReader.query(queryIntervals, true)) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (samIterator.hasNext()) {
                queryResults.add(samIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    public static final List<String> getCRAMResultsForUnmapped(
            final Path targetCRAMFile,
            final Path targetIndexFile, // .bai or .crai
            final CRAMReferenceSource referenceSource)
            throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        try (final CRAMFileReader cramReader = new CRAMFileReader(
                        targetCRAMFile, targetIndexFile, referenceSource, ValidationStringency.STRICT);
                final CloseableIterator<SAMRecord> cramIterator = cramReader.queryUnmapped()) {
            Assert.assertEquals(cramReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (cramIterator.hasNext()) {
                queryResults.add(cramIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    public static final List<String> getBAMResultsForUnmapped(final Path targetBAMFile) throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        final SamReaderFactory samReadFactory =
                SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);
        try (final SamReader samReader = samReadFactory.open(targetBAMFile);
                final CloseableIterator<SAMRecord> samIterator = samReader.queryUnmapped()) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (samIterator.hasNext()) {
                queryResults.add(samIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    private static void assertIsCRAIFile(final Path indexFile) throws IOException {
        assertFileStartsWith(indexFile, CRAI_MAGIC);
    }

    private static void assertIsBAIFile(final Path indexFile) throws IOException {
        assertFileStartsWith(indexFile, BAI_MAGIC);
    }

    private static void assertFileStartsWith(final Path indexFile, final byte[] magicBytes) throws IOException {
        try (final InputStream fis = Files.newInputStream(indexFile)) {
            for (final byte b : magicBytes) {
                if (fis.read() != (0xFF & b)) {
                    Assert.fail("Unexpected index file header");
                }
            }
        }
    }
}
