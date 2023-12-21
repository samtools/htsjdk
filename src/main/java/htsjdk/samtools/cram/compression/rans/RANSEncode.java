package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

public abstract class RANSEncode<T extends RANSParams> {
    private RANSEncodingSymbol[][] encodingSymbols;

    // Getter
    protected RANSEncodingSymbol[][] getEncodingSymbols() {
        return encodingSymbols;
    }

    // This method assumes that inBuffer is already rewound.
    // It compresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the compressed data.
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

    protected void buildSymsOrder0(final int[] frequencies) {
        updateEncodingSymbols(frequencies, getEncodingSymbols()[0]);
    }

    protected void buildSymsOrder1(final int[][] frequencies) {
        final RANSEncodingSymbol[][] encodingSymbols = getEncodingSymbols();
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            updateEncodingSymbols(frequencies[i], encodingSymbols[i]);
        }
    }

    private void updateEncodingSymbols(int[] frequencies, RANSEncodingSymbol[] encodingSymbols) {
        int cumulativeFreq = 0;
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (frequencies[symbol] != 0) {
                //For each symbol, set start = cumulative frequency and freq = frequencies[symbol]
                encodingSymbols[symbol].set(cumulativeFreq, frequencies[symbol], Constants.TOTAL_FREQ_SHIFT);
                cumulativeFreq += frequencies[symbol];
            }
        }
    }

}