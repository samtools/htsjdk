package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationDecode;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NameTokenizationInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_TOK_DIR = "tok3";

    @DataProvider(name = "allNameTokenizationFiles")
    public Object[][] getAllRansCodecsForRoundTrip() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path, NameTokenization decoder,
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : getInteropNameTokenizationCompressedFiles()) {
                Object[] objects = new Object[]{
                        path,
                        getNameTokenizationUnCompressedFilePath(path),
                        new NameTokenisationDecode()
                };
                testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});

    }

    @Test(
            dataProvider = "allNameTokenizationFiles",
            description = "Uncompress the existing compressed file using htsjdk NameTokenization " +
                    "and compare it with the original file.")
    public void testNameTokenizationDecoder(
            final Path compressedFilePath,
            final Path uncompressedFilePath,
            final NameTokenisationDecode nameTokenisationDecode) throws IOException {
        final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath);
        final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));
        final InputStream unCompressedInteropStream = Files.newInputStream(uncompressedFilePath);
        final ByteBuffer unCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(unCompressedInteropStream));
        String decompressedNames = nameTokenisationDecode.uncompress(preCompressedInteropBytes);
        ByteBuffer decompressedNamesBuffer = StandardCharsets.UTF_8.encode(decompressedNames);
        Assert.assertEquals(decompressedNamesBuffer,unCompressedInteropBytes);
    }

    // return a list of all NameTokenization encoded test data files in the htscodecs/tests/names/tok3 directory
    private List<Path> getInteropNameTokenizationCompressedFiles() throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                CRAMInteropTestUtils.getInteropTestDataLocation().resolve("names/"+COMPRESSED_TOK_DIR),
                        path -> Files.isRegularFile(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // Given a compressed test file path, return the corresponding uncompressed file path
    public static final Path getNameTokenizationUnCompressedFilePath(final Path compressedInteropPath) {
        String uncompressedFileName = getUncompressedFileName(compressedInteropPath.getFileName().toString());
        // Example compressedInteropPath: ../names/tok3/01.names.1 => unCompressedFilePath: ../names/01.names
        return compressedInteropPath.getParent().getParent().resolve(uncompressedFileName);
    }

    public static final String getUncompressedFileName(final String compressedFileName) {
        // Returns original filename from compressed file name
        int lastDotIndex = compressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0) {
            String fileName = compressedFileName.substring(0, lastDotIndex);
            return fileName;
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a perios followed by a number that" +
                    "indicates type of compression. Actual compressed file name = "+ compressedFileName);
        }
    }

}