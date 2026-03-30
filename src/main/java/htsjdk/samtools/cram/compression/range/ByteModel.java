package htsjdk.samtools.cram.compression.range;

import java.nio.ByteBuffer;

/**
 * Adaptive frequency model for the CRAM 3.1 arithmetic range coder. Maintains per-symbol frequency
 * counts and provides encode/decode operations that update the model after each symbol. Symbols
 * are kept approximately sorted by frequency (descending) for cache-friendly access.
 *
 * <p>Symbols and frequencies are interleaved in a single {@code int[]} array for cache locality:
 * even indices hold frequencies, odd indices hold symbol values. This eliminates the cache
 * thrashing that occurs with separate arrays during the linear scan.
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

    /**
     * Interleaved frequency/symbol pairs: {@code data[i*2]} = frequency, {@code data[i*2+1]} = symbol.
     * Keeping these adjacent improves cache hit rate during the linear scan in encode/decode.
     */
    public final int[] data;

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
        data = new int[(maxSymbol + 1) * 2];
        for (int i = 0; i <= maxSymbol; i++) {
            data[i * 2] = 1;       // frequency
            data[i * 2 + 1] = i;   // symbol
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
        while (cumulativeFrequency + data[x * 2] <= freq){
            cumulativeFrequency += data[x * 2];
            x++;
        }

        // update rangecoder
        rangeCoder.rangeDecode(inBuffer, cumulativeFrequency, data[x * 2]);

        // update model frequencies
        data[x * 2] += Constants.STEP;
        totalFrequency += Constants.STEP;
        if (totalFrequency > Constants.MAX_FREQ){
            modelRenormalize();
        }

        // keep symbols approximately frequency sorted
        final int symbol = data[x * 2 + 1];
        if (x > 0 && data[x * 2] > data[(x - 1) * 2]){
            // Swap frequency and symbol pairs
            final int tmpFreq = data[x * 2];
            final int tmpSym  = data[x * 2 + 1];
            data[x * 2]     = data[(x - 1) * 2];
            data[x * 2 + 1] = data[(x - 1) * 2 + 1];
            data[(x - 1) * 2]     = tmpFreq;
            data[(x - 1) * 2 + 1] = tmpSym;
        }
        return symbol;
    }

    /** Halve all frequencies (avoiding zeros) when total frequency exceeds {@link Constants#MAX_FREQ}. */
    public void modelRenormalize(){
        totalFrequency = 0;
        for (int i = 0; i <= maxSymbol; i++){
            data[i * 2] -= data[i * 2] >> 1;
            totalFrequency += data[i * 2];
        }
    }

    /**
     * Encode one symbol to the compressed stream and update the model frequencies.
     * Output is written to the range coder's internal byte[] buffer.
     *
     * @param rangeCoder the range coder state (must have output set via {@link RangeCoder#setOutput})
     * @param symbol the symbol value to encode (must be in range 0 to maxSymbol)
     */
    public void modelEncode(final RangeCoder rangeCoder, final int symbol){
        int cumulativeFrequency = 0;
        int i = 0;
        while (data[i * 2 + 1] != symbol) {
            cumulativeFrequency += data[i * 2];
            i++;
        }

        // Encode
        rangeCoder.rangeEncode(cumulativeFrequency, data[i * 2], totalFrequency);

        // Update Model
        data[i * 2] += Constants.STEP;
        totalFrequency += Constants.STEP;
        if (totalFrequency > Constants.MAX_FREQ){
            modelRenormalize();
        }

        // Keep symbols approximately frequency sorted (ascending order)
        if (i > 0 && data[i * 2] > data[(i - 1) * 2]){
            // swap frequency and symbol pairs
            final int tmpFreq = data[i * 2];
            final int tmpSym  = data[i * 2 + 1];
            data[i * 2]     = data[(i - 1) * 2];
            data[i * 2 + 1] = data[(i - 1) * 2 + 1];
            data[(i - 1) * 2]     = tmpFreq;
            data[(i - 1) * 2 + 1] = tmpSym;
        }
    }

}
