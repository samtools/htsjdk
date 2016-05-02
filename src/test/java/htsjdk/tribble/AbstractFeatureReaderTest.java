package htsjdk.tribble;

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

import static org.testng.Assert.*;

/**
 * @author jacob
 * @date 2013-Apr-10
 */
public class AbstractFeatureReaderTest {

    final static String HTTP_INDEXED_VCF_PATH = "http://www.broadinstitute.org/~picard/testdata/ex2.vcf";
    final static String LOCAL_MIRROR_HTTP_INDEXED_VCF_PATH = VariantBaseTest.variantTestDataRoot + "ex2.vcf";

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

    @Test(enabled = true, dataProvider = "blockCompressedExtensionExtensionStrings")
    public void testBlockCompressionExtensionString(final String testString, final boolean expected) {
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testString), expected);
    }

    @Test(enabled = true, dataProvider = "blockCompressedExtensionExtensionStrings")
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

    @Test(enabled = true, dataProvider = "blockCompressedExtensionExtensionURIStrings")
    public void testBlockCompressionExtension(final String testURIString, final boolean expected) throws URISyntaxException {
        URI testURI = URI.create(testURIString);
        Assert.assertEquals(AbstractFeatureReader.hasBlockCompressedExtension(testURI), expected);
    }

}
