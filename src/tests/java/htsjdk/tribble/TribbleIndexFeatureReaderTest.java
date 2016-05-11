package htsjdk.tribble;

import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.TestUtils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.testng.Assert.assertEquals;


public class TribbleIndexFeatureReaderTest {

    @DataProvider(name = "extensionURIStrings")
    public Object[][] createBlockCompressedExtensionURIs() {
        return new Object[][]{
                {"testzip.gz", true},
                {"testzip.GZ", true},
                {"testzip.gZ", true},
                {"testzip.Gz", true},

                {"test", false},
                {"test.gzip", false},
                {"test.bgz", false},
                {"test.bgzf", false},
                {"test.bzip2", false},

                {"file://testzip.gz", true},
                {"file://apath/testzip.gz", true},

                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gz", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.GZ", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gzip", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgz", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgzf", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bzip2", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877", false},

                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gz?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.GZ?alt=media", true},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gzip?alt=media", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgz?alt=media", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bgzf?alt=media", false},
                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.bzip2?alt=media", false},

                {"ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands.hg18.gz", true},
                {"ftp://ftp.broadinstitute.org/distribution/igv/TEST/cpgIslands.hg18.bed", false},

                {"https://www.googleapis.com/download/storage/v1/b/deflaux-public-test/o/NA12877.vcf.gz", true},
        };
    }

    @Test(enabled = true, dataProvider = "extensionURIStrings")
    public void testGZExtension(final String testString, final boolean expected) throws URISyntaxException {
        Assert.assertEquals(TribbleIndexedFeatureReader.isGZIPPath(testString), expected);
    }

    @DataProvider(name = "featureFileStrings")
    public Object[][] createFeatureFileStrings() {
        return new Object[][]{
                {TestUtils.DATA_DIR + "test.vcf", 5},
                {TestUtils.DATA_DIR + "test.vcf.gz", 5}
        };
    }

    @Test(dataProvider = "featureFileStrings")
    public void testIndexedGZIPVCF(final String testPath, final int expectedCount) throws IOException {
        final VCFCodec codec = new VCFCodec();
        try (final TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader =
                new TribbleIndexedFeatureReader(testPath, codec, false)) {
            final CloseableTribbleIterator<VariantContext> localIterator = featureReader.iterator();
            int count = 0;
            for (final Feature feat : featureReader.iterator()) {
                localIterator.next();
                count++;
            }
            Assert.assertEquals(count, expectedCount);
        }
    }

}
