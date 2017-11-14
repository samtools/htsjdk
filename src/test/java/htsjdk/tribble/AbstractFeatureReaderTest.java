package htsjdk.tribble;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.FileTruncatedException;
import htsjdk.samtools.util.TestUtil;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.VariantBaseTest;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.function.Function;

import static org.testng.Assert.*;

/**
 * @author jacob
 * @date 2013-Apr-10
 */
public class AbstractFeatureReaderTest extends HtsjdkTest {

    final static String HTTP_INDEXED_VCF_PATH = TestUtil.BASE_URL_FOR_HTTP_TESTS + "ex2.vcf";
    final static String LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH = VariantBaseTest.variantTestDataRoot + "ex2.vcf";

    //the "mangled" versions of the files have an extra byte added to the front of the file that makes them invalid
    private static final String TEST_PATH = "src/test/resources/htsjdk/tribble/AbstractFeatureReaderTest/";
    private static final String MANGLED_VCF = TEST_PATH + "mangledBaseVariants.vcf";
    private static final String MANGLED_VCF_INDEX = TEST_PATH + "mangledBaseVariants.vcf.idx";
    private static final String VCF = TEST_PATH + "baseVariants.vcf";
    private static final String VCF_INDEX = TEST_PATH + "baseVariants.vcf.idx";
    private static final String VCF_TABIX_BLOCK_GZIPPED = TEST_PATH + "baseVariants.vcf.gz";
    private static final String VCF_TABIX_INDEX = TEST_PATH + "baseVariants.vcf.gz.tbi";
    private static final String MANGLED_VCF_TABIX_BLOCK_GZIPPED = TEST_PATH + "baseVariants.mangled.vcf.gz";
    private static final String MANGLED_VCF_TABIX_INDEX = TEST_PATH + "baseVariants.mangled.vcf.gz.tbi";
    private static final String CORRUPTED_VCF_INDEX = TEST_PATH + "corruptedBaseVariants.vcf.idx";

    //wrapper which skips the first byte of a file and leaves the rest unchanged
    private static final Function<SeekableByteChannel, SeekableByteChannel> WRAPPER = SkippingByteChannel::new;

    /**
     * Asserts readability and correctness of VCF over HTTP.  The VCF is indexed and requires and index.
     */
    @Test
    public void testVcfOverHTTP() throws IOException {
        final VCFCodec codec = new VCFCodec();
        final AbstractFeatureReader<VariantContext, LineIterator> featureReaderHttp =
                AbstractFeatureReader.getFeatureReader(HTTP_INDEXED_VCF_PATH, codec, true); // Require an index to
        final AbstractFeatureReader<VariantContext, LineIterator> featureReaderLocal =
                AbstractFeatureReader.getFeatureReader(LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH, codec, false);
        final CloseableTribbleIterator<VariantContext> localIterator = featureReaderLocal.iterator();
        for (final Feature feat : featureReaderHttp.iterator()) {
            assertEquals(feat.toString(), localIterator.next().toString());
        }
        assertFalse(localIterator.hasNext());
    }

    @Test
    public void testLoadBEDFTP() throws Exception {
        final String path = "ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands with spaces.hg18.bed";
        final BEDCodec codec = new BEDCodec();
        final AbstractFeatureReader<BEDFeature, LineIterator> bfs = AbstractFeatureReader.getFeatureReader(path, codec, false);
        for (final Feature feat : bfs.iterator()) {
            assertNotNull(feat);
        }
    }

