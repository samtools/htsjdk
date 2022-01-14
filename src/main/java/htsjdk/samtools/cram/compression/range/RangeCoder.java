package htsjdk.samtools.cram.compression.range;

import java.nio.ByteBuffer;

public class RangeCoder {

    private long low;
    private long range;
    private long code;
    private int FFnum;
    private boolean carry;
    private int cache;

    public RangeCoder() {
        // Spec: RangeEncodeStart
        this.low = 0;
        this.range = Constants.MAX_RANGE; // 4 bytes of all 1's
        this.code = 0;
        this.FFnum = 0;
        this.carry = false;
        this.cache = 0;
    }

    public void rangeDecodeStart(final ByteBuffer inBuffer){
        for (int i = 0; i < 5; i++){
            code = (code << 8) + (inBuffer.get() & 0xFF);
        }
        code &= Constants.MAX_RANGE;
    }

    protected void rangeDecode(final ByteBuffer inBuffer, final int cumulativeFrequency, final int symbolFrequency){
        code -= cumulativeFrequency * range;
        range *= symbolFrequency;

        while (range < (1<<24)) {
            range <<= 8;
            code = (code << 8) + (inBuffer.get() & 0xFF); // Ensure code is positive
        }
    }

    protected int rangeGetFrequency(final int totalFrequency){
        range =  (long) Math.floor(range / totalFrequency);
        return (int) Math.floor(code / range);
    }

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

    protected void rangeEncodeEnd(final ByteBuffer outBuffer){
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