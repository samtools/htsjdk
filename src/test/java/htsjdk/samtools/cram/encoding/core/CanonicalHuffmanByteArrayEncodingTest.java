package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CanonicalHuffmanByteArrayEncodingTest extends HtsjdkTest {
    private static CanonicalHuffmanByteEncoding byteEncodingOf(final byte[] symbols, final int[] bitLengths) {
        return new CanonicalHuffmanByteEncoding(symbols, bitLengths);
    }

    /** Writes the arrays with a codec for the given code and returns the core block bytes they made. */
    private static byte[] writeToCoreBlock(final CanonicalHuffmanByteEncoding byteEncoding, final byte[][] arrays)
            throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final DefaultBitOutputStream bos = new DefaultBitOutputStream(baos)) {
            final CanonicalHuffmanByteArrayCodec codec =
                    new CanonicalHuffmanByteArrayCodec(null, bos, byteEncoding.getHuffmanParams());
            for (final byte[] array : arrays) {
                codec.write(array);
            }
            bos.flush();
            return baos.toByteArray();
        }
    }

    /** Reads back arrays of the given lengths from the core block bytes. */
    private static byte[][] readFromCoreBlock(
            final CanonicalHuffmanByteEncoding byteEncoding, final byte[] coreBlock, final int[] lengths)
            throws IOException {
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(coreBlock);
                final DefaultBitInputStream bis = new DefaultBitInputStream(bais)) {
            final CanonicalHuffmanByteArrayCodec codec =
                    new CanonicalHuffmanByteArrayCodec(bis, null, byteEncoding.getHuffmanParams());
            final byte[][] arrays = new byte[lengths.length][];
            for (int i = 0; i < lengths.length; i++) {
                arrays[i] = codec.read(lengths[i]);
            }
            return arrays;
        }
    }

    @Test
    public void serializedParamsRoundTrip() {
        final CanonicalHuffmanByteArrayEncoding encoding = new CanonicalHuffmanByteArrayEncoding(
                byteEncodingOf(new byte[] {10, 20, 30, 40}, new int[] {1, 2, 3, 3}));
        final byte[] serialized = encoding.toSerializedEncodingParams();
        Assert.assertEquals(
                CanonicalHuffmanByteArrayEncoding.fromSerializedEncodingParams(serialized)
                        .toSerializedEncodingParams(),
                serialized);
    }

    @Test
    public void writtenArraysReadBack() throws IOException {
        final CanonicalHuffmanByteEncoding byteEncoding =
                byteEncodingOf(new byte[] {10, 20, 30, 40}, new int[] {1, 2, 3, 3});
        final byte[][] arrays = {{10, 20, 30, 40, 10}, {}, {40}, {30, 30, 30, 10, 10, 20}};

        final byte[] coreBlock = writeToCoreBlock(byteEncoding, arrays);

        final int[] lengths = {5, 0, 1, 6};
        Assert.assertEquals(readFromCoreBlock(byteEncoding, coreBlock, lengths), arrays);
    }

    @Test
    public void aSingleSymbolCodeReadsArraysWithoutBits() throws IOException {
        final CanonicalHuffmanByteEncoding byteEncoding = byteEncodingOf(new byte[] {30}, new int[] {0});
        final byte[][] arrays = {{30, 30, 30}, {}, {30}};

        final byte[] coreBlock = writeToCoreBlock(byteEncoding, arrays);

        Assert.assertEquals(coreBlock.length, 0);
        Assert.assertEquals(readFromCoreBlock(byteEncoding, coreBlock, new int[] {3, 0, 1}), arrays);
    }
}
