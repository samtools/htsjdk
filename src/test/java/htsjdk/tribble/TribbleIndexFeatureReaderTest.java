package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;


public class TribbleIndexFeatureReaderTest extends HtsjdkTest {

    @DataProvider(name = "featureFileStrings")
    public Object[][] createFeatureFileStrings() {
        return new Object[][]{
                {TestUtils.DATA_DIR + "test.vcf", 5},
                {TestUtils.DATA_DIR + "test.vcf.gz", 5},
                {TestUtils.DATA_DIR + "test.vcf.bgz", 5},
                {TestUtils.DATA_DIR + "test with spaces.vcf", 5}
        };
    }

    @Test(dataProvider = "featureFileStrings")
    public void testIndexedGZIPVCF(final String testPath, final int expectedCount) throws IOException {
        final VCFCodec codec = new VCFCodec();
        try (final TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader =
                new TribbleIndexedFeatureReader<>(testPath, codec, false)) {
            final CloseableTribbleIterator<VariantContext> localIterator = featureReader.iterator();
            int count = 0;
            for (final Feature feat : featureReader.iterator()) {
                localIterator.next();
                count++;
            }
            Assert.assertEquals(count, expectedCount);
        }
    }

    @Test
    // This tests a large Unblocked GZipped vcf file which should fail to parse for a large input if it tries to open a BlockCompressedInputStream over the file
    public void testGZIPVCFNotTabix() throws IOException {
        final VCFCodec codec = new VCFCodec();
        try (final TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(TestUtils.DATA_DIR + "tabix/YRI.trio.2010_07.indel.sites.unBlocked.vcf.gz", codec, false)) {
            final CloseableTribbleIterator<VariantContext> localIterator = featureReader.iterator();
            int count = 0;
            for (final Feature feat : featureReader.iterator()) {
                localIterator.next();
                count++;
            }
            Assert.assertEquals(count, 12218);
        }
    }

}