    @DataProvider(name = "blockCompressedExtensionExtensionStrings")
    public Object[][] createBlockCompressedExtensionStrings() {
        return new Object[][] {
                { "testzip.gz", true },
                { "test.gzip", true },
                { "test.bgz", true },
                { "test.bgzf", true },
                { "test.bzip2", false }
        };
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings")
    public void testBlockCompressionExtensionString(final String testString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testString), expected);
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionStrings")
    public void testBlockCompressionExtensionFile(final String testString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(new File(testString)), expected);
    }

    @DataProvider(name = "blockCompressedExtensionExtensionURIStrings")
    public Object[][] createBlockCompressedExtensionURIs() {
        return new Object[][]{
                {"testzip.gz", true},
                {"test.gzip", true},
                {"test.bgz", true},
                {"test.bgzf", true},
                {"test", false},
                {"test.bzip2", false},

                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gz", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gzip", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgz", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgzf", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bzip2", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877", false},

                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gz?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gzip?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgz?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgzf?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bzip2?alt=media", false},

                {"ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands.hg18.gz", true},
                {"ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands.hg18.bed", false}
        };
    }

    @Test(dataProvider = "blockCompressedExtensionExtensionURIStrings")
    public void testBlockCompressionExtension(final String testURIString, final boolean expected) throws URISyntaxException {
        URI testURI = URI.create(testURIString);
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testURI), expected);
    }

    @DataProvider(name = "vcfFileAndWrapperCombinations")
    private static Object[][] vcfFileAndWrapperCombinations(){
        return new Object[][] {
                {VCF, VCF_INDEX, null, null},
                {MANGLED_VCF, MANGLED_VCF_INDEX, WRAPPER, WRAPPER},
                {VCF, MANGLED_VCF_INDEX, null, WRAPPER},
                {MANGLED_VCF, VCF_INDEX, WRAPPER, null},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX, WRAPPER, WRAPPER},
                {VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX, null, WRAPPER},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, WRAPPER, null},
                {VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX, null, null},
        };
    }

    @Test(dataProvider = "vcfFileAndWrapperCombinations")
    public void testGetFeatureReaderWithPathAndWrappers(String file, String index,
                                                        Function<SeekableByteChannel, SeekableByteChannel> wrapper,
                                                        Function<SeekableByteChannel, SeekableByteChannel> indexWrapper) throws IOException, URISyntaxException {
        try(FileSystem fs = Jimfs.newFileSystem("test", Configuration.unix());
            final AbstractFeatureReader<VariantContext, ?> featureReader = getFeatureReader(file, index, wrapper,
                                                                                            indexWrapper,
                                                                                            new VCFCodec(),
                                                                                            fs)){
            Assert.assertTrue(featureReader.hasIndex());
            Assert.assertEquals(featureReader.iterator().toList().size(), 26);
            Assert.assertEquals(featureReader.query("1", 190, 210).toList().size(), 3);
            Assert.assertEquals(featureReader.query("2", 190, 210).toList().size(), 1);
        }
    }

    @DataProvider(name = "failsWithoutWrappers")
    public static Object[][] failsWithoutWrappers(){
        return new Object[][] {
                {MANGLED_VCF, MANGLED_VCF_INDEX},
                {VCF, CORRUPTED_VCF_INDEX},
                {VCF, MANGLED_VCF_INDEX},
                {MANGLED_VCF, VCF_INDEX},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX},
                {VCF_TABIX_BLOCK_GZIPPED, MANGLED_VCF_TABIX_INDEX},
                {MANGLED_VCF_TABIX_BLOCK_GZIPPED, VCF_TABIX_INDEX},
        };
    }

    @Test(dataProvider = "failsWithoutWrappers", expectedExceptions = {TribbleException.class, FileTruncatedException.class})
    public void testFailureIfNoWrapper(String file, String index) throws IOException, URISyntaxException {
        try(final FileSystem fs = Jimfs.newFileSystem("test", Configuration.unix());
            final FeatureReader<?> reader = getFeatureReader(file, index, null, null, new VCFCodec(), fs)){
            // should have exploded by now
        }
    }

    private static <T extends Feature> AbstractFeatureReader<T, ?> getFeatureReader(String vcf, String index,
                                                                                    Function<SeekableByteChannel, SeekableByteChannel> wrapper,
                                                                                    Function<SeekableByteChannel, SeekableByteChannel> indexWrapper,
                                                                                    FeatureCodec<T, ?> codec,
                                                                                    FileSystem fileSystem) throws IOException, URISyntaxException {
        final Path vcfInJimfs = TestUtils.getTribbleFileInJimfs(vcf, index, fileSystem);
        return AbstractFeatureReader.getFeatureReader(
                vcfInJimfs.toUri().toString(),
                null,
                codec,
                true,
                wrapper,
                indexWrapper);
    }

    /**
     * skip the first byte of a SeekableByteChannel
     */
    private static class SkippingByteChannel implements SeekableByteChannel{
        private final int toSkip;
        private final SeekableByteChannel input;

       private SkippingByteChannel(SeekableByteChannel input) {
           this.toSkip = 1;
           try {
               this.input = input;
               input.position(toSkip);
           } catch (final IOException e){
               throw new RuntimeException(e);
           }
       }

       @Override
        public boolean isOpen() {
            return input.isOpen();
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
           return input.read(dst);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public long position() throws IOException {
            return input.position() - toSkip;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition < 0 ){
                throw new RuntimeException("negative position not allowed");
            }
            return input.position( newPosition + toSkip);
        }

        @Override
        public long size() throws IOException {
            return input.size() - toSkip;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            return input.truncate(size + toSkip);
        }
    }

}
