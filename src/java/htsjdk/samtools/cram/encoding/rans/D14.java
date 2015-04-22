package htsjdk.samtools.cram.encoding.rans;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class D14 {
    static void uncompress(ByteBuffer in, ByteBuffer out_buf, Decoding.ari_decoder[] D,
                           Decoding.RansDecSymbol[][] syms) {
        int out_sz = out_buf.remaining();
        int rans0, rans1, rans2, rans7;
        ByteBuffer ptr = in;
        ptr.order(ByteOrder.LITTLE_ENDIAN);
        rans0 = ptr.getInt();
        rans1 = ptr.getInt();
        rans2 = ptr.getInt();
        rans7 = ptr.getInt();

        int isz4 = out_sz >> 2;
        int i0 = 0;
        int i1 = isz4;
        int i2 = 2 * isz4;
        int i7 = 3 * isz4;
        int l0 = 0;
        int l1 = 0;
        int l2 = 0;
        int l7 = 0;
        for (; i0 < isz4; i0++, i1++, i2++, i7++) {
            int c0 = 0xFF & D[l0].R[Decoding.RansDecGet(rans0,
                    Constants.TF_SHIFT)];
            int c1 = 0xFF & D[l1].R[Decoding.RansDecGet(rans1,
                    Constants.TF_SHIFT)];
            int c2 = 0xFF & D[l2].R[Decoding.RansDecGet(rans2,
                    Constants.TF_SHIFT)];
            int c7 = 0xFF & D[l7].R[Decoding.RansDecGet(rans7,
                    Constants.TF_SHIFT)];

            out_buf.put(i0, (byte) c0);
            out_buf.put(i1, (byte) c1);
            out_buf.put(i2, (byte) c2);
            out_buf.put(i7, (byte) c7);

            rans0 = Decoding.RansDecAdvanceSymbolStep(rans0, syms[l0][c0],
                    Constants.TF_SHIFT);
            rans1 = Decoding.RansDecAdvanceSymbolStep(rans1, syms[l1][c1],
                    Constants.TF_SHIFT);
            rans2 = Decoding.RansDecAdvanceSymbolStep(rans2, syms[l2][c2],
                    Constants.TF_SHIFT);
            rans7 = Decoding.RansDecAdvanceSymbolStep(rans7, syms[l7][c7],
                    Constants.TF_SHIFT);

            rans0 = Decoding.RansDecRenorm(rans0, ptr);
            rans1 = Decoding.RansDecRenorm(rans1, ptr);
            rans2 = Decoding.RansDecRenorm(rans2, ptr);
            rans7 = Decoding.RansDecRenorm(rans7, ptr);

            l0 = c0;
            l1 = c1;
            l2 = c2;
            l7 = c7;
        }

        // Remainder
        for (; i7 < out_sz; i7++) {
            int c7 = 0xFF & D[l7].R[Decoding.RansDecGet(rans7,
                    Constants.TF_SHIFT)];
            out_buf.put(i7, (byte) c7);
            rans7 = Decoding.RansDecAdvanceSymbol(rans7, ptr, syms[l7][c7],
                    Constants.TF_SHIFT);
            // rans7 = Decoding.RansDecRenorm(rans7, ptr);
            l7 = c7;
        }
    }
}
