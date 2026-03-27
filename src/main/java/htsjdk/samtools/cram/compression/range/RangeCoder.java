package htsjdk.samtools.cram.compression.range;

import java.nio.ByteBuffer;

/**
 * Arithmetic range coder used by the CRAM 3.1 Range (adaptive arithmetic) codec and FQZComp quality
 * score codec. Implements both encoding and decoding using a 32-bit range with carry propagation
 * for output byte generation.
 *
 * <p>The range coder maintains a probability interval [low, low+range) and narrows it for each
 * symbol based on cumulative and symbol frequencies. When the range becomes too small (&lt; 2^24),
 * it renormalizes by shifting out the top byte.
 *
 * @see ByteModel
 * @see <a href="https://samtools.github.io/hts-specs/CRAMv3.pdf">CRAM 3.1 specification, Section 3.5</a>
 */
public class RangeCoder {

    private long low;
    private long range;
    private long code;
    private int FFnum;
    private boolean carry;
    private int cache;

    public RangeCoder() {
        this.low = 0;
        this.range = Constants.MAX_RANGE;
        this.code = 0;
        this.FFnum = 0;
        this.carry = false;
        this.cache = 0;
    }

    /**
     * Initialize the decoder by reading the first 5 bytes of the compressed stream into the code register.
     * Must be called before any calls to {@link ByteModel#modelDecode}.
     *
     * @param inBuffer the compressed input stream
     */
    public void rangeDecodeStart(final ByteBuffer inBuffer){
        for (int i = 0; i < 5; i++){
            code = (code << 8) + (inBuffer.get() & 0xFF);
        }
        code &= Constants.MAX_RANGE;
    }

    /**
     * Update the decoder state after a symbol has been decoded.
     *
     * @param inBuffer the compressed input stream (for renormalization reads)
     * @param cumulativeFrequency cumulative frequency of symbols before the decoded symbol
     * @param symbolFrequency frequency of the decoded symbol
     */
    protected void rangeDecode(final ByteBuffer inBuffer, final int cumulativeFrequency, final int symbolFrequency){
        code -= cumulativeFrequency * range;
        range *= symbolFrequency;

        while (range < (1<<24)) {
            range <<= 8;
            code = (code << 8) + (inBuffer.get() & 0xFF); // Ensure code is positive
        }
    }

    /**
     * Compute the scaled frequency for symbol lookup during decoding.
     *
     * @param totalFrequency the sum of all symbol frequencies
     * @return the scaled frequency value used to identify the decoded symbol
     */
    protected int rangeGetFrequency(final int totalFrequency){
        range =  (long) Math.floor(range / totalFrequency);
        return (int) Math.floor(code / range);
    }

    /**
     * Encode a symbol by narrowing the range interval and emitting output bytes as needed.
     *
     * @param outBuffer the output stream for compressed bytes
     * @param cumulativeFrequency cumulative frequency of all symbols before this one
     * @param symbolFrequency frequency of the symbol being encoded
     * @param totalFrequency sum of all symbol frequencies
     */
    protected void rangeEncode(
            final ByteBuffer outBuffer,
            final int cumulativeFrequency,
            final int symbolFrequency,
            final int totalFrequency){
        final long old_low = low;
        range = (long) Math.floor(range/totalFrequency);
        low += cumulativeFrequency * range;
        low &= 0xFFFFFFFFL; // keep bottom 4 bytes, shift the top byte out of low
        range *= symbolFrequency;

        if (low < old_low) {
            carry = true;
        }

        // Renormalise if range gets too small
        while (range < (1<<24)) {
            range <<= 8;
            rangeShiftLow(outBuffer);
        }

    }

    /**
     * Flush the encoder state by emitting the final 5 bytes. Must be called after all symbols
     * have been encoded to produce a valid compressed stream.
     *
     * @param outBuffer the output stream for compressed bytes
     */
    public void rangeEncodeEnd(final ByteBuffer outBuffer){
        for(int i = 0; i < 5; i++){
            rangeShiftLow(outBuffer);
        }
    }

    private void rangeShiftLow(final ByteBuffer outBuffer) {
        // rangeShiftLow tracks the total number of extra bytes to emit and
        // carry indicates whether they are a string of 0xFF or 0x00 values

        // range must be less than (2^24) or (1<<24) or (0x1000000)
        // "cache" holds the top byte that will be flushed to the output

        if ((low < 0xff000000L) || carry) {
            if (carry == false) {
                outBuffer.put((byte) cache);
                while (FFnum > 0) {
                    outBuffer.put((byte) 0xFF);
                    FFnum--;
                }
            } else {
                outBuffer.put((byte) (cache + 1));
                while (FFnum > 0) {
                    outBuffer.put((byte) 0x00);
                    FFnum--;
                }

            }
            cache = (int) (low >>> 24); // Copy of top byte ready for next flush
            carry = false;
        } else {
            FFnum++;
        }
        low = low<<8 & (0xFFFFFFFFL); // force low to be +ve
    }

}