package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

final class Utils {

    private static void reverse(final byte[] array, final int offset, final int size) {
        if (array == null) {
            return;
        }
        int i = offset;
        int j = offset + size - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    static void reverse(final ByteBuffer byteBuffer) {
        byte tmp;
        if (byteBuffer.hasArray()) {
            reverse(byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.limit());
        } else {
            for (int i = 0; i < byteBuffer.limit(); i++) {
                tmp = byteBuffer.get(i);
                byteBuffer.put(i, byteBuffer.get(byteBuffer.limit() - i - 1));
                byteBuffer.put(byteBuffer.limit() - i - 1, tmp);
            }
        }
    }

    // Returns the current cumulative frequency (map it to a symbol yourself!)
    static int RANSDecodeGet(final int r, final int scaleBits) {
        return r & ((1 << scaleBits) - 1);
    }

    // Re-normalize.
    static int RANSDecodeRenormalize4x8(int r, final ByteBuffer byteBuffer) {
        // re-normalize

        //rans4x8
        if (r < Constants.RANS_BYTE_L_4x8) {
            do {
                r = (r << 8) | (0xFF & byteBuffer.get());
            } while (r < Constants.RANS_BYTE_L_4x8);
        }
        return r;
    }

    static int RANSDecodeRenormalizeNx16(int r, final ByteBuffer byteBuffer) {
        // ransNx16
        if (r < (Constants.RANS_BYTE_L_Nx16)) {
            int i = (0xFF & byteBuffer.get());
            i |= (0xFF & byteBuffer.get()) <<8;

            r = (r << 16) | i;
        }
        return r;
    }

    public static void writeUint7(int i, ByteBuffer cp){
        int s = 0;
        int X = i;
        do {
            s += 7;
            X >>= 7;
        }while (X>0);
        do {
            s -=7;
            //writeByte
            int s_ = (s > 0)?1:0;
            cp.put((byte) (((i >> s) & 0x7f) + (s_ << 7)));
        } while (s>0);
    }

    public static int readUint7(ByteBuffer cp){
        int i = 0;
        int c;
        do {
            //read byte
            c = cp.get();
            i = (i<<7) | (c & 0x7f);
        } while((c & 0x80)!=0);
        return i;
    }
}
