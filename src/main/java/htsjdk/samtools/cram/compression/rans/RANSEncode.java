package htsjdk.samtools.cram.compression.rans;

/**
 * Abstract base class for rANS encoders (both 4x8 and Nx16). Holds the shared encoding
 * symbol matrix and provides helper methods for frequency-to-symbol setup.
 *
 * <p>The matrix has a row of symbols per order-1 context. Row 0, which is all that order-0 coding
 * uses, is allocated at construction; the other rows are allocated together the first time an
 * order-1 stream is encoded, since they are most of an encoder's memory and many encoders never
 * encode one. Rows are reused across compress calls without being reset.
 */
public abstract class RANSEncode<T extends RANSParams> {
    private final RANSEncodingSymbol[][] encodingSymbols = new RANSEncodingSymbol[Constants.NUMBER_OF_SYMBOLS][];

    protected RANSEncode() {
        encodingSymbols[0] = newSymbolRow();
    }

    private static RANSEncodingSymbol[] newSymbolRow() {
        final RANSEncodingSymbol[] row = new RANSEncodingSymbol[Constants.NUMBER_OF_SYMBOLS];
        for (int j = 0; j < row.length; j++) {
            row[j] = new RANSEncodingSymbol();
        }
        return row;
    }

    /**
     * @return the symbol matrix, indexed by context and then by symbol. Only row 0 is sure to be there
     *     until {@link #buildSymsOrder1} has been called.
     */
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
        if (encodingSymbols[1] == null) {
            for (int i = 1; i < Constants.NUMBER_OF_SYMBOLS; i++) {
                encodingSymbols[i] = newSymbolRow();
            }
        }
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
