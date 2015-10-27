package htsjdk.samtools.cram.encoding.rans;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class D14 {
    static void uncompress(final ByteBuffer in, final ByteBuffer out_buf, final Decoding.AriDecoder[] D,
                           final Decoding.RansDecSymbol[][] syms) {
        final int out_sz = out_buf.remaining();
        int rans0, rans1, rans2, rans7;
        in.order(ByteOrder.LITTLE_ENDIAN);
        rans0 = in.getInt();
        rans1 = in.getInt();
        rans2 = in.getInt();
        rans7 = in.getInt();

        final int isz4 = out_sz >> 2;
        int i0 = 0;
        int i1 = isz4;
        int i2 = 2 * isz4;
        int i7 = 3 * isz4;
        int l0 = 0;
        int l1 = 0;
        int l2 = 0;
        int l7 = 0;
        for (; i0 < isz4; i0++, i1++, i2++, i7++) {
            final int c0 = 0xFF & D[l0].R[Decoding.RansDecGet(rans0,
                    Constants.TF_SHIFT)];
            final int c1 = 0xFF & D[l1].R[Decoding.RansDecGet(rans1,
                    Constants.TF_SHIFT)];
            final int c2 = 0xFF & D[l2].R[Decoding.RansDecGet(rans2,
                    Constants.TF_SHIFT)];
            final int c7 = 0xFF & D[l7].R[Decoding.RansDecGet(rans7,
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

            rans0 = Decoding.RansDecRenormalize(rans0, in);
            rans1 = Decoding.RansDecRenormalize(rans1, in);
            rans2 = Decoding.RansDecRenormalize(rans2, in);
            rans7 = Decoding.RansDecRenormalize(rans7, in);

            l0 = c0;
            l1 = c1;
            l2 = c2;
            l7 = c7;
        }

        // Remainder
        for (; i7 < out_sz; i7++) {
            final int c7 = 0xFF & D[l7].R[Decoding.RansDecGet(rans7,
                    Constants.TF_SHIFT)];
            out_buf.put(i7, (byte) c7);
            rans7 = Decoding.RansDecAdvanceSymbol(rans7, in, syms[l7][c7],
                    Constants.TF_SHIFT);
            l7 = c7;
        }
    }
}
