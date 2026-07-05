package htsjdk.tribble;

import htsjdk.HtsjdkTest;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.example.ExampleBinaryCodec;
import htsjdk.tribble.readers.LineIterator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BinaryFeaturesTest extends HtsjdkTest {
    @DataProvider(name = "BinaryFeatureSources")
    public Object[][] createData1() {
        return new Object[][] {
            {Path.of(TestUtils.DATA_DIR + "test.bed"), new BEDCodec()},
            {Path.of(TestUtils.DATA_DIR + "bed/Unigene.sample.bed"), new BEDCodec()},
            {
                Path.of(TestUtils.DATA_DIR + "bed/NA12878.deletions.10kbp.het.gq99.hand_curated.hg19_fixed.bed"),
                new BEDCodec()
            },
        };
    }

    @Test(enabled = true, dataProvider = "BinaryFeatureSources")
    public void testBinaryCodec(final Path source, final FeatureCodec<Feature, LineIterator> codec) throws IOException {
        final Path tmpFile = Files.createTempFile("testBinaryCodec", ".binary.bed");
        ExampleBinaryCodec.convertToBinaryTest(source, tmpFile, codec);
        tmpFile.toFile().deleteOnExit();

        final FeatureReader<Feature> originalReader =
                AbstractFeatureReader.getFeatureReader(source.toAbsolutePath().toString(), codec, false);
        final FeatureReader<Feature> binaryReader = AbstractFeatureReader.getFeatureReader(
                tmpFile.toAbsolutePath().toString(), new ExampleBinaryCodec(), false);

        // make sure the header is what we expect
        final List<String> header = (List<String>) binaryReader.getHeader();
        Assert.assertEquals(header.size(), 1, "We expect exactly one header line");
        Assert.assertEquals(header.get(0), ExampleBinaryCodec.HEADER_LINE, "Failed to read binary header line");

        final Iterator<Feature> oit = originalReader.iterator();
        final Iterator<Feature> bit = binaryReader.iterator();
        while (oit.hasNext()) {
            final Feature of = oit.next();

            Assert.assertTrue(
                    bit.hasNext(), "Original iterator has items, but there's no items left in binary iterator");
            final Feature bf = bit.next();

            Assert.assertEquals(bf.getContig(), of.getContig(), "Chr not equal between original and binary encoding");
            Assert.assertEquals(bf.getStart(), of.getStart(), "Start not equal between original and binary encoding");
            Assert.assertEquals(bf.getEnd(), of.getEnd(), "End not equal between original and binary encoding");
        }
        Assert.assertTrue(!bit.hasNext(), "Original iterator is done, but there's still some data in binary iterator");

        originalReader.close();
        binaryReader.close();
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testGetTabixFormatThrowsException() {
        new ExampleBinaryCodec().getTabixFormat();
    }
}
