package htsjdk.samtools.cram.encoding.arith;

import static htsjdk.samtools.cram.encoding.arith.rans_byte.RansDecAdvanceSymbol;
import static htsjdk.samtools.cram.encoding.arith.rans_byte.RansDecAdvanceSymbolStep;
import static htsjdk.samtools.cram.encoding.arith.rans_byte.RansDecGet;
import static htsjdk.samtools.cram.encoding.arith.rans_byte.RansDecRenorm;
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
import java.util.Arrays;

public class rANS_Decoder1_4way {
	static int TF_SHIFT = 14;
	static int TOTFREQ = (1 << TF_SHIFT);

	static int BLK_SIZE = 1000000;

	// Room to allow for expanded BLK_SIZE on worst case compression.
	static int BLK_SIZE2 = ((int) (1.05 * BLK_SIZE));

	void readStats(ByteBuffer cp, Decoder0.ari_decoder[] D, RansSymbol[][] syms) {
		int i = 0xFF & cp.get();
		int j = 0, x = 0;
		do {
			j = 0xFF & cp.get();
			x = 0;
			if (D[i] == null)
				D[i] = new Decoder0.ari_decoder();
			do {
				if (D[i].fc[j] == null)
					D[i].fc[j] = new Decoder0.FC();
				D[i].fc[j].F = ((cp.get() & 0xFF) << 8) | (cp.get() & 0xFF);
				D[i].fc[j].C = x;

				// System.out.printf("i=%d j=%d F=%d C=%d\n", i, j,
				// D[i].fc[j].F, D[i].fc[j].C);

				if (D[i].fc[j].F == 0)
					D[i].fc[j].F = TOTFREQ;

				if (syms[i][j] == null)
					syms[i][j] = new RansSymbol();
				rans_byte.RansSymbolInit(syms[i][j], D[i].fc[j].C, D[i].fc[j].F);

				/* Build reverse lookup table */
				if (D[i].R == null)
					D[i].R = new byte[TOTFREQ];
				Arrays.fill(D[i].R, x, x + D[i].fc[j].F, (byte) j);

				x += D[i].fc[j].F;
				j = 0xFF & cp.get();
			} while (j != 0);

			i = 0xFF & cp.get();
		} while (i != 0);
	}

	ByteBuffer rans_uncompress_O1(ByteBuffer in, ByteBuffer out_buf) {
		int out_sz = out_buf.remaining();

		/* Load in the static tables */
		ByteBuffer cp = in.slice();
		Decoder0.ari_decoder[] D = new Decoder0.ari_decoder[256];
		RansSymbol[][] syms = new RansSymbol[256][256];
		for (int i = 0; i < syms.length; i++)
			for (int j = 0; j < syms[i].length; j++)
				syms[i][j] = new RansSymbol();
		readStats(cp, D, syms);

		// Precompute reverse lookup of frequency.

		int rans0, rans1, rans2, rans7;
		ByteBuffer ptr = cp.slice();
		ptr.order(ByteOrder.LITTLE_ENDIAN);
		rans0 = rans_byte.RansDecInit(ptr);
		rans1 = rans_byte.RansDecInit(ptr);
		rans2 = rans_byte.RansDecInit(ptr);
		rans7 = rans_byte.RansDecInit(ptr);

		// System.out.printf("rans: %d, %d, %d, %d, %d, %d, %d\n", rans0, rans1,
		// rans2, rans3, rans4, rans5, rans6, rans7);

		int isz4 = out_sz >> 2;
		int i0 = 0 * isz4;
		int i1 = 1 * isz4;
		int i2 = 2 * isz4;
		int i7 = 3 * isz4;
		int l0 = 0;
		int l1 = 0;
		int l2 = 0;
		int l7 = 0;
		for (; i0 < isz4; i0++, i1++, i2++, i7++) {
			int c0 = 0xFF & D[l0].R[RansDecGet(rans0, TF_SHIFT)];
			int c1 = 0xFF & D[l1].R[RansDecGet(rans1, TF_SHIFT)];
			int c2 = 0xFF & D[l2].R[RansDecGet(rans2, TF_SHIFT)];
			int c7 = 0xFF & D[l7].R[RansDecGet(rans7, TF_SHIFT)];
			// System.out.printf("%d, %d, %d, %d, %d, %d, %d\n", c0, c1, c2, c3,
			// c4, c5, c6, c7);

			out_buf.put(i0, (byte) c0);
			out_buf.put(i1, (byte) c1);
			out_buf.put(i2, (byte) c2);
			out_buf.put(i7, (byte) c7);

			rans0 = RansDecAdvanceSymbolStep(rans0, syms[l0][c0], TF_SHIFT);
			rans1 = RansDecAdvanceSymbolStep(rans1, syms[l1][c1], TF_SHIFT);
			rans2 = RansDecAdvanceSymbolStep(rans2, syms[l2][c2], TF_SHIFT);
			rans7 = RansDecAdvanceSymbolStep(rans7, syms[l7][c7], TF_SHIFT);
			// System.out.printf("rans: %d, %d, %d, %d, %d, %d, %d\n", rans0,
			// rans1, rans2, rans3, rans4, rans5, rans6,
			// rans7);

			rans0 = RansDecRenorm(rans0, ptr);
			rans1 = RansDecRenorm(rans1, ptr);
			rans2 = RansDecRenorm(rans2, ptr);
			rans7 = RansDecRenorm(rans7, ptr);

			l0 = c0;
			l1 = c1;
			l2 = c2;
			l7 = c7;
		}

		// Remainder
		for (; i7 < out_sz; i7++) {
			int c7 = 0xFF & D[l7].R[RansDecGet(rans7, TF_SHIFT)];
			out_buf.put(i7, (byte) c7);
			rans7 = RansDecAdvanceSymbol(rans7, ptr, syms[l7][c7], TF_SHIFT);
			l7 = c7;
		}

		return out_buf;
	}

	private static void test(byte[] data) {
		ByteBuffer out = new rANS_Decoder1_4way().rans_uncompress_O1(ByteBuffer.wrap(data), null);
		byte[] output = new byte[out.limit()];
		out.get(output);
		System.out.println(Arrays.toString(output));
	}

	public static void main(String[] args) throws IOException {
		File inFile = new File(args[0]);
		File outFile = new File(args[1]);

		byte[] inBuf = new byte[BLK_SIZE * 2];
		byte[] outBuf = new byte[inBuf.length * 10];
		ByteBuffer out = ByteBuffer.wrap(outBuf);
		InputStream is = new BufferedInputStream(new FileInputStream(inFile));
		OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile));
		long start = System.nanoTime();
		while (true) {
			int order = is.read();
			if (order == -1)
				break;
			int in_size = ByteBufferUtils.int32(is) - 4;
			int out_size = ByteBufferUtils.int32(is);
			if (in_size != ByteBufferUtils.readFully(inBuf, in_size, 0, is))
				throw new RuntimeException("Input is incomplete.");

			ByteBuffer buf = ByteBuffer.wrap(inBuf, 0, in_size);

			out.clear();
			out.limit(out_size);
			new rANS_Decoder1_4way().rans_uncompress_O1(buf, out);
			os.write(outBuf, 0, out.limit());
		}
		os.close();
		is.close();
		long end = System.nanoTime();

		System.out.printf("Took %d microseconds, %5.1f MB/s\n", (end - start) / 1000, (double) inFile.length()
				/ ((end - start) / 1000f));
	}
}
