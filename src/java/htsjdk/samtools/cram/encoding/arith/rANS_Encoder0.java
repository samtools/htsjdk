package htsjdk.samtools.cram.encoding.arith;

import htsjdk.samtools.cram.encoding.arith.rans_byte.RansSymbol;
import htsjdk.samtools.cram.io.ByteBufferUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class rANS_Encoder0 {
	static int TF_SHIFT = 14;
	static int TOTFREQ = (1 << TF_SHIFT);

	static int BLK_SIZE = 1000000;

	// Room to allow for expanded BLK_SIZE on worst case compression.
	static int BLK_SIZE2 = ((int) (1.05 * BLK_SIZE));

	/*-----------------------------------------------------------------------------
	 * Memory to memory compression functions.
	 *
	 * These are original versions without any manual loop unrolling. They
	 * are easier to understand, but can be up to 2x slower.
	 */

	public ByteBuffer rans_compress_O0(ByteBuffer in, ByteBuffer out_buf) {
		int in_size = in.remaining();
		if (out_buf == null)
			out_buf = ByteBuffer.allocate((int) (1.05 * in_size + 257 * 257 * 3 + 4));
		out_buf.position(1 + 4 + 4);

		RansSymbol[] syms = new RansSymbol[256];
		int rans0, rans1, rans2, rans3;
		int T = 0, i, j, tab_size;
		int[] C = new int[256];

		// Compute statistics
		int[] F = FrequencyTable.Order0_FT(in, TOTFREQ);

		// Encode statistics.
		// Not the most optimal encoding method, but simple
		ByteBuffer cp = out_buf.slice();

		for (T = j = 0; j < 256; j++) {
			C[j] = T;
			T += F[j];
			syms[j] = new RansSymbol();
			if (F[j] != 0) {
				cp.put((byte) j);
				cp.put((byte) (F[j] >> 8));
				cp.put((byte) (F[j] & 0xff));
				rans_byte.RansSymbolInit(syms[j], C[j], F[j]);
			}
		}

		cp.put((byte) 0);
		tab_size = cp.position();

		// output compressed bytes in FORWARD order:
		ByteBuffer ptr = cp.slice();

		rans0 = rans_byte.RansEncInit();
		rans1 = rans_byte.RansEncInit();
		rans2 = rans_byte.RansEncInit();
		rans3 = rans_byte.RansEncInit();

		switch (i = (in_size & 3)) {
		case 3:
			rans2 = rans_byte.RansEncPutSymbol(rans2, ptr, syms[0xFF & in.get(in_size - (i - 2))], TF_SHIFT);
		case 2:
			rans1 = rans_byte.RansEncPutSymbol(rans1, ptr, syms[0xFF & in.get(in_size - (i - 1))], TF_SHIFT);
		case 1:
			rans0 = rans_byte.RansEncPutSymbol(rans0, ptr, syms[0xFF & in.get(in_size - (i - 0))], TF_SHIFT);
		case 0:
			break;
		}
		for (i = (in_size & ~3); i > 0; i -= 4) {
			int c3 = 0xFF & in.get(i - 1);
			int c2 = 0xFF & in.get(i - 2);
			int c1 = 0xFF & in.get(i - 3);
			int c0 = 0xFF & in.get(i - 4);

			rans3 = rans_byte.RansEncPutSymbol(rans3, ptr, syms[c3], TF_SHIFT);
			rans2 = rans_byte.RansEncPutSymbol(rans2, ptr, syms[c2], TF_SHIFT);
			rans1 = rans_byte.RansEncPutSymbol(rans1, ptr, syms[c1], TF_SHIFT);
			rans0 = rans_byte.RansEncPutSymbol(rans0, ptr, syms[c0], TF_SHIFT);
		}

		ptr.putInt(rans3);
		ptr.putInt(rans2);
		ptr.putInt(rans1);
		ptr.putInt(rans0);
		ptr.flip();
		int cdata_size = ptr.limit();
		// reverse the compressed bytes, so that they become in REVERSE order:
		ByteBufferUtils.reverse(ptr);

		// Finalise block size and return it
		out_buf.limit(1 + 4 + 4 + tab_size + cdata_size);
		out_buf.put(0, (byte) 0);
		ByteOrder byteOrder = out_buf.order();
		out_buf.order(ByteOrder.LITTLE_ENDIAN);
		out_buf.putInt(1, out_buf.limit() - 5);
		out_buf.putInt(5, in_size);
		out_buf.order(byteOrder);
		out_buf.position(0);
		return out_buf;
	}

	public static void main(String[] args) throws IOException {
		File inFile = new File(args[0]);
		File outFile = new File(args[1]);

		byte[] inBuf = new byte[BLK_SIZE];
		byte[] outBuf = new byte[inBuf.length * 2];
		ByteBuffer out = ByteBuffer.wrap(outBuf);
		InputStream is = new BufferedInputStream(new FileInputStream(inFile));
		OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile));
		long start = System.nanoTime();
		while (true) {
			int len = is.read(inBuf);
			if (len == -1)
				break;
			out.clear();
			new rANS_Encoder0().rans_compress_O0(ByteBuffer.wrap(inBuf, 0, len), out);
			os.write(outBuf, 0, out.limit());
		}
		os.close();
		is.close();
		long end = System.nanoTime();

		System.out.printf("Took %d microseconds, %5.1f MB/s\n", (end - start) / 1000, (double) inFile.length()
				/ ((end - start) / 1000f));
	}
}
