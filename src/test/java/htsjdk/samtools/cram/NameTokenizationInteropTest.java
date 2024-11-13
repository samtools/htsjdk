package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationDecode;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationEncode;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NameTokenizationInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_TOK_DIR = "tok3";

    @DataProvider(name = "allNameTokenizationInteropTests")
    public Object[][] getAllNameTokenizationInteropTests() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path, NameTokenization encoder, NameTokenization decoder
        final List<Object[]> testCases = new ArrayList<>();
        for (final Path preCompressedFilePath : getPreCompressedInteropNameTokTestFiles()) {
                Object[] objects = new Object[] {
                        preCompressedFilePath,
                        getExpectedRawPathFromCompressedPath(preCompressedFilePath)
                };
                testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    //TODO: check in the interop streams and get rid of these getCorpus methods
    @Test(description = "Test if CRAM Interop Test Data is available")
    public void testGetHTSCodecsCorpus() {
        if (!CRAMInteropTestUtils.isInteropTestDataAvailable()) {
            throw new SkipException(String.format("CRAM Interop Test Data is not available at %s",
                    CRAMInteropTestUtils.INTEROP_TEST_FILES_PATH));
        }
    }

    @Test (
            dependsOnMethods = "testGetHTSCodecsCorpus",
            dataProvider = "allNameTokenizationInteropTests",
            description = "Roundtrip using htsjdk NameTokenization Codec. Compare the output with the original file" )
    public void testNameTokenizationRoundTrip(
            final Path precompressedFilePath,
            final Path expectedRawFilePath) throws IOException {

        try (final InputStream expectedRawInteropStream = Files.newInputStream(expectedRawFilePath)) {
            final ByteBuffer expectedRawInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(expectedRawInteropStream));

            final NameTokenisationEncode nameTokenisationEncode = new NameTokenisationEncode();
            final ByteBuffer compressedHtsjdkBytes = nameTokenisationEncode.compress(expectedRawInteropBytes);

            final NameTokenisationDecode nameTokenisationDecode = new NameTokenisationDecode();
            //TODO: get rid of the intermediate String returned by uncompress....
            final String decompressedHtsjdkString = nameTokenisationDecode.uncompress(compressedHtsjdkBytes);

            final ByteBuffer decompressedHtsjdkBytes = StandardCharsets.UTF_8.encode(decompressedHtsjdkString);
            translateNameDelimiter(decompressedHtsjdkBytes);
            expectedRawInteropBytes.rewind();
            Assert.assertEquals(decompressedHtsjdkBytes, expectedRawInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testRangeRoundTrip as either the input precompressed file " +
                    "or the uncompressed file is missing.", ex);
        }
    }

    @Test (
            dependsOnMethods = "testGetHTSCodecsCorpus",
            dataProvider = "allNameTokenizationInteropTests",
            description = "Compress the original file using htsjdk NameTokenization Codec and compare it with the existing compressed file. " +
                    "Uncompress the existing compressed file using htsjdk NameTokenization Codec and compare it with the original file.")
    public void testNameTokenizationDecompress(
            final Path precompressedFilePath,
            final Path expectedUncompressedFilePath) throws IOException {
        try (final InputStream preCompressedInteropStream = Files.newInputStream(precompressedFilePath);
            final InputStream expectedUnCompressedStream = Files.newInputStream(expectedUncompressedFilePath)) {

            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));
            final ByteBuffer unCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(expectedUnCompressedStream));
            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final NameTokenisationDecode nameTokenisationDecode = new NameTokenisationDecode();
            final String uncompressedHtsjdkString = nameTokenisationDecode.uncompress(preCompressedInteropBytes);
            final ByteBuffer uncompressedHtsjdkBytes = StandardCharsets.UTF_8.encode(uncompressedHtsjdkString);
            translateNameDelimiter(uncompressedHtsjdkBytes);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, unCompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testNameTokenizationPrecompressed as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

    // return a list of all NameTokenization encoded test data files in the htscodecs/tests/names/tok3 directory
    private List<Path> getPreCompressedInteropNameTokTestFiles() throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                CRAMInteropTestUtils.getInteropTestDataLocation().resolve("names/"+COMPRESSED_TOK_DIR),
                        path -> Files.isRegularFile(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // Given a compressed test file path, return the corresponding uncompressed file path
    private static final Path getExpectedRawPathFromCompressedPath(final Path compressedInteropPath) {
        final String uncompressedFileName = getUncompressedFileName(compressedInteropPath.getFileName().toString());
        // Example compressedInteropPath: ../names/tok3/01.names.1 => unCompressedFilePath: ../names/01.names
        return compressedInteropPath.getParent().getParent().resolve(uncompressedFileName);
    }

    private static final String getUncompressedFileName(final String compressedFileName) {
        // Returns original filename from compressed file name
        int lastDotIndex = compressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0) {
            return compressedFileName.substring(0, lastDotIndex);
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a period followed by a number that" +
                    "indicates type of compression. Actual compressed file name = "+ compressedFileName);
        }
    }

    private static void translateNameDelimiter(final ByteBuffer byteBuffer) {
        for (int i = 0; i < byteBuffer.limit(); i++) {
            if (byteBuffer.get(i) == ((byte) '\0')) {
                byteBuffer.put(i, (byte) '\n');
            }
        }
    }
}