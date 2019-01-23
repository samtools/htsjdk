package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
import jdk.internal.org.objectweb.asm.tree.VarInsnNode;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.function.Function;


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

    @DataProvider(name = "1111")
    public Object[][] createTribbleINdexedFile() {
        return new Object[][]{
                {new File("src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf"), new VCFCodec()}
            };
        }

    @Test(dataProvider = "1111")
    public void testBug1111(final File inputFile, final FeatureCodec codec) {
        try {
            TribbleIndexedFeatureReader obj = new TribbleIndexedFeatureReader(inputFile.getAbsolutePath(), null, codec, true);
        } catch (java.io.IOException e) {
            System.out.print("lol");
        };
    }

}
