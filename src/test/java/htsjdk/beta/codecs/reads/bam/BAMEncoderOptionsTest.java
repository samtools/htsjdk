package htsjdk.beta.codecs.reads.bam;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.AbstractAsyncWriter;
import htsjdk.samtools.util.zip.DeflaterFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BAMEncoderOptionsTest extends HtsjdkTest {

    @Test
    public void testDeflaterFactory() {
        final BAMEncoderOptions bamEncoderOptions = new BAMEncoderOptions();
        final DeflaterFactory deflaterFactory = new DeflaterFactory();
        bamEncoderOptions.setDeflaterFactory(deflaterFactory);
        // test reference equality
        Assert.assertTrue(bamEncoderOptions.getDeflaterFactory() == deflaterFactory);
    }

    @Test
    public void testUseAsyncIO() {
        final BAMEncoderOptions bamEncoderOptions = new BAMEncoderOptions();
        final boolean defaultUseAsyncIO = bamEncoderOptions.isUseAsyncIo();
        Assert.assertFalse(defaultUseAsyncIO);

        bamEncoderOptions.setUseAsyncIo(true);
        Assert.assertEquals(bamEncoderOptions.isUseAsyncIo(), true);

    }

    @Test
    public void testAsyncOutputBufferSize() {
        final int DEFAULT_ASYNC_BUFFER_SIZE = AbstractAsyncWriter.DEFAULT_QUEUE_SIZE;
        final int TEST_BUFFER_SIZE = 1024;

        final BAMEncoderOptions bamEncoderOptions = new BAMEncoderOptions();
        final int defaultAsyncOutputBufferSize = bamEncoderOptions.getAsyncOutputBufferSize();
        Assert.assertEquals(defaultAsyncOutputBufferSize, DEFAULT_ASYNC_BUFFER_SIZE);

        Assert.assertNotEquals(defaultAsyncOutputBufferSize, TEST_BUFFER_SIZE);
        bamEncoderOptions.setAsyncOutputBufferSize(TEST_BUFFER_SIZE);
        Assert.assertEquals(bamEncoderOptions.getAsyncOutputBufferSize(), TEST_BUFFER_SIZE);
    }

    @Test
    public void testOutputBufferSize() {
        final int DEFAULT_OUTPUT_BUFFER_SIZE = Defaults.BUFFER_SIZE;
        final int TEST_OUTPUT_BUFFER_SIZE = 1024;

        final BAMEncoderOptions bamEncoderOptions = new BAMEncoderOptions();
        final int defaultOutputBufferSize = bamEncoderOptions.getOutputBufferSize();
        Assert.assertEquals(defaultOutputBufferSize, DEFAULT_OUTPUT_BUFFER_SIZE);

        Assert.assertNotEquals(defaultOutputBufferSize, TEST_OUTPUT_BUFFER_SIZE);
        bamEncoderOptions.setOutputBufferSize(TEST_OUTPUT_BUFFER_SIZE);
        Assert.assertEquals(bamEncoderOptions.getOutputBufferSize(), TEST_OUTPUT_BUFFER_SIZE);
    }

    @Test
    public void testCompressionLevel() {
        final int DEFAULT_COMPRESSION_LEVEL = Defaults.COMPRESSION_LEVEL;
        final int TEST_COMPRESSION_LEVEL = 2;

        final BAMEncoderOptions bamEncoderOptions = new BAMEncoderOptions();
        final int defaultCompresionLevel = bamEncoderOptions.getCompressionLevel();
        Assert.assertEquals(defaultCompresionLevel, DEFAULT_COMPRESSION_LEVEL);

        Assert.assertNotEquals(defaultCompresionLevel, TEST_COMPRESSION_LEVEL);
        bamEncoderOptions.setOutputBufferSize(TEST_COMPRESSION_LEVEL);
        Assert.assertEquals(bamEncoderOptions.getOutputBufferSize(), TEST_COMPRESSION_LEVEL);
    }

    @Test
    public void testMaxRecordInRam() {
        final int DEFAULT_MAX_RECORDS_IN_RAM = BAMEncoderOptions.DEAFULT_MAX_RECORDS_IN_RAM;
        final int TEST_RECORDS_IN_RAM = 2;

        final BAMEncoderOptions bamEncoderOptions = new BAMEncoderOptions();
        final int defaultRecordsInRAM = bamEncoderOptions.getMaxRecordsInRam();
        Assert.assertEquals(defaultRecordsInRAM, DEFAULT_MAX_RECORDS_IN_RAM);

        Assert.assertNotEquals(defaultRecordsInRAM, TEST_RECORDS_IN_RAM);
        bamEncoderOptions.setMaxRecordsInRam(TEST_RECORDS_IN_RAM);
        Assert.assertEquals(bamEncoderOptions.getMaxRecordsInRam(), TEST_RECORDS_IN_RAM);
    }

}
