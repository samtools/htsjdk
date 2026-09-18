package htsjdk.samtools.cram.compression.rans;

/**
 * Abstract base class for rANS decoders (both 4x8 and Nx16). Holds the shared decoding
 * state: per-context frequency tables, reverse-lookup tables, and decoding symbols.
 *
 * <p>Each table has a row per order-1 context. Row 0, which is all that order-0 decoding uses, is
 * allocated at construction; the other rows are allocated together by {@link #allocateOrder1Rows}
 * the first time an order-1 stream is decoded, since they are most of a decoder's memory and many
 * decoders never decode one. Rows are reused across calls: between calls, only the rows that were
 * actually used in the previous decode are reset, avoiding the O(65536) full reset that would
 * otherwise be required.
 */
public abstract class RANSDecode {
    private final int[][] frequencies = new int[Constants.NUMBER_OF_SYMBOLS][];
    private final byte[][] reverseLookup = new byte[Constants.NUMBER_OF_SYMBOLS][];
    private final RANSDecodingSymbol[][] decodingSymbols = new RANSDecodingSymbol[Constants.NUMBER_OF_SYMBOLS][];
    private final boolean[] usedRows = new boolean[Constants.NUMBER_OF_SYMBOLS];
    private int usedRowCount;

    protected RANSDecode() {
        allocateRow(0);
    }

    private void allocateRow(final int row) {
        frequencies[row] = new int[Constants.NUMBER_OF_SYMBOLS];
        reverseLookup[row] = new byte[Constants.TOTAL_FREQ];
        decodingSymbols[row] = new RANSDecodingSymbol[Constants.NUMBER_OF_SYMBOLS];
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            decodingSymbols[row][j] = new RANSDecodingSymbol();
        }
    }

    /**
     * Make sure that the rows of every context are there. Must be called before an order-1 frequency
     * table is read; the tables returned by the getters have only row 0 until then.
     */
    protected final void allocateOrder1Rows() {
        if (frequencies[1] == null) {
            for (int i = 1; i < Constants.NUMBER_OF_SYMBOLS; i++) {
                allocateRow(i);
            }
        }
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
