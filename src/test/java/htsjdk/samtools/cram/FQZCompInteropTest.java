package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.fqzcomp.FQZCompDecode;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FQZCompInteropTest extends HtsjdkTest {

    public static final String COMPRESSED_FQZCOMP_DIR = "fqzcomp";

    // uses the available compressed interop test files
    @DataProvider(name = "decodeOnlyTestCases")
    public Object[][] getDecodeOnlyTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // FQZComp decoder
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : CRAMInteropTestUtils.getInteropCompressedFilePaths(COMPRESSED_FQZCOMP_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedFilePath(path),
                    new FQZCompDecode()
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test(description = "Test if CRAM Interop Test Data is available")
    public void testHtsCodecsCorpusIsAvailable() {
        if (!CRAMInteropTestUtils.isInteropTestDataAvailable()) {
            throw new SkipException(String.format("CRAM Interop Test Data is not available at %s",
                    CRAMInteropTestUtils.INTEROP_TEST_FILES_PATH));
        }
    }

    @Test (
            dependsOnMethods = "testHtsCodecsCorpusIsAvailable",
            dataProvider = "decodeOnlyTestCases",
            description = "Uncompress the existing compressed file using htsjdk FQZComp and compare it with the original file.")
    public void testDecodeOnly(
            final Path compressedFilePath,
            final Path uncompressedInteropPath,
            final FQZCompDecode fqzcompDecode) throws IOException {
        try (final InputStream uncompressedInteropStream = Files.newInputStream(uncompressedInteropPath);
             final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath)
        ) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through FQZComp codec
            // and compare the results
            final ByteBuffer uncompressedInteropBytes = CompressionUtils.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            final ByteBuffer preCompressedInteropBytes = CompressionUtils.wrap(IOUtils.toByteArray(preCompressedInteropStream));

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer uncompressedHtsjdkBytes = fqzcompDecode.uncompress(preCompressedInteropBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testDecodeOnly as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

}