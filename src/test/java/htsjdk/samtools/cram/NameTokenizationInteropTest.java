package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationDecode;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationEncode;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

// Test the roundtrip and decompression of name tokenization encoded data using the hts-specs cram interop stream
// data for the name tokenization codec.
public class NameTokenizationInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_TOK_DIR = "tok3/";

    // the hts-specs cram interop tests streams use this separator in the raw (uncompressed) streams to separate
    // the read names that are passed into/out of the name tokenization codec, but htsjdk uses '\0' because
    // the downstream htsjdk cram code assumes that value; so in the interop tests we need to replace the hts-specs
    // separator with the corresponding name tokenization separator used by htsjdk
    public static final byte HTS_SPECS_NAME_SEPARATOR = '\n';

    @DataProvider(name = "allNameTokInteropTests")
    public Object[][] getAllNameTokenizationInteropTests() throws IOException {
        // raw (unCompressed) path, useArith
        final List<Object[]> testCases = new ArrayList<>();
        for (final Path preCompressedInteropPath : CRAMInteropTestUtils.getCRAMInteropCompressedPaths(COMPRESSED_TOK_DIR)) {
            for (boolean useArith: new boolean[]{true, false}) {
               testCases.add(new Object[] {
                       //unCompressedPathFromPreCompressedPath(preCompressedInteropPath),
                       CRAMInteropTestUtils.getUnCompressedPathForCompressedPath(preCompressedInteropPath),
                       useArith
                });
            }
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test (dataProvider = "allNameTokInteropTests",
            description = "Roundtrip the uncompressed path using htsjdk NameTokenization codec, compare the output with the original uncompressed path")
    public void testNameTokRoundTrip(
            final Path unCompressedInteropPath,
            final boolean useArith) throws IOException {

        try (final InputStream unCompressedInteropStream = new GZIPInputStream(Files.newInputStream(unCompressedInteropPath))) {
            // convert the uncompressed data from hts-specs to the unCompressed format used to pass data in/out of the htsjdk name tok codec
            final ByteBuffer unCompressedInteropBytes = convertHTSSpecsToHTSJDKStreamFormat(
                    ByteBuffer.wrap(IOUtils.toByteArray(unCompressedInteropStream)),
                    NameTokenisationDecode.NAME_SEPARATOR
            );

            // Use htsjdk to compress the uncompressed data with the provided useArith flag
            final NameTokenisationEncode nameEncoder = new NameTokenisationEncode();
            final ByteBuffer compressedHtsjdkBytes = nameEncoder.compress(
                    unCompressedInteropBytes,
                    useArith,
                    NameTokenisationDecode.NAME_SEPARATOR);

            // Now use htsjdk to uncompress the data we just compressed
            final NameTokenisationDecode nameDecoder = new NameTokenisationDecode();
            final ByteBuffer unCompressedHtsjdkBytes = ByteBuffer.wrap(nameDecoder.uncompress(
                    compressedHtsjdkBytes,
                    NameTokenisationDecode.NAME_SEPARATOR));

            // compare to the original (ByteBuffers have to have identical positions in order to be equal (!),
            // so rewind both buffers before comparing)
            unCompressedInteropBytes.rewind();
            unCompressedHtsjdkBytes.rewind();
            Assert.assertEquals(unCompressedHtsjdkBytes, unCompressedInteropBytes);
        }
    }

    @DataProvider(name = "uncompressNameTokInteropTests")
    public Object[][] getUncompressNameTokInteropTests() throws IOException {
        // compressed path (hts-specs interop preCompressed file), raw (unCompressed) path, useArith (used for round tripping only)
        final List<Object[]> testCases = new ArrayList<>();
        for (final Path preCompressedInteropPath : CRAMInteropTestUtils.getCRAMInteropCompressedPaths(COMPRESSED_TOK_DIR)) {
            testCases.add(new Object[] {
                    preCompressedInteropPath,
                    //unCompressedPathFromPreCompressedPath(preCompressedInteropPath)
                    CRAMInteropTestUtils.getUnCompressedPathForCompressedPath(preCompressedInteropPath)
            });
        }
        return testCases.toArray(new Object[][]{});
    }
    
    @Test (dataProvider = "uncompressNameTokInteropTests",
            description = "Uncompress the hts-specs compressed file using htsjdk and compare it with the uncompressed file.")
    public void testNameTokUnCompress(
            final Path preCompressedInteropPath,
            final Path unCompressedInteropPath) throws IOException {
        try (final InputStream preCompressedInteropStream = Files.newInputStream(preCompressedInteropPath);
            final InputStream unCompressedInteropStream = new GZIPInputStream(Files.newInputStream(unCompressedInteropPath))) {
            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));
            // convert the uncompressed data from hts-specs to the unCompressed format used to pass data in/out of the htsjdk name tok codec
            final ByteBuffer uncompressedInteropBytes = convertHTSSpecsToHTSJDKStreamFormat(
                    ByteBuffer.wrap(IOUtils.toByteArray(unCompressedInteropStream)),
                    NameTokenisationDecode.NAME_SEPARATOR
            );

            // Use htsjdk to uncompress the precompressed file from hts-specs repo
            final NameTokenisationDecode nameTokenisationDecode = new NameTokenisationDecode();
            final ByteBuffer uncompressedHtsjdkBytes = ByteBuffer.wrap(
                    nameTokenisationDecode.uncompress(preCompressedInteropBytes, NameTokenisationDecode.NAME_SEPARATOR)
            );

            // Compare the htsjdk uncompressed bytes with the original input file from hts-specs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        }
    }

    // translate an hts-specs interop stream into the stream format used by the htsjdk name tokenization codec
    private ByteBuffer convertHTSSpecsToHTSJDKStreamFormat(final ByteBuffer htsSpecsBuffer, final byte newSeparator) {
        final ByteBuffer translatedBuffer = ByteBuffer.allocate(htsSpecsBuffer.limit());
        for (int i = 0; i < htsSpecsBuffer.limit(); i++) {
            if (htsSpecsBuffer.get(i) == HTS_SPECS_NAME_SEPARATOR) {
                translatedBuffer.put(i, newSeparator);
            } else {
                translatedBuffer.put(i, htsSpecsBuffer.get(i));
            }
        }
        return translatedBuffer;
    }

}