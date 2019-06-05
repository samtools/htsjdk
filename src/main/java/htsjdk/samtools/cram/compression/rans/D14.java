package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class D14 {
    static void uncompress(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final ArithmeticDecoder[] D,
            final RANSDecodingSymbol[][] syms) {
        final int out_sz = outBuffer.remaining();
        int rans0, rans1, rans2, rans7;
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        rans0 = inBuffer.getInt();
        rans1 = inBuffer.getInt();
        rans2 = inBuffer.getInt();
        rans7 = inBuffer.getInt();

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
            final int c0 = 0xFF & D[l0].R[Utils.RANSDecodeGet(rans0, Constants.TF_SHIFT)];
            final int c1 = 0xFF & D[l1].R[Utils.RANSDecodeGet(rans1, Constants.TF_SHIFT)];
            final int c2 = 0xFF & D[l2].R[Utils.RANSDecodeGet(rans2, Constants.TF_SHIFT)];
            final int c7 = 0xFF & D[l7].R[Utils.RANSDecodeGet(rans7, Constants.TF_SHIFT)];

            outBuffer.put(i0, (byte) c0);
            outBuffer.put(i1, (byte) c1);
            outBuffer.put(i2, (byte) c2);
            outBuffer.put(i7, (byte) c7);

            rans0 = syms[l0][c0].advanceSymbolStep(rans0,  Constants.TF_SHIFT);
            rans1 = syms[l1][c1].advanceSymbolStep(rans1, Constants.TF_SHIFT);
            rans2 = syms[l2][c2].advanceSymbolStep(rans2, Constants.TF_SHIFT);
            rans7 = syms[l7][c7].advanceSymbolStep(rans7,  Constants.TF_SHIFT);

            rans0 = Utils.RANSDecodeRenormalize(rans0, inBuffer);
            rans1 = Utils.RANSDecodeRenormalize(rans1, inBuffer);
            rans2 = Utils.RANSDecodeRenormalize(rans2, inBuffer);
            rans7 = Utils.RANSDecodeRenormalize(rans7, inBuffer);

            l0 = c0;
            l1 = c1;
            l2 = c2;
            l7 = c7;
        }

        // Remainder
        for (; i7 < out_sz; i7++) {
            final int c7 = 0xFF & D[l7].R[Utils.RANSDecodeGet(rans7, Constants.TF_SHIFT)];
            outBuffer.put(i7, (byte) c7);
            rans7 = syms[l7][c7].advanceSymbol(rans7, inBuffer, Constants.TF_SHIFT);
            l7 = c7;
        }
    }
}
