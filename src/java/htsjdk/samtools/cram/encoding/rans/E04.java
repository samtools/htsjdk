package htsjdk.samtools.cram.encoding.rans;

import htsjdk.samtools.cram.encoding.rans.Encoding.RansEncSymbol;

import java.nio.ByteBuffer;

class E04 {

    static int compress(final ByteBuffer in, final RansEncSymbol[] syms,
                        final ByteBuffer cp) {
        final int cdata_size;
        final int in_size = in.remaining();
        int rans0, rans1, rans2, rans3;
        final ByteBuffer ptr = cp.slice();

        rans0 = Constants.RANS_BYTE_L;
        rans1 = Constants.RANS_BYTE_L;
        rans2 = Constants.RANS_BYTE_L;
        rans3 = Constants.RANS_BYTE_L;

        int i;
        switch (i = (in_size & 3)) {
            case 3:
                rans2 = Encoding.RansEncPutSymbol(rans2, ptr,
                        syms[0xFF & in.get(in_size - (i - 2))]);
            case 2:
                rans1 = Encoding.RansEncPutSymbol(rans1, ptr,
                        syms[0xFF & in.get(in_size - (i - 1))]);
            case 1:
                rans0 = Encoding.RansEncPutSymbol(rans0, ptr,
                        syms[0xFF & in.get(in_size - (i))]);
            case 0:
                break;
        }
        for (i = (in_size & ~3); i > 0; i -= 4) {
            final int c3 = 0xFF & in.get(i - 1);
            final int c2 = 0xFF & in.get(i - 2);
            final int c1 = 0xFF & in.get(i - 3);
            final int c0 = 0xFF & in.get(i - 4);

            rans3 = Encoding.RansEncPutSymbol(rans3, ptr, syms[c3]);
            rans2 = Encoding.RansEncPutSymbol(rans2, ptr, syms[c2]);
            rans1 = Encoding.RansEncPutSymbol(rans1, ptr, syms[c1]);
            rans0 = Encoding.RansEncPutSymbol(rans0, ptr, syms[c0]);
        }

        ptr.putInt(rans3);
        ptr.putInt(rans2);
        ptr.putInt(rans1);
        ptr.putInt(rans0);
        ptr.flip();
        cdata_size = ptr.limit();
        // reverse the compressed bytes, so that they become in REVERSE
        // order:
        Utils.reverse(ptr);
        in.position(in.limit());
        return cdata_size;
    }
}
