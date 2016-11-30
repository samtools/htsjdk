package htsjdk.tribble.index;

import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.index.linear.LinearIndex;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.tribble.util.TabixUtils;
import htsjdk.variant.vcf.VCFCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class IndexTest {
    private final static String CHR = "1";
    private final static File MassiveIndexFile = new File(TestUtils.DATA_DIR + "Tb.vcf.idx");

    @DataProvider(name = "StartProvider")
    public Object[][] makeStartProvider() {
        List<Object[]> tests = new ArrayList<>();

//        for ( int mid = 0; mid <= end; mid += 1000000 ) {
//            tests.add(new Object[]{0, mid, mid+1000000, end});
//        }

        tests.add(new Object[]{1226943, 1226943, 1226943, 2000000});

        return tests.toArray(new Object[][]{});
    }

    @Test(dataProvider = "StartProvider")
    public void testMassiveQuery(final int start, final int mid, final int mid2, final int end) throws IOException {
        LinearIndex index = (LinearIndex)IndexFactory.loadIndex(MassiveIndexFile.getAbsolutePath());

        final List<Block> leftBlocks = index.getBlocks(CHR, start, mid);
        final List<Block> rightBlocks = index.getBlocks(CHR, mid2, end); // gap must be big to avoid overlaps
        final List<Block> allBlocks = index.getBlocks(CHR, start, end);

        final long leftSize = leftBlocks.isEmpty() ? 0 : leftBlocks.get(0).getSize();
        final long rightSize = rightBlocks.isEmpty() ? 0 : rightBlocks.get(0).getSize();
        final long allSize = allBlocks.isEmpty() ? 0 : allBlocks.get(0).getSize();

        Assert.assertTrue(leftSize >= 0, "Expected leftSize to be positive " + leftSize);
        Assert.assertTrue(rightSize >= 0, "Expected rightSize to be positive " + rightSize);
        Assert.assertTrue(allSize >= 0, "Expected allSize to be positive " + allSize);

        Assert.assertTrue(allSize >= Math.max(leftSize,rightSize), "Expected size of joint query " + allSize + " to be at least >= max of left " + leftSize + " and right queries " + rightSize);
    }


    @DataProvider(name = "writeIndexData")
    public Object[][] writeIndexData() {
        return new Object[][]{
                {new File("src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf"), IndexFactory.IndexType.LINEAR, new VCFCodec()},
                {new File("src/test/resources/htsjdk/tribble/tabix/testTabixIndex.vcf.gz"), IndexFactory.IndexType.TABIX, new VCFCodec()},
                {new File("src/test/resources/htsjdk/tribble/test.bed"), IndexFactory.IndexType.LINEAR, new BEDCodec()}
        };
    }

    private final static OutputStream nullOutputStrem = new OutputStream() {
        @Override
        public void write(int b) throws IOException { }
    };

    @Test(dataProvider = "writeIndexData")
    public void testWriteIndex(final File inputFile, final IndexFactory.IndexType type, final  FeatureCodec codec) throws Exception {
        // temp index file for this test
        final File tempIndex = File.createTempFile("index", (type == IndexFactory.IndexType.TABIX) ? TabixUtils.STANDARD_INDEX_EXTENSION : Tribble.STANDARD_INDEX_EXTENSION);
        tempIndex.delete();
        tempIndex.deleteOnExit();
        // create the index
        final Index index = IndexFactory.createIndex(inputFile, codec, type);
        Assert.assertFalse(tempIndex.exists());
        // write the index to a file
        index.write(tempIndex);
        Assert.assertTrue(tempIndex.exists());
        // load the generated index
        final Index loadedIndex = IndexFactory.loadIndex(tempIndex.getAbsolutePath());
        // tess that the sequences and properties are the same
        Assert.assertEquals(loadedIndex.getSequenceNames(), index.getSequenceNames());
        Assert.assertEquals(loadedIndex.getProperties(), index.getProperties());
        // test that write to a stream does not blows ip
        index.write(new LittleEndianOutputStream(nullOutputStrem));
    }

}
