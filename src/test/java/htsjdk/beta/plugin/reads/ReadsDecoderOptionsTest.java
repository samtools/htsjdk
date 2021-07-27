package htsjdk.beta.plugin.reads;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.reads.ReadsCodecUtils;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ReadsDecoderOptionsTest extends HtsjdkTest {
    //    private Function<SeekableByteChannel, SeekableByteChannel> readsChannelTransformer;
    //    private Function<SeekableByteChannel, SeekableByteChannel> indexChannelTransformer;
    //    private BAMDecoderOptions bamDecoderOptions         = new BAMDecoderOptions();
    //    private CRAMDecoderOptions cramDecoderOptions       = new CRAMDecoderOptions();

    @Test
    public void testValidationStringency() {
        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions();
        Assert.assertEquals(readsDecoderOptions.getValidationStringency(), ValidationStringency.STRICT);

        readsDecoderOptions.setValidationStringency(ValidationStringency.SILENT);
        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        Assert.assertEquals(samReaderFactory.validationStringency(), ValidationStringency.SILENT);
    }

    @Test
    public void testEagerlyDecode() {
        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions();
        Assert.assertEquals(readsDecoderOptions.isEagerlyDecode(), false);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        readsDecoderOptions.setEagerlyDecode(true);
        Assert.assertEquals(readsDecoderOptions.isEagerlyDecode(), true);
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        //TODO: need a way to verify factory propagation
    }

    @Test
    public void testCacheFileBasedIndexes() {
        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions();
        Assert.assertEquals(readsDecoderOptions.isCacheFileBasedIndexes(), false);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        readsDecoderOptions.setCacheFileBasedIndexes(true);
        Assert.assertEquals(readsDecoderOptions.isCacheFileBasedIndexes(), true);
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        //TODO: need a way to verify factory propagation
    }

    @Test
    public void testDontMemoryMapIndexesIndexes() {
        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions();
        Assert.assertEquals(readsDecoderOptions.isDontMemoryMapIndexes(), false);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        readsDecoderOptions.setDontMemoryMapIndexes(true);
        Assert.assertEquals(readsDecoderOptions.isDontMemoryMapIndexes(), true);
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        //TODO: need a way to verify factory propagation
    }

}
