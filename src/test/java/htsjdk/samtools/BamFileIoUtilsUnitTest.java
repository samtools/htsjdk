package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.beta.exception.HtsjdkException;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

public class BamFileIoUtilsUnitTest extends HtsjdkTest {
    @DataProvider(name="ReheaderBamFileTestInput")
    public Object[][] getReheaderBamFileTestInput() { // tsato: is this the right naming scheme? e.g. get(method-name)Input
        // tsato: extract this as a method that takes a number of arguments and returns 2^n elements
        return new Object[][] {
                {true, true},
                {true, false},
                {false, true},
                {false, false}
        };
    }

    @Test(dataProvider = "ReheaderBamFileTestInput")
    public void testReheaderBamFile(final boolean createMd5, final boolean createIndex) throws IOException {
        final File originalBam =HtsjdkTestUtils.NA12878_500;
        SAMFileHeader header = SamReaderFactory.make().getFileHeader(HtsjdkTestUtils.NA12878_500);
        header.addComment("This is a new, modified header");

        final Path output = Files.createTempFile("output", ".bam");
        BamFileIoUtils.reheaderBamFile(header, originalBam.toPath(), output, createMd5, createIndex);

        // Confirm that the header has been replaced
        final SamReader outputReader = SamReaderFactory.make().open(output);
        Assert.assertEquals(outputReader.getFileHeader(), header);

        // Check that the reads are the same as the original
        // tsato: should I be using something similar to IOUtil.toPath for converting Path -> File to propagate null?
        assertBamRecordsEqual(originalBam, output.toFile());

        if (createMd5){
            Assert.assertTrue(Files.exists(output.resolveSibling(output.getFileName() + FileExtensions.MD5)));
        }

        if (createIndex){
            Assert.assertTrue(SamReaderFactory.make().open(output).hasIndex());
        }
    }

    /**
     * Compares all the reads in the two bam files are equal (but does not check the headers).
     */
    private void assertBamRecordsEqual(final File bam1, final File bam2){
        try (SamReader reader1 = SamReaderFactory.make().open(bam1);
             SamReader reader2 = SamReaderFactory.make().open(bam2)) {
            final Iterator<SAMRecord> originalBamIterator = reader1.iterator();
            final Iterator<SAMRecord> outputBamIterator = reader2.iterator();

            Assert.assertEquals(originalBamIterator, outputBamIterator);
        } catch (Exception e){
            throw new HtsjdkException("Encountered an error reading bam files: " + bam1.getAbsolutePath() + " and " + bam2.getAbsolutePath(), e);
        }
    }

    @DataProvider(name="BlockCopyBamFileTestInput")
    public Object[][] getBlockCopyBamFileTestInput() {
        return new Object[][] {
                {true, true},
                {true, false},
                {false, true},
                {false, false}
        };
    }

    @Test(dataProvider = "BlockCopyBamFileTestInput")
    public void testBlockCopyBamFile(final boolean skipHeader, final boolean skipTerminator) throws IOException {
        final File output = File.createTempFile("output", ".bam");
        try (final OutputStream out = Files.newOutputStream(output.toPath())) {
            final Path input = IOUtil.toPath(HtsjdkTestUtils.NA12878_500);

            BamFileIoUtils.blockCopyBamFile(IOUtil.toPath(HtsjdkTestUtils.NA12878_500), out, skipHeader, skipTerminator);

            final SamReader inputReader = SamReaderFactory.make().open(input);
            final SamReader outputReader = SamReaderFactory.make().open(output);

            if (skipHeader) {
                SAMFileHeader h = outputReader.getFileHeader();
                Assert.assertTrue(h.getReadGroups().isEmpty()); // a proxy for the header being empty
            } else {
                Assert.assertEquals(outputReader.getFileHeader(), inputReader.getFileHeader());
                // Reading will fail when the header is absent
                assertBamRecordsEqual(input.toFile(), output);
            }

            if (skipTerminator) {
                BlockCompressedInputStream.FileTermination termination = BlockCompressedInputStream.checkTermination(output);
                Assert.assertEquals(termination, BlockCompressedInputStream.FileTermination.HAS_HEALTHY_LAST_BLOCK);
            }
        } catch (IOException e){
            throw new HtsjdkException("Caught an IO exception block copying a bam file to " + output.getAbsolutePath(), e);
        }
    }
}