package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

public abstract class RANSDecode {
    private ArithmeticDecoder[] D;
    private RANSDecodingSymbol[][] decodingSymbols;

    // GETTERS
    protected ArithmeticDecoder[] getD() {
        return D;
    }

    protected RANSDecodingSymbol[][] getDecodingSymbols() {
        return decodingSymbols;
    }

    // This method assumes that inBuffer is already rewound.
    // It uncompresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the uncompressed data.
    public abstract ByteBuffer uncompress(final ByteBuffer inBuffer);

    // Lazy initialization of working memory for the decoder
    protected void initializeRANSDecoder() {
        if (D == null) {
            D = new ArithmeticDecoder[Constants.NUMBER_OF_SYMBOLS];
            for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
                D[i] = new ArithmeticDecoder();
            }
        } else {
            for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
                D[i].reset();
            }
        }
        if (decodingSymbols == null) {
            decodingSymbols = new RANSDecodingSymbol[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
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
    }

}