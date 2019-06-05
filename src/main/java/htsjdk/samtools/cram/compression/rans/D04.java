package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

final class D04 {
    static void uncompress(
            final ByteBuffer inBuffer,
            final ArithmeticDecoder D,
            final RANSDecodingSymbol[] syms,
            final ByteBuffer outBuffer) {
        int rans0, rans1, rans2, rans3;
        rans0 = inBuffer.getInt();
        rans1 = inBuffer.getInt();
        rans2 = inBuffer.getInt();
        rans3 = inBuffer.getInt();

        final int out_sz = outBuffer.remaining();
        final int out_end = (out_sz & ~3);
        for (int i = 0; i < out_end; i += 4) {
            final byte c0 = D.R[Utils.RANSDecodeGet(rans0, Constants.TF_SHIFT)];
            final byte c1 = D.R[Utils.RANSDecodeGet(rans1, Constants.TF_SHIFT)];
            final byte c2 = D.R[Utils.RANSDecodeGet(rans2, Constants.TF_SHIFT)];
            final byte c3 = D.R[Utils.RANSDecodeGet(rans3, Constants.TF_SHIFT)];

            outBuffer.put(i, c0);
            outBuffer.put(i + 1, c1);
            outBuffer.put(i + 2, c2);
            outBuffer.put(i + 3, c3);

            rans0 = syms[0xFF & c0].advanceSymbolStep(rans0, Constants.TF_SHIFT);
            rans1 = syms[0xFF & c1].advanceSymbolStep(rans1, Constants.TF_SHIFT);
            rans2 = syms[0xFF & c2].advanceSymbolStep(rans2, Constants.TF_SHIFT);
            rans3 = syms[0xFF & c3].advanceSymbolStep(rans3,  Constants.TF_SHIFT);

            rans0 = Utils.RANSDecodeRenormalize(rans0, inBuffer);
            rans1 = Utils.RANSDecodeRenormalize(rans1, inBuffer);
            rans2 = Utils.RANSDecodeRenormalize(rans2, inBuffer);
            rans3 = Utils.RANSDecodeRenormalize(rans3, inBuffer);
        }

        outBuffer.position(out_end);
        byte c;
        switch (out_sz & 3) {
            case 0:
                break;

            case 1:
                c = D.R[Utils.RANSDecodeGet(rans0, Constants.TF_SHIFT)];
                syms[0xFF & c].advanceSymbol(rans0, inBuffer, Constants.TF_SHIFT);
                outBuffer.put(c);
                break;

            case 2:
                c = D.R[Utils.RANSDecodeGet(rans0, Constants.TF_SHIFT)];
                syms[0xFF & c].advanceSymbol(rans0, inBuffer, Constants.TF_SHIFT);
                outBuffer.put(c);

                c = D.R[Utils.RANSDecodeGet(rans1, Constants.TF_SHIFT)];
                syms[0xFF & c].advanceSymbol(rans1, inBuffer, Constants.TF_SHIFT);
                outBuffer.put(c);
                break;

            case 3:
                c = D.R[Utils.RANSDecodeGet(rans0, Constants.TF_SHIFT)];
                syms[0xFF & c].advanceSymbol(rans0, inBuffer,  Constants.TF_SHIFT);
                outBuffer.put(c);

                c = D.R[Utils.RANSDecodeGet(rans1, Constants.TF_SHIFT)];
                syms[0xFF & c].advanceSymbol(rans1, inBuffer, Constants.TF_SHIFT);
                outBuffer.put(c);

                c = D.R[Utils.RANSDecodeGet(rans2, Constants.TF_SHIFT)];
                syms[0xFF & c].advanceSymbol(rans2, inBuffer, Constants.TF_SHIFT);
                outBuffer.put(c);
                break;
        }

        outBuffer.position(0);
    }
}
