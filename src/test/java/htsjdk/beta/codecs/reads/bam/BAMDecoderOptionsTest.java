package htsjdk.beta.codecs.reads.bam;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.zip.InflaterFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BAMDecoderOptionsTest extends HtsjdkTest {

    @Test
    public void testUseAsyncIO() {
        final BAMDecoderOptions bamDecoderOptions = new BAMDecoderOptions();
        final boolean defaultUseAsyncIO = bamDecoderOptions.isAsyncIO();
        Assert.assertFalse(defaultUseAsyncIO);

        bamDecoderOptions.setAsyncIO(true);
        Assert.assertEquals(bamDecoderOptions.isAsyncIO(), true);
    }

    @Test
    public void testValidateCRCChecksums() {
        final BAMDecoderOptions bamDecoderOptions = new BAMDecoderOptions();
        final boolean defaultValidateCRCChecksums = bamDecoderOptions.isValidateCRCChecksums();
        Assert.assertFalse(defaultValidateCRCChecksums);

        bamDecoderOptions.setAsyncIO(true);
        Assert.assertEquals(bamDecoderOptions.isAsyncIO(), true);
    }

    @Test
    public void testInflaterFactory() {
        final BAMDecoderOptions bamDecoderOptions = new BAMDecoderOptions();
        final InflaterFactory inflaterFactory = new InflaterFactory();
        bamDecoderOptions.setInflaterFactory(inflaterFactory);
        // test reference equality
        Assert.assertTrue(bamDecoderOptions.getInflaterFactory() == inflaterFactory);
    }

}
