package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.IntervalList.IntervalListCodec;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;


public class TribbleIndexedFeatureReaderTest extends HtsjdkTest {

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
    public void testUnindexedVCF(final String testPath, final int expectedCount) throws IOException {
        final VCFCodec codec = new VCFCodec();
        try (final TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(testPath, codec, false)) {

            Assert.assertEquals(featureReader.iterator().stream().count(), expectedCount);
        }
    }

    @DataProvider()
    public Object[][] createIndexedFeatureFileStrings() {
        return new Object[][]{
                {TestUtils.DATA_DIR + "test.tabix.bed",  100000},
                {TestUtils.DATA_DIR + "test.tabix.bed",  100020},
        };
    }

    @Test(dataProvider = "createIndexedFeatureFileStrings", expectedExceptions = TribbleException.MalformedFeatureFile.class)
    public void testIndexedTribble(final String testPath, final int start) throws IOException {
        final BEDCodec codec = new BEDCodec();
        try (final TribbleIndexedFeatureReader<BEDFeature, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(testPath, codec, true)) {

            featureReader
                    .query("chr1", start, 100040)
                    .stream()
                    .count();
        }
    }



    @DataProvider()
    public Object[][] createIntervalFileStrings() {
        return new Object[][]{
                {new File(TestUtils.DATA_DIR, "interval_list/shortExample.interval_list"), 4}
        };
    }

    @Test(dataProvider = "createIntervalFileStrings")
    public void testUnIndexedIntervalList(final File testPath, final int expectedCount) throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(testPath.getAbsolutePath(), codec, false)) {
            Assert.assertEquals(featureReader.iterator().stream().count(), expectedCount);
        }
    }

    @Test(dataProvider = "createIntervalFileStrings", expectedExceptions = TribbleException.class)
    public void testUnIndexedIntervalListWithQuery(final File testPath, final int ignored) throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(testPath.getAbsolutePath(), codec, false)) {

            Assert.assertEquals(featureReader.query("1", 17032814, 17032814).stream().count(), 1);
        }
    }

    @Test(expectedExceptions = TribbleException.MalformedFeatureFile.class)
    public void testPoolyFormatedIntervalListWithQuery() throws IOException {
        final IntervalListCodec codec = new IntervalListCodec();
        final File testPath = new File(TestUtils.DATA_DIR, "interval_list/badExample.interval_list");
        try (final TribbleIndexedFeatureReader<Interval, LineIterator> featureReader =
                     new TribbleIndexedFeatureReader<>(testPath.getAbsolutePath(), codec, false)) {
            final CloseableTribbleIterator<Interval> iterator = featureReader.iterator();
            int numberOfRecords=0;
            while(iterator.hasNext()){
                iterator.next();
                numberOfRecords++;
            }
            Assert.assertEquals(numberOfRecords, 4);
        }
    }
}
