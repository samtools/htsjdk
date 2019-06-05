package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

final class E04 {

    static int compress(final ByteBuffer inBuffer, final RANSEncodingSymbol[] syms, final ByteBuffer cp) {
        final int cdata_size;
        final int in_size = inBuffer.remaining();
        int rans0, rans1, rans2, rans3;
        final ByteBuffer ptr = cp.slice();

        rans0 = Constants.RANS_BYTE_L;
        rans1 = Constants.RANS_BYTE_L;
        rans2 = Constants.RANS_BYTE_L;
        rans3 = Constants.RANS_BYTE_L;

        int i;
        switch (i = (in_size & 3)) {
            case 3:
                rans2 = syms[0xFF & inBuffer.get(in_size - (i - 2))].putSymbol(rans2, ptr);
            case 2:
                rans1 = syms[0xFF & inBuffer.get(in_size - (i - 1))].putSymbol(rans1, ptr);
            case 1:
                rans0 = syms[0xFF & inBuffer.get(in_size - (i))].putSymbol(rans0, ptr);
            case 0:
                break;
        }
        for (i = (in_size & ~3); i > 0; i -= 4) {
            final int c3 = 0xFF & inBuffer.get(i - 1);
            final int c2 = 0xFF & inBuffer.get(i - 2);
            final int c1 = 0xFF & inBuffer.get(i - 3);
            final int c0 = 0xFF & inBuffer.get(i - 4);

            rans3 = syms[c3].putSymbol(rans3, ptr);
            rans2 = syms[c2].putSymbol(rans2, ptr);
            rans1 = syms[c1].putSymbol(rans1, ptr);
            rans0 = syms[c0].putSymbol(rans0, ptr);
        }

        ptr.putInt(rans3);
        ptr.putInt(rans2);
        ptr.putInt(rans1);
        ptr.putInt(rans0);
        ptr.flip();
        cdata_size = ptr.limit();
        // reverse the compressed bytes, so that they become in REVERSE order:
        Utils.reverse(ptr);
        inBuffer.position(inBuffer.limit());
        return cdata_size;
    }
}
