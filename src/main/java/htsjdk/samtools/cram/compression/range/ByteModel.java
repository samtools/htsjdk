package htsjdk.samtools.cram.compression.range;

import java.nio.ByteBuffer;

/**
 * Adaptive frequency model for the CRAM 3.1 arithmetic range coder. Maintains per-symbol frequency
 * counts and provides encode/decode operations that update the model after each symbol. Symbols
 * are kept approximately sorted by frequency (descending) for cache-friendly access.
 *
 * <p>Each symbol starts with a frequency of 1. After encoding/decoding a symbol, its frequency
 * is incremented by {@link Constants#STEP} (16). When total frequency exceeds {@link Constants#MAX_FREQ},
 * all frequencies are halved (avoiding zeros).
 *
 * @see RangeCoder
 */
public class ByteModel {
    public int totalFrequency;
    public final int maxSymbol;
    public final int[] symbols;
    public final int[] frequencies;

    /**
     * Create a new model for the given number of distinct symbols (0 to numSymbols-1),
     * each starting with frequency 1.
     *
     * @param numSymbols number of distinct symbols this model can encode/decode
     */
    public ByteModel(final int numSymbols) {
        // Spec: ModelCreate method
        this.totalFrequency = numSymbols;
        this.maxSymbol = numSymbols - 1;
        frequencies = new int[maxSymbol+1];
        symbols = new int[maxSymbol+1];
        for (int i = 0; i <= maxSymbol; i++) {
            this.symbols[i] = i;
            this.frequencies[i] = 1;
        }
    }

    /**
     * Decode one symbol from the compressed stream, update the model frequencies, and return the symbol.
     *
     * @param inBuffer the compressed input stream
     * @param rangeCoder the range coder state (must have been started with {@link RangeCoder#rangeDecodeStart})
     * @return the decoded symbol value
     */
    public int modelDecode(final ByteBuffer inBuffer, final RangeCoder rangeCoder){
        final int freq = rangeCoder.rangeGetFrequency(totalFrequency);
        int cumulativeFrequency = 0;
        int x = 0;
        while (cumulativeFrequency + frequencies[x] <= freq){
            cumulativeFrequency += frequencies[x++];
        }

        // update rangecoder
        rangeCoder.rangeDecode(inBuffer,cumulativeFrequency,frequencies[x]);

        // update model frequencies
        frequencies[x] += Constants.STEP;
        totalFrequency += Constants.STEP;
        if (totalFrequency > Constants.MAX_FREQ){
            // if totalFrequency is too high, the frequencies are halved, making
            // sure to avoid any zero frequencies being created.
            modelRenormalize();
        }

        // keep symbols approximately frequency sorted
        final int symbol = symbols[x];
        if (x > 0 && frequencies[x] > frequencies[x-1]){
            // Swap frequencies[x], frequencies[x-1]
            int tmp = frequencies[x];
            frequencies[x] = frequencies[x-1];
            frequencies[x-1] = tmp;

            // Swap symbols[x], symbols[x-1]
            tmp = symbols[x];
            symbols[x] = symbols[x-1];
            symbols[x-1] = tmp;
        }
        return symbol;
    }

    /** Halve all frequencies (avoiding zeros) when total frequency exceeds {@link Constants#MAX_FREQ}. */
    public void modelRenormalize(){
        totalFrequency = 0;
        for (int i=0; i <= maxSymbol; i++){
            frequencies[i] -= Math.floorDiv(frequencies[i],2);
            totalFrequency += frequencies[i];
        }
    }

    /**
     * Encode one symbol to the compressed stream and update the model frequencies.
     *
     * @param outBuffer the output stream for compressed bytes
     * @param rangeCoder the range coder state
     * @param symbol the symbol value to encode (must be in range 0 to maxSymbol)
     */
    public void modelEncode(final ByteBuffer outBuffer, final RangeCoder rangeCoder, final int symbol){
        int cumulativeFrequency = 0;
        int i;
        for( i = 0; symbols[i] != symbol; i++){
            cumulativeFrequency += frequencies[i];
        }

        // Encode
        rangeCoder.rangeEncode(outBuffer, cumulativeFrequency, frequencies[i],totalFrequency);

        // Update Model
        frequencies[i] += Constants.STEP;
        totalFrequency += Constants.STEP;
        if (totalFrequency > Constants.MAX_FREQ){
            modelRenormalize();
        }

        // Keep symbols approximately frequency sorted (ascending order)
        if (i > 0 && frequencies[i] > frequencies[i-1]){
            // swap frequencies
            int tmp = frequencies[i];
            frequencies[i] = frequencies[i-1];
            frequencies[i-1]=tmp;

            // swap symbols
            tmp = symbols[i];
            symbols[i] = symbols[i-1];
            symbols[i-1] = tmp;
        }
    }

}