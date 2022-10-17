package htsjdk.samtools.cram.compression.range;

import java.nio.ByteBuffer;

public class RangeCoder {

    private int low;
    private long range;
    private int code;
    private int FFnum;
    private boolean carry;
    private int cache;

    public RangeCoder() {
        // Spec: RangeEncodeStart
        low = 0;
        range = 0xffffffff; // 4 bytes of all 1's (2**32 - 1)
        code = 0;
        FFnum = 0;
        carry = false;
        cache = 0;
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


        if ((low < 0xff000000) || carry) {
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
            cache = low >> 24; // Copy of top byte ready for next flush
            carry = false;
        } else {
            FFnum++;
        }
        low = low << 8;
        // TODO: is it necessary to do -> low = low >>> 0 // keep "low" positive
        // i.e, arithmetic right shift by 0 bits?
    }

    public void rangeEncode(final ByteBuffer outBuffer, final int sym_low, final int sym_freq, final int tot_freq){
        int old_low = low;
        range = (long) Math.floor(range/tot_freq);
        low += sym_low * range;
        low >>>=0; // TODO: Inspect this!! Truncate to +ve int so we can spot overflow
        range *= sym_freq;

        if (low < old_low) {
            carry = true;
        }

        // Renormalise if range gets too small
        while (range < (1<<24)) {
            range <<= 8; // range *= 256
            rangeShiftLow(outBuffer);
        }

    }

    public void rangeEncodeEnd(final ByteBuffer outBuffer){
        //TODO: Where is the magic number 5 coming from?
        for(int i=0; i<5;i++){
            rangeShiftLow(outBuffer);
        }
    }

}