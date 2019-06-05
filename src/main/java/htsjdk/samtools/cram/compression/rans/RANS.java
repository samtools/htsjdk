package htsjdk.samtools.cram.compression.rans;

import htsjdk.utils.ValidationUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RANS {

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

    public static final int NUMBER_OF_SYMBOLS = 256;

    // working variables used by the encoder and decoder; initialize them lazily since
    // they consist of lots of small objects, and we don't want to instantiate them
    // until we actually use them
    private ArithmeticDecoder[] D;
    private RANSDecodingSymbol[][] decodingSymbols;
    private RANSEncodingSymbol[][] encodingSymbols;

    // Lazy initialization of working memory for the encoder/decoder
    private void initializeRANSCoder() {
        if (D == null) {
            D = new ArithmeticDecoder[NUMBER_OF_SYMBOLS];
            for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
                D[i] = new ArithmeticDecoder();
            }
        } else {
            for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
                D[i].reset();
            }
        }
        if (decodingSymbols == null) {
            decodingSymbols = new RANSDecodingSymbol[NUMBER_OF_SYMBOLS][NUMBER_OF_SYMBOLS];
            for (int i = 0; i < decodingSymbols.length; i++) {
                for (int j = 0; j < decodingSymbols[i].length; j++) {
                    decodingSymbols[i][j] = new RANSDecodingSymbol();
                }
            }
        } else {
            for (int i = 0; i < decodingSymbols.length; i++) {
                for (int j = 0; j < decodingSymbols[i].length; j++) {
                    decodingSymbols[i][j].set(0, 0);
                }
            }
        }
        if (encodingSymbols == null) {
            encodingSymbols = new RANSEncodingSymbol[NUMBER_OF_SYMBOLS][NUMBER_OF_SYMBOLS];
            for (int i = 0; i < encodingSymbols.length; i++) {
                for (int j = 0; j < encodingSymbols[i].length; j++) {
                    encodingSymbols[i][j] = new RANSEncodingSymbol();
                }
            }
        } else {
            for (int i = 0; i < encodingSymbols.length; i++) {
                for (int j = 0; j < encodingSymbols[i].length; j++) {
                    encodingSymbols[i][j].reset();
                }
            }
        }
    }

    public ByteBuffer uncompress(final ByteBuffer inBuffer) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        initializeRANSCoder();

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

    public ByteBuffer compress(final ByteBuffer inBuffer, final ORDER order) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        initializeRANSCoder();

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

    private ByteBuffer compressOrder0Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);

        // move the output buffer ahead to the start of the frequency table (we'll come back and
        // write the output stream prefix at the end of this method)
        outBuffer.position(PREFIX_BYTE_LENGTH); // start of frequency table

        final int[] F = Frequencies.calcFrequenciesOrder0(inBuffer);
        Frequencies.buildSymsOrder0(F, encodingSymbols[0]);

        final ByteBuffer cp = outBuffer.slice();
        final int frequencyTableSize = Frequencies.writeFrequenciesOrder0(cp, F);

        inBuffer.rewind();
        final int compressedBlobSize = E04.compress(inBuffer, encodingSymbols[0], cp);

        // rewind and write the prefix
        writeCompressionPrefix(ORDER.ZERO, outBuffer, inSize, frequencyTableSize, compressedBlobSize);
        return outBuffer;
    }

    private ByteBuffer compressOrder1Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);

        // move to start of frequency
        outBuffer.position(PREFIX_BYTE_LENGTH);

        final int[][] F = Frequencies.calcFrequenciesOrder1(inBuffer);
        Frequencies.buildSymsOrder1(F, encodingSymbols);

        final ByteBuffer cp = outBuffer.slice();
        final int frequencyTableSize = Frequencies.writeFrequenciesOrder1(cp, F);

        inBuffer.rewind();
        final int compressedBlobSize = E14.compress(inBuffer, encodingSymbols, cp);

        // rewind and write the prefix
        writeCompressionPrefix(ORDER.ONE, outBuffer, inSize, frequencyTableSize, compressedBlobSize);
        return outBuffer;
    }

    private ByteBuffer uncompressOrder0Way4(final ByteBuffer inBuffer, final ByteBuffer outBuffer) {
        Frequencies.readStatsOrder0(inBuffer, D[0], decodingSymbols[0]);
        D04.uncompress(inBuffer, D[0], decodingSymbols[0], outBuffer);

        return outBuffer;
    }

    private ByteBuffer uncompressOrder1Way4(final ByteBuffer in, final ByteBuffer outBuffer) {
        Frequencies.readStatsOrder1(in, D, decodingSymbols);
        D14.uncompress(in, outBuffer, D, decodingSymbols);
        return outBuffer;
    }

    private static ByteBuffer allocateOutputBuffer(final int inSize) {
        // This calculation is identical to the one in samtools rANS_static.c
        // Presumably the frequency table (always big enough for order 1) = 257*257, then * 3 for each entry
        // (byte->symbol, 2 bytes -> scaled frequency), + 9 for the header (order byte, and 2 int lengths
        // for compressed/uncompressed lengths) ? Plus additional 5% for..., for what ???
        final int compressedSize = (int) (1.05 * inSize + 257 * 257 * 3 + 9);
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
        // write the (ORDER as a single byte at offset 0)
        outBuffer.put(0, (byte) (order == ORDER.ZERO ? 0 : 1));
        outBuffer.order(ByteOrder.LITTLE_ENDIAN);
        // move past the ORDER and write the compressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH, frequencyTableSize + compressedBlobSize);
        // move past the compressed size and write the uncompressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH, inSize);
        outBuffer.rewind();
    }

}
