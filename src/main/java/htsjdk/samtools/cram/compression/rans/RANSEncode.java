package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class RANSEncode<T extends RANSParams> {
    private RANSEncodingSymbol[][] encodingSymbols;

    // Getter
    protected RANSEncodingSymbol[][] getEncodingSymbols() {
        return encodingSymbols;
    }

    public abstract ByteBuffer compress(final ByteBuffer inBuffer, final T params);

    // Lazy initialization of working memory for the encoder
    protected void initializeRANSEncoder() {
        if (encodingSymbols == null) {
            encodingSymbols = new RANSEncodingSymbol[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
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

    protected ByteBuffer allocateOutputBuffer(final int inSize) {
        // TODO: This should vary depending on the RANS type and order
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

}