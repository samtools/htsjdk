package htsjdk.samtools;

import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.CloseableIterator;
import org.testng.Assert;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CRAMIndexTestHelper {

    private final static byte[] BAI_MAGIC ="BAI\1".getBytes();
    // CRAI is gzipped text, so it's magic is same as {@link java.util.zip.GZIPInputStream.GZIP_MAGIC}
    private final static byte[] CRAI_MAGIC = new byte[]{(byte) 0x1f, (byte) 0x8b};

    // Given an input SAM/BAM/CRAM, using the supplied CRAMEncodingStrategy to create and return a temporary CRAM
    // with the same content as the input SAM/BAM/CRAM but created using the supplied CRAMEncodingStrategy, along
    // with an accompanying temporary companion BAI index file.
    public static File createCRAMWithBAIForEncodingStrategy(
            final File sourceFile,
            final CRAMReferenceSource referenceSource,
            final CRAMEncodingStrategy cramEncodingStrategy) throws IOException
    {
        final File temporaryCRAM = createCRAMAndIndexFiles(".bai");
        final File temporaryBAI = new File(temporaryCRAM.getAbsolutePath() + ".bai");

        final SamReaderFactory samReadFactory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);
        try (final SamReader samReader = samReadFactory.open(sourceFile.toPath());
             final FileOutputStream cramFileOutputStream = new FileOutputStream(temporaryCRAM);
             final FileOutputStream cramIndexOutputStream = new FileOutputStream(temporaryBAI);
             final CRAMFileWriter cramWriter = new CRAMFileWriter(
                     cramEncodingStrategy,
                     cramFileOutputStream,
                     cramIndexOutputStream,
                     true,
                     referenceSource,
                     samReader.getFileHeader(),
                     temporaryCRAM.getAbsolutePath());
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
    public static File createCRAMWithCRAIForEncodingStrategy(
            final File sourceFile,
            final CRAMReferenceSource referenceSource,
            final CRAMEncodingStrategy cramEncodingStrategy) throws IOException
    {
        final File temporaryCRAM = createCRAMAndIndexFiles(".crai");
        final File temporaryCRAI = new File(temporaryCRAM.getAbsolutePath() + ".crai");

        final SamReaderFactory samReadFactory = SamReaderFactory.makeDefault()
                .referenceSource(referenceSource)
                .validationStringency(ValidationStringency.STRICT);
        try (final SamReader samReader = samReadFactory.open(sourceFile.toPath());
             final FileOutputStream cramFileOutputStream = new FileOutputStream(temporaryCRAM);
             final CRAMFileWriter cramWriter = new CRAMFileWriter(
                    cramEncodingStrategy,
                    cramFileOutputStream,
                    null, // suppress BAI index creation and manually create CRAI after the fact (see below)
                     true,
                     referenceSource,
                    samReader.getFileHeader(),
                     temporaryCRAM.getAbsolutePath());
             final CloseableIterator<SAMRecord> samIterator = samReader.iterator()) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (samIterator.hasNext()) {
                cramWriter.addAlignment(samIterator.next());
            }
        }
        // since CRAMFileWriter creates a BAI by default if an index is requested, make sure some codepath
        // didn't accidentally request one, since we want to ensure we use a .crai
        Assert.assertFalse(Files.exists(Paths.get(temporaryCRAM.getAbsolutePath() + ".bai")));

        // now manually create the CRAI
        try (FileOutputStream bos = new FileOutputStream(temporaryCRAI)) {
            CRAMCRAIIndexer.writeIndex(new SeekableFileStream(temporaryCRAM), bos);
        }

        // finally, make sure the contents are actually a CRAI
        assertIsCRAIFile(temporaryCRAI);
        return temporaryCRAM;
    }

    public static File createCRAMAndIndexFiles(final String indexExtension) throws IOException {
        final Path temporaryCRAMDir = Files.createTempDirectory("tempCRAMWithIndex");
        temporaryCRAMDir.toFile().deleteOnExit();

        final File temporaryCRAM = new File(temporaryCRAMDir.toFile(), "tempCRAMWithIndex.cram");
        temporaryCRAM.deleteOnExit();
        final File temporaryIndex = new File(temporaryCRAM.getAbsolutePath() + indexExtension);
        temporaryIndex.deleteOnExit();

        return temporaryCRAM;
    }

    //SamFiles.findIndex(tempCRAM)
    public static File createBAIForCRAIAsText(
            final File cramFile,
            final CRAMReferenceSource referenceSource,
            final File craiFile) throws IOException {
        final SAMSequenceDictionary dictionary =
                SamReaderFactory.makeDefault()
                        .referenceSource(referenceSource)
                        .open(cramFile)
                        .getFileHeader().getSequenceDictionary();

        // first, convert the crai to bai and write that out
        final InputStream is = SamIndexes.openIndexFileAsBaiOrNull(craiFile, dictionary);
        final File baiOutputFile = new File(craiFile.getAbsolutePath() + ".bai");
        baiOutputFile.deleteOnExit();
        try (final FileOutputStream fos = new FileOutputStream(baiOutputFile)) {
            byte b[] = new byte[1];
            while (is.read(b, 0, 1) >= 0) {
                fos.write(b);
            }
        }

        // now, convert the bai to text and write that out
        final File baiOutputTextFile = new File(craiFile.getAbsolutePath() + ".bai.txt");
        baiOutputTextFile.deleteOnExit();
        BAMIndexer.createAndWriteIndex(baiOutputFile, baiOutputTextFile, true);

        return baiOutputTextFile;
    }

    //SamFiles.findIndex(tempCRAM)
    public static File createCRAIAsText(final File craiFile) throws IOException {
        try (final FileInputStream fis = new FileInputStream(craiFile)) {
            final CRAIIndex craiIndex = CRAMCRAIIndexer.readIndex(fis);

            final File temporaryCRAIText = new File(craiFile.getAbsolutePath() + ".txt");
            temporaryCRAIText.deleteOnExit();

            try (final FileOutputStream fos = new FileOutputStream(temporaryCRAIText)) {
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
            final File targetCRAMFile,
            final File targetIndexFile, // .bai or .crai
            final CRAMReferenceSource referenceSource,
            final QueryInterval[] queryIntervals) throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        try (final CRAMFileReader cramReader = new CRAMFileReader(
                targetCRAMFile,
                targetIndexFile,
                referenceSource,
                ValidationStringency.STRICT);
             final CloseableIterator<SAMRecord> cramIterator = cramReader.query(queryIntervals, true)) {
            Assert.assertEquals(cramReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (cramIterator.hasNext()) {
                queryResults.add(cramIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    public static final List<String> getBAMResultsForQueryIntervals(
            final File targetBAMFile,
            final QueryInterval[] queryIntervals) throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        final SamReaderFactory samReadFactory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);

        try (final SamReader samReader = samReadFactory.open(targetBAMFile.toPath());
             final CloseableIterator<SAMRecord> samIterator = samReader.query(queryIntervals, true)) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (samIterator.hasNext()) {
                queryResults.add(samIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    public static final List<String> getCRAMResultsForUnmapped(
            final File targetCRAMFile,
            final File targetIndexFile, // .bai or .crai
            final CRAMReferenceSource referenceSource) throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        try (final CRAMFileReader cramReader = new CRAMFileReader(
                targetCRAMFile,
                targetIndexFile,
                referenceSource,
                ValidationStringency.STRICT);
             final CloseableIterator<SAMRecord> cramIterator = cramReader.queryUnmapped()) {
            Assert.assertEquals(cramReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (cramIterator.hasNext()) {
                queryResults.add(cramIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    public static final List<String> getBAMResultsForUnmapped(final File targetBAMFile) throws IOException {
        final List<String> queryResults = new ArrayList<>(); // might contain duplicates
        final SamReaderFactory samReadFactory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);
        try (final SamReader samReader = samReadFactory.open(targetBAMFile.toPath());
             final CloseableIterator<SAMRecord> samIterator = samReader.queryUnmapped()) {
            Assert.assertEquals(samReader.getFileHeader().getSortOrder(), SAMFileHeader.SortOrder.coordinate);
            while (samIterator.hasNext()) {
                queryResults.add(samIterator.next().getReadName());
            }
        }
        return queryResults;
    }

    private static void assertIsCRAIFile(final File indexFile) throws IOException {
        assertFileStartsWith(indexFile, CRAI_MAGIC);
    }

    private static void assertIsBAIFile(final File indexFile) throws IOException {
        assertFileStartsWith(indexFile, BAI_MAGIC);
    }

    private static void assertFileStartsWith(final File indexFile, final byte[] magicBytes) throws IOException {
        try (final FileInputStream fis = new FileInputStream(indexFile)) {
            for (final byte b : magicBytes) {
                if (fis.read() != (0xFF & b)) {
                    Assert.fail("Unexpected index file header");
                }
            }
        }
    }

}
