package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

class D04 {
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
            final byte c0 = D.R[Decoding.RansDecGet(rans0, Constants.TF_SHIFT)];
            final byte c1 = D.R[Decoding.RansDecGet(rans1, Constants.TF_SHIFT)];
            final byte c2 = D.R[Decoding.RansDecGet(rans2, Constants.TF_SHIFT)];
            final byte c3 = D.R[Decoding.RansDecGet(rans3, Constants.TF_SHIFT)];

            outBuffer.put(i, c0);
            outBuffer.put(i + 1, c1);
            outBuffer.put(i + 2, c2);
            outBuffer.put(i + 3, c3);

            rans0 = Decoding.RansDecAdvanceSymbolStep(rans0, syms[0xFF & c0], Constants.TF_SHIFT);
            rans1 = Decoding.RansDecAdvanceSymbolStep(rans1, syms[0xFF & c1], Constants.TF_SHIFT);
            rans2 = Decoding.RansDecAdvanceSymbolStep(rans2, syms[0xFF & c2], Constants.TF_SHIFT);
            rans3 = Decoding.RansDecAdvanceSymbolStep(rans3, syms[0xFF & c3], Constants.TF_SHIFT);

            rans0 = Decoding.RansDecRenormalize(rans0, inBuffer);
            rans1 = Decoding.RansDecRenormalize(rans1, inBuffer);
            rans2 = Decoding.RansDecRenormalize(rans2, inBuffer);
            rans3 = Decoding.RansDecRenormalize(rans3, inBuffer);
        }

        outBuffer.position(out_end);
        byte c;
        switch (out_sz & 3) {
            case 0:
                break;

            case 1:
                c = D.R[Decoding.RansDecGet(rans0, Constants.TF_SHIFT)];
                Decoding.RansDecAdvanceSymbol(rans0, inBuffer, syms[0xFF & c], Constants.TF_SHIFT);
                outBuffer.put(c);
                break;

            case 2:
                c = D.R[Decoding.RansDecGet(rans0, Constants.TF_SHIFT)];
                Decoding.RansDecAdvanceSymbol(rans0, inBuffer, syms[0xFF & c], Constants.TF_SHIFT);
                outBuffer.put(c);

                c = D.R[Decoding.RansDecGet(rans1, Constants.TF_SHIFT)];
                Decoding.RansDecAdvanceSymbol(rans1, inBuffer, syms[0xFF & c], Constants.TF_SHIFT);
                outBuffer.put(c);
                break;

            case 3:
                c = D.R[Decoding.RansDecGet(rans0, Constants.TF_SHIFT)];
                Decoding.RansDecAdvanceSymbol(rans0, inBuffer, syms[0xFF & c], Constants.TF_SHIFT);
                outBuffer.put(c);

                c = D.R[Decoding.RansDecGet(rans1, Constants.TF_SHIFT)];
                Decoding.RansDecAdvanceSymbol(rans1, inBuffer, syms[0xFF & c], Constants.TF_SHIFT);
                outBuffer.put(c);

                c = D.R[Decoding.RansDecGet(rans2, Constants.TF_SHIFT)];
                Decoding.RansDecAdvanceSymbol(rans2, inBuffer, syms[0xFF & c], Constants.TF_SHIFT);
                outBuffer.put(c);
                break;
        }

        outBuffer.position(0);
    }
}
