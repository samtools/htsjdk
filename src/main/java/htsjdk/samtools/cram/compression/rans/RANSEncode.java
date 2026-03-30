package htsjdk.samtools.cram.compression.rans;

/**
 * Abstract base class for rANS encoders (both 4x8 and Nx16). Holds the shared encoding
 * symbol matrix and provides helper methods for frequency-to-symbol setup.
 *
 * <p>The encoding symbol matrix is allocated once at construction and reused across
 * compress calls. Between calls, only the symbols that were actually used are reset.
 */
public abstract class RANSEncode<T extends RANSParams> {
    private final RANSEncodingSymbol[][] encodingSymbols;

    protected RANSEncode() {
        encodingSymbols = new RANSEncodingSymbol[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        for (int i = 0; i < encodingSymbols.length; i++) {
            for (int j = 0; j < encodingSymbols[i].length; j++) {
                encodingSymbols[i][j] = new RANSEncodingSymbol();
            }
        }
    }

    protected final RANSEncodingSymbol[][] getEncodingSymbols() {
        return encodingSymbols;
    }

    /**
     * Compress a byte array using this rANS encoder.
     *
     * @param input the data to compress
     * @param params encoder-specific parameters (order, flags, etc.)
     * @return the compressed byte stream
     */
    public abstract byte[] compress(final byte[] input, final T params);

    /**
     * Set up encoding symbols for Order-0 from the given normalized frequency table.
     * Only symbols with non-zero frequency are initialized; others are reset to zero.
     */
    protected final void buildSymsOrder0(final int[] frequencies) {
        resetAndUpdateEncodingSymbols(frequencies, encodingSymbols[0]);
    }

    /**
     * Set up encoding symbols for Order-1 from the given normalized frequency tables.
     * Each row corresponds to one context symbol.
     */
    protected final void buildSymsOrder1(final int[][] frequencies) {
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            resetAndUpdateEncodingSymbols(frequencies[i], encodingSymbols[i]);
        }
    }

    private void resetAndUpdateEncodingSymbols(final int[] frequencies, final RANSEncodingSymbol[] symbols) {
        // No explicit reset needed: set() overwrites all fields, and symbols with zero frequency
        // are never accessed during encoding (only symbols present in the input are encoded).
        int cumulativeFreq = 0;
        for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
            if (frequencies[symbol] != 0) {
                symbols[symbol].set(cumulativeFreq, frequencies[symbol], Constants.TOTAL_FREQ_SHIFT);
                cumulativeFreq += frequencies[symbol];
            }
        }
    }
}
