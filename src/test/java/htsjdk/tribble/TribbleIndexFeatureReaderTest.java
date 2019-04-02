package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.IntervalList.IntervalListCodec;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
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

            Assert.assertEquals(featureReader.iterator().stream().count(), expectedCount);
        }
    }


    @DataProvider()
    public Object[][] createIntervalFileStrings() {
        return new Object[][]{
                {new File(TestUtils.DATA_DIR, "interval_list/shortExample.interval_list"), 4}
        };
    }

    @Test(dataProvider = "createFeatureFileStrings")
    public void testIndexedIntervalList(final File testPath, final int expectedCount) throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(testPath.getAbsolutePath(), codec, false)) {
            Assert.assertEquals(featureReader.iterator().stream().count(), expectedCount);
        }
    }

    @Test(dataProvider = "createFeatureFileStrings", expectedExceptions = TribbleException.class)
    public void testIndexedIntervalListWithQuery(final File testPath, final int ignored) throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(testPath.getAbsolutePath(), codec, false)) {

            Assert.assertEquals(featureReader.query("1", 17032814, 17032814).stream().count(), 1);
        }
    }
}
