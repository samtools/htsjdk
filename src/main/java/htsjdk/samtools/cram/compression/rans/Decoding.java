package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

class Decoding {

    // Returns the current cumulative frequency (map it to a symbol yourself!)
    static int RANSDecodeGet(final int r, final int scaleBits) {
        return r & ((1 << scaleBits) - 1);
    }

    // Re-normalize.
    static int RANSDecodeRenormalize(int r, final ByteBuffer byteBuffer) {
        // re-normalize
        if (r < Constants.RANS_BYTE_L) {
            do {
                r = (r << 8) | (0xFF & byteBuffer.get());
            } while (r < Constants.RANS_BYTE_L);
        }

        return r;
    }

}
