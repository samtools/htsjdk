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
        Assert.assertEquals(readsDecoderOptions.isDecodeEagerly(), false);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        readsDecoderOptions.setDecodeEagerly(true);
        Assert.assertEquals(readsDecoderOptions.isDecodeEagerly(), true);
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        //TODO: need a way to verify factory propagation
    }

    @Test
    public void testCacheFileBasedIndexes() {
        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions();
        Assert.assertEquals(readsDecoderOptions.isFileBasedIndexCached(), false);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        readsDecoderOptions.setFileBasedIndexCached(true);
        Assert.assertEquals(readsDecoderOptions.isFileBasedIndexCached(), true);
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        //TODO: need a way to verify factory propagation
    }

    @Test
    public void testDontMemoryMapIndexesIndexes() {
        final ReadsDecoderOptions readsDecoderOptions = new ReadsDecoderOptions();
        Assert.assertEquals(readsDecoderOptions.isMemoryMapIndexes(), true);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        readsDecoderOptions.setMemoryMapIndexes(false);
        Assert.assertEquals(readsDecoderOptions.isMemoryMapIndexes(), false);
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        //TODO: need a way to verify factory propagation
    }

}
