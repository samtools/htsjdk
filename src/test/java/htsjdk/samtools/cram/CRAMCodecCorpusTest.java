package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.rans.*;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * HTSCodecs test data is kept in a separate repository, currently at https://github.com/jkbonfield/htscodecs-corpus
 * so it can be shared across htslib/samtools/htsjdk.
 */
public class CRAMCodecCorpusTest extends HtsjdkTest {
    @Test
    public void testGetHTSCodecsCorpus() {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException(String.format(
                    "No HTS codecs test data found." +
                            " The %s environment variable must be set to the location of the local hts codecs test data.",
                    CRAMCodecCorpus.HTSCODECS_TEST_DATA_ENV));
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // RANS tests
    /////////////////////////////////////////////////////////////////////////////////////////////////

    //TODO: the TestDataProviders tests fail if the hts codecs corpus isn't available because

    @DataProvider(name = "rans4x8")
    public Object[][] getRANS4x8TestData() throws IOException {
        // cache/reuse this for each test case to eliminate excessive garbage collection
        final RANS4x8Encode rans4x8Encode = new RANS4x8Encode();
        final RANS4x8Decode rans4x8Decode = new RANS4x8Decode();
        final RANS4x8Params params0 = new RANS4x8Params(RANSParams.ORDER.ZERO); // RANS 4x8 order 0
        final RANS4x8Params params1 = new RANS4x8Params(RANSParams.ORDER.ONE); // RANS 4x8 order 1
        final List<Object[]> testCases = new ArrayList<>();
        getHtsCodecRANSTestFiles().stream()
                .forEach(p ->
                {
                    testCases.add(new Object[] {p, rans4x8Encode , rans4x8Decode, params0 });
                    testCases.add(new Object[] {p, rans4x8Encode , rans4x8Decode, params1 });
                });
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name = "ransNx16")
    public Object[][] getRANS4x16TestData() throws IOException {
        final RANSNx16Encode ransNx16Encode = new RANSNx16Encode();
        final RANSNx16Decode ransNx16Decode = new RANSNx16Decode();
        final RANSNx16Params params0 = new RANSNx16Params(0); // RANS Nx16 order 0, none of the bit flags are set
        final List<Object[]> testCases = new ArrayList<>();
        getHtsCodecRANSTestFiles().stream()
                .forEach(p ->
                {
                    testCases.add(new Object[] {p, ransNx16Encode, ransNx16Decode , params0});
                });
        return testCases.toArray(new Object[][]{});
    }

    @Test (
            dataProvider = "rans4x8",
            dependsOnMethods = "testGetHTSCodecsCorpus",
            description = "Roundtrip using htsjdk RANS4x8." +
                    " Compare the output with the original file" )
    public void testRANSRoundTrip4x8(
            final Path inputTestDataPath,
            final RANS4x8Encode ransEncode,
            final RANS4x8Decode ransDecode,
            final RANS4x8Params params) throws IOException {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException("htscodecs test data is not available locally");
        }
        try (final InputStream is = Files.newInputStream(inputTestDataPath)) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results

            final ByteBuffer uncompressedBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(is)));
            final ByteBuffer compressedBytes = ransEncode.compress(uncompressedBytes, params);
            uncompressedBytes.rewind();
            System.out.println(String.format("%s RANS4x16 Order (%s) Uncompressed: (%,d) Compressed: (%,d)",
                    inputTestDataPath.getFileName(),
                    params.getOrder(),
                    uncompressedBytes.remaining(),
                    compressedBytes.remaining()));
            Assert.assertEquals(ransDecode.uncompress(compressedBytes, params), uncompressedBytes);
        }
    }

    @Test (
            dataProvider = "rans4x8",
            dependsOnMethods = "testGetHTSCodecsCorpus",
            description = "Compress the original file using htsjdk RANS4x8 and compare it with the existing compressed file. " +
                    "Uncompress the existing compressed file using htsjdk RANS4x8 and compare it with the original file.")
    public void testRANSPreCompressed4x8(
            final Path inputTestDataPath,
            final RANS4x8Encode ransEncode,
            final RANS4x8Decode ransDecode,
            final RANS4x8Params params ) throws IOException {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException("htscodecs test data is not available locally");
        }

        final Path preCompressedDataPath = getCompressedRANSPath("4x8",inputTestDataPath, params.getOrder().ordinal());
        try (final InputStream inputStream = Files.newInputStream(inputTestDataPath);
             final InputStream preCompressedInputStream = Files.newInputStream(preCompressedDataPath);
        ) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer inputBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(inputStream)));

            final ByteBuffer preCompressedInputBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInputStream));

            // Use htsjdk to compress the input file from htscodecs repo
            final ByteBuffer htsjdkCompressedBytes = ransEncode.compress(inputBytes, params);
            inputBytes.rewind();

            // Compare the htsjdk compressed bytes with the precompressed file from htscodecs repo
            Assert.assertEquals(htsjdkCompressedBytes, preCompressedInputBytes);

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer htsjdkUncompressedBytes = ransDecode.uncompress(preCompressedInputBytes, params);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(htsjdkUncompressedBytes, inputBytes);
        }
    }

    @Test (
            dataProvider = "ransNx16",
            dependsOnMethods = "testGetHTSCodecsCorpus",
            description = "Roundtrip the original file using RANSNx16 htsjdk." +
                    " Compare the output with the original file" )
    public void testRANSRoundTripNx16(
            final Path inputTestDataPath,
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode ransDecode,
            final RANSNx16Params params) throws IOException {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException("htscodecs test data is not available locally");
        }

        try (final InputStream is = Files.newInputStream(inputTestDataPath)) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results

            final ByteBuffer uncompressedBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(is)));
            final ByteBuffer compressedBytes = ransEncode.compress(uncompressedBytes, params);
            uncompressedBytes.rewind();
            System.out.println(String.format("%s RANS4x16 Order (%s) Uncompressed: (%,d) Compressed: (%,d)",
                    inputTestDataPath.getFileName(),
                    params.getOrder(),
                    uncompressedBytes.remaining(),
                    compressedBytes.remaining()));
            Assert.assertEquals(ransDecode.uncompress(compressedBytes,params), uncompressedBytes);
        }
    }

    @Test (
            dataProvider = "ransNx16",
            dependsOnMethods = "testGetHTSCodecsCorpus",
            description = "Compress the original file using htsjdk RANSNx16 and compare it with the existing compressed file. " +
                    "Uncompress the existing compressed file using htsjdk RANSNx16 and compare it with the original file.")
    public void testRANSPreCompressedNx16(
            final Path inputTestDataPath,
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode ransDecode,
            final RANSNx16Params params) throws IOException {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException("htscodecs test data is not available locally");
        }

        final Path preCompressedDataPath = getCompressedRANSPath("4x16",inputTestDataPath, params.getOrder().ordinal());
        try (final InputStream inputStream = Files.newInputStream(inputTestDataPath);
             final InputStream preCompressedInputStream = Files.newInputStream(preCompressedDataPath);
        ) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            final ByteBuffer inputBytes = ByteBuffer.wrap(filterEmbeddedNewlines(IOUtils.toByteArray(inputStream)));

            final ByteBuffer preCompressedInputBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInputStream));

            // Use htsjdk to compress the input file from htscodecs repo
            final ByteBuffer htsjdkCompressedBytes = ransEncode.compress(inputBytes, params);
            inputBytes.rewind();

            // Compare the htsjdk compressed bytes with the precompressed file from htscodecs repo
            Assert.assertEquals(htsjdkCompressedBytes, preCompressedInputBytes);

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer htsjdkUncompressedBytes = ransDecode.uncompress(preCompressedInputBytes, params);

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(htsjdkUncompressedBytes, inputBytes);
        }
    }

    // return a list of all RANS test data files in the htscodecs test directory
    private List<Path> getHtsCodecRANSTestFiles() throws IOException {
        CRAMCodecCorpus.assertHTSCodecsTestDataAvailable();
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                        CRAMCodecCorpus.getHTSCodecsTestDataLocation().resolve("dat"),
                        path -> path.getFileName().startsWith("q4") ||
                                path.getFileName().startsWith("q8") ||
                                path.getFileName().startsWith("qvar"))
                // q40+dir is excluded because the uncompressed size in the compressed file prefix does not match
                // the original file size.
                // Q: why isn't q40+dir not included as it also startswith q4?
                .forEach(path -> paths.add(path));
        return paths;
    }

    // the input files have embedded newlines that the test remove before round-tripping...
    final byte[] filterEmbeddedNewlines(final byte[] rawBytes) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (final byte b : rawBytes) {
                if (b != '\n') {
                    baos.write(b);
                }
            }
            return baos.toByteArray();
        }
    }

    // Given a test file name, map it to the corresponding rans compressed path
    final Path getCompressedRANSPath(final String ransType,final Path inputTestDataPath, int order) {
        final String compressedFileName = String.format("r%s/%s.%s", ransType, inputTestDataPath.getFileName(), order);
        return inputTestDataPath.getParent().resolve(compressedFileName);
    }

}