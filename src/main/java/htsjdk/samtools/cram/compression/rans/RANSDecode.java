package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

public abstract class RANSDecode<T extends RANSParams> {
    private ArithmeticDecoder[] D;
    private RANSDecodingSymbol[][] decodingSymbols;

    // GETTERS
    public ArithmeticDecoder[] getD() {
        return D;
    }

    public RANSDecodingSymbol[][] getDecodingSymbols() {
        return decodingSymbols;
    }

    abstract ByteBuffer uncompress(final ByteBuffer inBuffer, final T params);

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