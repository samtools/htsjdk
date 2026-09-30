package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.core.huffmanUtils.HuffmanCanoncialCodeGenerator;
import htsjdk.samtools.cram.encoding.core.huffmanUtils.HuffmanParams;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

/**
 * Encode byte arrays by writing each byte with a Canonical Huffman code, in the core block.
 */
final class CanonicalHuffmanByteArrayCodec extends CoreCodec<byte[]> {
    private final HuffmanCanoncialCodeGenerator<Byte> helper;

    /**
     * @param coreBlockInputStream the input bitstream to read from
     * @param coreBlockOutputStream the output bitstream to write to
     * @param huffmanParams the code for a single byte
     */
    CanonicalHuffmanByteArrayCodec(
            final BitInputStream coreBlockInputStream,
            final BitOutputStream coreBlockOutputStream,
            final HuffmanParams<Byte> huffmanParams) {
        super(coreBlockInputStream, coreBlockOutputStream);
        helper = new HuffmanCanoncialCodeGenerator<>(huffmanParams);
    }

    @Override
    public byte[] read() {
        throw new RuntimeException("Cannot read byte array of unknown length.");
    }

    @Override
    public byte[] read(final int length) {
        final byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = helper.read(coreBlockInputStream);
        }
        return bytes;
    }

    @Override
    public void write(final byte[] bytes) {
        for (final byte value : bytes) {
            helper.write(coreBlockOutputStream, value);
        }
    }
}
