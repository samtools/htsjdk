package htsjdk.tribble;

import htsjdk.samtools.util.Locatable;
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
            for (final Locatable feat : featureReader.iterator()) {
                localIterator.next();
                count++;
            }
            Assert.assertEquals(count, expectedCount);
        }
    }

}
