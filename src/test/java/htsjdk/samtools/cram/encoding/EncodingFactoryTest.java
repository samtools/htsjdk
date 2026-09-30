package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.CanonicalHuffmanByteArrayEncoding;
import htsjdk.samtools.cram.encoding.core.CanonicalHuffmanByteEncoding;
import htsjdk.samtools.cram.encoding.external.ByteArrayStopEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalByteArrayEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalIntegerEncoding;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingID;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EncodingFactoryTest extends HtsjdkTest {
    private static byte[] byteArrayLenParams() {
        return new ByteArrayLenEncoding(new ExternalIntegerEncoding(1), new ExternalByteArrayEncoding(2))
                .toSerializedEncodingParams();
    }

    private static byte[] byteArrayStopParams() {
        return new ByteArrayStopEncoding((byte) 0, 1).toSerializedEncodingParams();
    }

    @Test
    public void anIntSeriesRejectsAByteArrayEncoding() {
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> EncodingFactory.createCRAMEncoding(
                        DataSeriesType.INT, EncodingID.BYTE_ARRAY_LEN, byteArrayLenParams()));
    }

    @Test
    public void aLongSeriesRejectsAByteArrayEncoding() {
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> EncodingFactory.createCRAMEncoding(
                        DataSeriesType.LONG, EncodingID.BYTE_ARRAY_STOP, byteArrayStopParams()));
    }

    @Test
    public void aByteArraySeriesAcceptsHuffman() {
        final byte[] params =
                new CanonicalHuffmanByteEncoding(new byte[] {30}, new int[] {0}).toSerializedEncodingParams();
        final CRAMEncoding<byte[]> encoding =
                EncodingFactory.createCRAMEncoding(DataSeriesType.BYTE_ARRAY, EncodingID.HUFFMAN, params);
        Assert.assertTrue(encoding instanceof CanonicalHuffmanByteArrayEncoding);
    }
}
