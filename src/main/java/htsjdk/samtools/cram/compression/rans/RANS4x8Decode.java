package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANS4x8Decode extends RANSDecode<RANS4x8Params> {

    private static final int RAW_BYTE_LENGTH = 4;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer uncompress(final ByteBuffer inBuffer, final RANS4x8Params params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        initializeRANSDecoder();
        // first byte of compressed stream gives order
        final RANSParams.ORDER order = RANSParams.ORDER.fromInt(inBuffer.get());

        // For RANS decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // compressed bytes length
        final int inSize = inBuffer.getInt();
        if (inSize != inBuffer.remaining() - RAW_BYTE_LENGTH) {
            throw new RuntimeException("Incorrect input length.");
        }

        // uncompressed bytes length
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

    private ByteBuffer uncompressOrder0Way4(final ByteBuffer inBuffer, final ByteBuffer outBuffer) {
        // read the frequency table. using the frequency table, set the values of RANSDecodingSymbols
        Frequencies4x8.readStatsOrder0(inBuffer, getD()[0], getDecodingSymbols()[0]);
        D04.uncompress(inBuffer, getD()[0], getDecodingSymbols()[0], outBuffer);

        return outBuffer;
    }

    private ByteBuffer uncompressOrder1Way4(final ByteBuffer in, final ByteBuffer outBuffer) {
        // read the frequency table. using the frequency table, set the values of RANSDecodingSymbols
        Frequencies4x8.readStatsOrder1(in, getD(), getDecodingSymbols());
        D14.uncompress(in, outBuffer, getD(), getDecodingSymbols());
        return outBuffer;
    }

}