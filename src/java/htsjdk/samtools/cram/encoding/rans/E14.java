package htsjdk.samtools.cram.encoding.rans;

import htsjdk.samtools.cram.encoding.rans.Encoding.RansEncSymbol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@SuppressWarnings("UnusedAssignment")
class E14 {

    static int compress(final ByteBuffer in, final RansEncSymbol[][] syms,
                        final ByteBuffer out_buf) {
        final int in_size = in.remaining();
        final int compressedBlob_size;
        int rans0, rans1, rans2, rans3;
        rans0 = Constants.RANS_BYTE_L;
        rans1 = Constants.RANS_BYTE_L;
        rans2 = Constants.RANS_BYTE_L;
        rans3 = Constants.RANS_BYTE_L;

		/*
         * Slicing is needed for buffer reversing later.
		 */
        final ByteBuffer ptr = out_buf.slice();

        final int isz4 = in_size >> 2;
        int i0 = isz4 - 2;
        int i1 = 2 * isz4 - 2;
        int i2 = 3 * isz4 - 2;
        int i3 = 4 * isz4 - 2;

        int l0 = 0;
        if (i0 + 1 >= 0)
            l0 = 0xFF & in.get(i0 + 1);
        int l1 = 0;
        if (i1 + 1 >= 0)
            l1 = 0xFF & in.get(i1 + 1);
        int l2 = 0;
        if (i2 + 1 >= 0)
            l2 = 0xFF & in.get(i2 + 1);
        int l3;

        // Deal with the remainder
        l3 = 0xFF & in.get(in_size - 1);
        for (i3 = in_size - 2; i3 > 4 * isz4 - 2 && i3 >= 0; i3--) {
            final int c3 = 0xFF & in.get(i3 > -1 ? i3 : 0);
            rans3 = Encoding.RansEncPutSymbol(rans3, ptr, syms[c3][l3]);
            l3 = c3;
        }

        for (; i0 >= 0; i0--, i1--, i2--, i3--) {
            final int c0 = 0xFF & in.get(i0);
            final int c1 = 0xFF & in.get(i1);
            final int c2 = 0xFF & in.get(i2);
            final int c3 = 0xFF & in.get(i3);

            rans3 = Encoding.RansEncPutSymbol(rans3, ptr, syms[c3][l3]);
            rans2 = Encoding.RansEncPutSymbol(rans2, ptr, syms[c2][l2]);
            rans1 = Encoding.RansEncPutSymbol(rans1, ptr, syms[c1][l1]);
            rans0 = Encoding.RansEncPutSymbol(rans0, ptr, syms[c0][l0]);

            l0 = c0;
            l1 = c1;
            l2 = c2;
            l3 = c3;
        }

        rans3 = Encoding.RansEncPutSymbol(rans3, ptr, syms[0][l3]);
        rans2 = Encoding.RansEncPutSymbol(rans2, ptr, syms[0][l2]);
        rans1 = Encoding.RansEncPutSymbol(rans1, ptr, syms[0][l1]);
        rans0 = Encoding.RansEncPutSymbol(rans0, ptr, syms[0][l0]);

        ptr.order(ByteOrder.BIG_ENDIAN);
        ptr.putInt(rans3);
        ptr.putInt(rans2);
        ptr.putInt(rans1);
        ptr.putInt(rans0);
        ptr.flip();
        compressedBlob_size = ptr.limit();
        Utils.reverse(ptr);
        /*
         * Depletion of the in buffer cannot be confirmed because of the get(int
		 * position) method use during encoding, hence enforcing:
		 */
        in.position(in.limit());
        return compressedBlob_size;
    }
}
