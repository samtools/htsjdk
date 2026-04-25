package htsjdk.samtools.cram.compression.rans;

/**
 * Abstract base class for rANS decoders (both 4x8 and Nx16). Holds the shared decoding
 * state: per-context frequency tables, reverse-lookup tables, and decoding symbols.
 *
 * <p>State is allocated once at construction and reused across calls. Between calls,
 * only the rows that were actually used in the previous decode are reset, avoiding
 * the O(65536) full reset that would otherwise be required.
 */
public abstract class RANSDecode {
    private final int[][] frequencies;
    private final byte[][] reverseLookup;
    private final RANSDecodingSymbol[][] decodingSymbols;
    private final boolean[] usedRows;
    private int usedRowCount;

    protected RANSDecode() {
        frequencies = new int[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        reverseLookup = new byte[Constants.NUMBER_OF_SYMBOLS][Constants.TOTAL_FREQ];
        decodingSymbols = new RANSDecodingSymbol[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                decodingSymbols[i][j] = new RANSDecodingSymbol();
            }
        }
        usedRows = new boolean[Constants.NUMBER_OF_SYMBOLS];
    }

    protected final int[][] getFrequencies() {
        return frequencies;
    }

    protected final byte[][] getReverseLookup() {
        return reverseLookup;
    }

    protected final RANSDecodingSymbol[][] getDecodingSymbols() {
        return decodingSymbols;
    }

    /**
     * Uncompress a rANS-encoded byte stream.
     *
     * @param input the compressed byte stream (format-specific header + encoded data)
     * @return the uncompressed data
     */
    public abstract byte[] uncompress(final byte[] input);

    /**
     * Mark a context row as used. Called by subclass readFrequencyTable methods when
     * populating a row. Enables selective reset on the next {@link #resetDecoderState} call.
     */
    protected final void markRowUsed(final int row) {
        if (!usedRows[row]) {
            usedRows[row] = true;
            usedRowCount++;
        }
    }

    /**
     * Reset only the decoder rows that were used in the previous decode operation.
     * Called at the start of each uncompress to prepare for new data.
     */
    protected final void resetDecoderState() {
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS && usedRowCount > 0; i++) {
            if (usedRows[i]) {
                java.util.Arrays.fill(frequencies[i], 0);
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    decodingSymbols[i][j].set(0, 0);
                }
                usedRows[i] = false;
                usedRowCount--;
            }
        }
        usedRowCount = 0;
    }
}
