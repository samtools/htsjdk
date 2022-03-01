package htsjdk.samtools.cram.compression.rans;

import htsjdk.utils.ValidationUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANS4x8Encode extends RANSEncode<RANS4x8Params> {
    private static final int ORDER_BYTE_LENGTH = 1;
    private static final int COMPRESSED_BYTE_LENGTH = 4;
    private static final int RAW_BYTE_LENGTH = 4;
    private static final int PREFIX_BYTE_LENGTH = ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH + RAW_BYTE_LENGTH;

    // streams smaller than this value don't have sufficient symbol context for ORDER-1 encoding,
    // so always use ORDER-0
    private static final int MINIMUM__ORDER_1_SIZE = 4;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);


    public ByteBuffer compress(final ByteBuffer inBuffer, final RANS4x8Params params) {
        final RANSParams.ORDER order= params.getOrder();
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        initializeRANSEncoder();
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
                throw new RuntimeException("Unknown rANS order: " + params.getOrder());
        }
    }

    private ByteBuffer compressOrder0Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);

        // move the output buffer ahead to the start of the frequency table (we'll come back and
        // write the output stream prefix at the end of this method)
        outBuffer.position(PREFIX_BYTE_LENGTH); // start of frequency table

        // get the normalised frequencies of the alphabets
        final int[] F = Frequencies4x8.calcFrequenciesOrder0(inBuffer);

        // using the normalised frequencies, set the RANSEncodingSymbols
        Frequencies4x8.buildSymsOrder0(F, getEncodingSymbols()[0]);

        final ByteBuffer cp = outBuffer.slice();

        // write Frequency table
        final int frequencyTableSize = Frequencies4x8.writeFrequenciesOrder0(cp, F);

        inBuffer.rewind();
        final int compressedBlobSize = E04.compress(inBuffer, getEncodingSymbols()[0], cp);

        // write the prefix at the beginning of the output buffer
        writeCompressionPrefix(RANSParams.ORDER.ZERO, outBuffer, inSize, frequencyTableSize, compressedBlobSize);
        return outBuffer;
    }

    private ByteBuffer compressOrder1Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);

        // move to start of frequency
        outBuffer.position(PREFIX_BYTE_LENGTH);

        // get normalized frequencies
        final int[][] F = Frequencies4x8.calcFrequenciesOrder1(inBuffer);

        // using the normalised frequencies, set the RANSEncodingSymbols
        Frequencies4x8.buildSymsOrder1(F, getEncodingSymbols());

        final ByteBuffer cp = outBuffer.slice();
        final int frequencyTableSize = Frequencies4x8.writeFrequenciesOrder1(cp, F);

        inBuffer.rewind();
        final int compressedBlobSize = E14.compress(inBuffer, getEncodingSymbols(), cp);

        // write the prefix at the beginning of the output buffer
        writeCompressionPrefix(RANSParams.ORDER.ONE, outBuffer, inSize, frequencyTableSize, compressedBlobSize);
        return outBuffer;
    }

    private static void writeCompressionPrefix(
            final RANSParams.ORDER order,
            final ByteBuffer outBuffer,
            final int inSize,
            final int frequencyTableSize,
            final int compressedBlobSize) {
        ValidationUtils.validateArg(order == RANSParams.ORDER.ONE || order == RANSParams.ORDER.ZERO,"unrecognized RANS order");
        outBuffer.limit(PREFIX_BYTE_LENGTH + frequencyTableSize + compressedBlobSize);

        // go back to the beginning of the stream and write the prefix values
        // write the (ORDER as a single byte at offset 0)
        outBuffer.put(0, (byte) (order == RANSParams.ORDER.ZERO ? 0 : 1));
        outBuffer.order(ByteOrder.LITTLE_ENDIAN);
        // move past the ORDER and write the compressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH, frequencyTableSize + compressedBlobSize);
        // move past the compressed size and write the uncompressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH, inSize);
        outBuffer.rewind();
    }

}