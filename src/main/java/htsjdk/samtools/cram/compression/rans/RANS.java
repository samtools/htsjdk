package htsjdk.samtools.cram.compression.rans;

import htsjdk.utils.ValidationUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANS {

    public enum ORDER {
        ZERO, ONE;

        public static ORDER fromInt(final int orderValue) {
            try {
                return ORDER.values()[orderValue];
            } catch (final ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Unknown rANS order: " + orderValue);
            }
        }
    }

    // A compressed rANS stream consists of a prefix containing 3 values, followed by the compressed data block:
    // byte - order of the codec (0 or 1)
    // int - total compressed size of the frequency table and compressed content
    // int - total size of the raw/uncompressed content
    // byte[] - frequency table (RLE)
    // byte[] - compressed data

    private static final int ORDER_BYTE_LENGTH = 1;
    private static final int COMPRESSED_BYTE_LENGTH = 4;
    private static final int RAW_BYTE_LENGTH = 4;
    private static final int PREFIX_BYTE_LENGTH = ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH + RAW_BYTE_LENGTH;

    // streams smaller than this value don't have sufficient symbol context for ORDER-1 encoding,
    // so always use ORDER-0
    private static final int MINIMUM__ORDER_1_SIZE = 4;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public static ByteBuffer uncompress(final ByteBuffer inBuffer) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        final ORDER order = ORDER.fromInt(inBuffer.get());

        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final int inSize = inBuffer.getInt();
        if (inSize != inBuffer.remaining() - RAW_BYTE_LENGTH) {
            throw new RuntimeException("Incorrect input length.");
        }
        final int outSize = inBuffer.getInt();
        final ByteBuffer outBuffer = ByteBuffer.allocate(outSize);

        switch (order) {
            case ZERO:
                return uncompressOrder0Way4(inBuffer, outBuffer);

            case ONE:
                return uncompressOrder1Way4(inBuffer, outBuffer);

            default:
                throw new RuntimeException("Unknown rANS order: " + order);
        }
    }

    public static ByteBuffer compress(final ByteBuffer inBuffer, final ORDER order) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        if (inBuffer.remaining() < MINIMUM__ORDER_1_SIZE) {
            // ORDER-1 encoding of less than 4 bytes is not permitted, so just use ORDER-0
            return compressOrder0Way4(inBuffer);
        }

        switch (order) {
            case ZERO:
                return compressOrder0Way4(inBuffer);

            case ONE:
                return compressOrder1Way4(inBuffer);

            default:
                throw new RuntimeException("Unknown rANS order: " + order);
        }
    }

    private static ByteBuffer compressOrder0Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);

        // move the output buffer ahead to the start of the frequency table (we'll come back and
        // write the output stream prefix at the end of this method)
        outBuffer.position(PREFIX_BYTE_LENGTH); // start of frequency table

        final int[] F = Frequencies.calcFrequenciesOrder0(inBuffer);
        final RANSEncodingSymbol[] syms = Frequencies.buildSymsOrder0(F);

        final ByteBuffer cp = outBuffer.slice();
        final int frequencyTableSize = Frequencies.writeFrequenciesOrder0(cp, F);

        inBuffer.rewind();
        final int compressedBlobSize = E04.compress(inBuffer, syms, cp);

        // rewind and write the prefix
        writeCompressionPrefix(ORDER.ZERO, outBuffer, inSize, frequencyTableSize, compressedBlobSize);
        return outBuffer;
    }

    private static ByteBuffer compressOrder1Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);
        // move to start of frequency
        outBuffer.position(PREFIX_BYTE_LENGTH);

        final int[][] F = Frequencies.calcFrequenciesOrder1(inBuffer);
        final RANSEncodingSymbol[][] syms = Frequencies.buildSymsOrder1(F);

        final ByteBuffer cp = outBuffer.slice();
        final int frequencyTableSize = Frequencies.writeFrequenciesOrder1(cp, F);

        inBuffer.rewind();
        final int compressedBlobSize = E14.compress(inBuffer, syms, cp);

        // rewind and write the prefix
        writeCompressionPrefix(ORDER.ONE, outBuffer, inSize, frequencyTableSize, compressedBlobSize);
        return outBuffer;
    }

    private static ByteBuffer uncompressOrder0Way4(final ByteBuffer inBuffer, final ByteBuffer outBuffer) {
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final ArithmeticDecoder D = new ArithmeticDecoder();
        final RANSDecodingSymbol[] syms = new RANSDecodingSymbol[256];
        for (int i = 0; i < syms.length; i++) {
            syms[i] = new RANSDecodingSymbol();
        }

        Frequencies.readStatsOrder0(inBuffer, D, syms);
        D04.uncompress(inBuffer, D, syms, outBuffer);

        return outBuffer;
    }

    private static ByteBuffer uncompressOrder1Way4(final ByteBuffer in, final ByteBuffer outBuffer) {

        final ArithmeticDecoder[] D = new ArithmeticDecoder[256];
        for (int i = 0; i < 256; i++) {
            D[i] = new ArithmeticDecoder();
        }
        final RANSDecodingSymbol[][] syms = new RANSDecodingSymbol[256][256];
        for (int i = 0; i < syms.length; i++) {
            for (int j = 0; j < syms[i].length; j++) {
                syms[i][j] = new RANSDecodingSymbol();
            }
        }
        Frequencies.readStatsOrder1(in, D, syms);

        D14.uncompress(in, outBuffer, D, syms);

        return outBuffer;
    }

    // TODO: Allocates an output buffer to hold the result of compressing a stream of a given input size
    // TODO: on an inSize of 3, ths allocates 198,154 bytes (used for 256*256 symbol freq table ???)
    // TODO: what is (1.05 * inSize + 257 * 257 * 3 + 4) derived from ?
    private static ByteBuffer allocateOutputBuffer(final int inSize) {
        final int compressedSize = (int) (1.05 * inSize + 257 * 257 * 3 + 4);
        final ByteBuffer outputBuffer = ByteBuffer.allocate(compressedSize);
        if (outputBuffer.remaining() < compressedSize) {
            throw new RuntimeException("Failed to allocate sufficient buffer size for RANS coder.");
        }
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return outputBuffer;
    }

    private static void writeCompressionPrefix(
            final ORDER order,
            final ByteBuffer outBuffer,
            final int inSize,
            final int frequencyTableSize,
            final int compressedBlobSize) {
        ValidationUtils.validateArg(order == ORDER.ONE || order == ORDER.ZERO,"unrecognized RANS order");
        outBuffer.limit(PREFIX_BYTE_LENGTH + frequencyTableSize + compressedBlobSize);

        // go back to the beginning of the stream and write the prefix values
        // write the (ORDER as a single byte at offset 0
        outBuffer.put(0, (byte) (order == ORDER.ZERO ? 0 : 1));
        outBuffer.order(ByteOrder.LITTLE_ENDIAN);
        // move past the ORDER and write the compressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH, frequencyTableSize + compressedBlobSize);
        // move past the compressed size and write the uncompressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH, inSize);
        outBuffer.rewind();
    }

}
