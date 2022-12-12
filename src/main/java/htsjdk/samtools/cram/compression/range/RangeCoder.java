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
        this.range = 0xFFFFFFFFL; // 4 bytes of all 1's
        this.code = 0;
        this.FFnum = 0;
        this.carry = false;
        this.cache = 0;
    }

    public void rangeDecodeStart(ByteBuffer inBuffer){
        for (int i = 0; i < 5; i++){

            // Get next 5 bytes. Ensure it is +ve
            code = (code << 8) + (inBuffer.get() & 0xFF);
        }
    }

    public void rangeDecode(ByteBuffer inBuffer, int sym_low, int sym_freq, int tot_freq){
        code -= sym_low * range;
        range *= sym_freq;

        while (range < (1<<24)) {
            range <<= 8;
            code = (code << 8) + (inBuffer.get() & 0xFF); // Ensure code is positive
        }
    }

    public int rangeGetFrequency(final int tot_freq){
        range =  (long) Math.floor(range / tot_freq);
        return (int) Math.floor(code / range);
    }

    public void rangeShiftLow(ByteBuffer outBuffer) {
        // rangeShiftLow tracks the total number of extra bytes to emit and
        // carry indicates whether they are a string of 0xFF or 0x00 values

        // range must be less than (2^24) or (1<<24) or (0x1000000)
        // "cache" holds the top byte that will be flushed to the output

        if ((low < 0xff000000L) || carry) { //TODO: 0xff000000L make this magic number a constant
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
            cache = (int) (low >> 24); // Copy of top byte ready for next flush
            carry = false;
        } else {
            FFnum++;
        }
        low = low<<8 & (0xFFFFFFFFL); // force low to be +ve
    }

    public void rangeEncode(final ByteBuffer outBuffer, final int sym_low, final int sym_freq, final int tot_freq){
        long old_low = low;
        range = (long) Math.floor(range/tot_freq);
        low += sym_low * range;
        low &= 0xFFFFFFFFL; // keep bottom 4 bytes, shift the top byte out of low
        range *= sym_freq;

        if (low < old_low) {
            carry = true;
        }

        // Renormalise if range gets too small
        while (range < (1<<24)) {
            range <<= 8;
            rangeShiftLow(outBuffer);
        }

    }

    public void rangeEncodeEnd(final ByteBuffer outBuffer){
        //TODO: Where is the magic number 5 coming from?
        for(int i = 0; i < 5; i++){
            rangeShiftLow(outBuffer);
        }
    }

}