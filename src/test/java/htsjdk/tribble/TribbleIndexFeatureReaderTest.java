package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;
//import jdk.internal.org.objectweb.asm.tree.VarInsnNode;
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
    public void testIfIndexFileWasCreated() throws IOException {
        String inputFilePath = "src/test/resources/htsjdk/tribble/tabix/chimeric_test.vcf";
        try (TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader = new TribbleIndexedFeatureReader<>(inputFilePath, null,  new VCFCodec(), true)) {
            Assert.assertTrue(featureReader.hasIndex());

            final String indexFilePath = Tribble.indexFile("file//" + inputFilePath);
            final File indexFile = new File(indexFilePath);
            indexFile.delete();
        }
    }

    @Test
    public void testIfIndexFileWasRecreated() throws IOException {
        String inputFilePath = "src/test/resources/htsjdk/tribble/tabix/chimeric_test.vcf";
        long initialTime = 0;
        long modifiedTime = 0;
        try (TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader = new TribbleIndexedFeatureReader<>(inputFilePath, null,  new VCFCodec(), true)) {
            Assert.assertTrue(featureReader.hasIndex());
        }
            String initialIndexFilePath = Tribble.indexFile("file//" + inputFilePath);
            final File initialIndexFile = new File(initialIndexFilePath);
            initialTime = initialIndexFile.lastModified();

        try(TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader = new TribbleIndexedFeatureReader<>(inputFilePath, null,  new VCFCodec(), true)) {
            Assert.assertTrue(featureReader.hasIndex());
        }
            final String modifiedIndexFilePath = Tribble.indexFile("file//" + inputFilePath);
            final File modifiedIndexFile = new File(modifiedIndexFilePath);
            modifiedTime = modifiedIndexFile.lastModified();
            Assert.assertEquals(initialTime, modifiedTime);

            initialIndexFile.delete();
    }

    @Test
    public void testTryToReadCreatedIndexFile() throws  Exception {
        String inputFilePath = "src/test/resources/htsjdk/tribble/tabix/chimeric_test.vcf";
        try (TribbleIndexedFeatureReader<VariantContext, LineIterator> featureReader = new TribbleIndexedFeatureReader<>(inputFilePath, null,  new VCFCodec(), true)) {
            Assert.assertTrue(featureReader.hasIndex());
        }
        File inputFile = new File(inputFilePath + ".tbi");
        final TabixIndex index = new TabixIndex(inputFile);
        final File indexFile = File.createTempFile("TabixIndexTest.", TabixUtils.STANDARD_INDEX_EXTENSION);
        indexFile.deleteOnExit();
        final LittleEndianOutputStream los = new LittleEndianOutputStream(new BlockCompressedOutputStream(indexFile));
        index.write(los);
        los.close();
        final TabixIndex index2 = new TabixIndex(indexFile);
        Assert.assertEquals(index, index2);
        inputFile.deleteOnExit();
    }

}
