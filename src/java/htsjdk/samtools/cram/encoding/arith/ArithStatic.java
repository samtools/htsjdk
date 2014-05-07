package htsjdk.samtools.cram.encoding.arith;

import static org.junit.Assert.assertArrayEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class ArithStatic {

	private static final int TOP = (1 << 23);
	private static final int TF_SHIFT = 16;
	private static final int TOTFREQ = (1 << TF_SHIFT);
	private static final double DBL_EPSILON = 2.2204460492503131e-16D;
	private static final long RANGE_INIT = Integer.MAX_VALUE;

	private static class RngCoder {
		long low;
		int range;
		int code;
		ByteBuffer buf;
	}

	double[] RC_recip = new double[65536];

	void RC_init() {
		for (int i = 0; i < 65536; i++)
			RC_recip[i] = (1.0 + DBL_EPSILON) / i;
	}

	static void RC_input(RngCoder rc, ByteBuffer in) {
		rc.buf = in.duplicate();
	}

	static void RC_output(RngCoder rc, ByteBuffer out) {
		rc.buf = out.duplicate();
	}

	static void RC_StartEncode(RngCoder rc) {
		rc.low = 0;
		rc.range = (int) RANGE_INIT;
	}

	static void RC_StartDecode(RngCoder rc) {
		rc.code = 0;
		rc.low = 0;
		rc.range = (int) RANGE_INIT;
		for (int i = 0; i < 7; i++)
			rc.code = (rc.code << 8) | (0xFF & rc.buf.get());
	}

	static void RC_FinishEncode(RngCoder rc) {
		for (int i = 0; i < 8; i++) {
			rc.buf.put((byte) (0xFF & (rc.low >> 48)));
			rc.low <<= 8;
		}
		rc.buf.flip();
	}

	static void RC_FinishDecode(RngCoder rc) {
	}

	static void RC_Encode(RngCoder rc, int cumFreq, int freq) {
		rc.low += cumFreq * (rc.range >>= TF_SHIFT);
		rc.range *= freq;

		if (cumFreq + freq > TOTFREQ)
			throw new RuntimeException();

		while (rc.range < TOP) {
			if (0 != ((rc.low ^ (rc.low + rc.range)) >> 48))
				rc.range = (((int) rc.low | (TOP - 1)) - (int) rc.low);

			rc.buf.put((byte) (0xFF & (rc.low >> 48)));
			rc.range <<= 8;
			rc.low <<= 8;
		}
	}

	static int RC_GetFreq(RngCoder rc) {
		return ((rc.code / (rc.range >>= TF_SHIFT)));
	}

	static void RC_Decode(RngCoder rc, int cumFreq, int freq) {
		int temp = (cumFreq * rc.range);
		rc.low += temp;
		rc.code -= temp;
		rc.range *= freq;

		while (rc.range < TOP) {
			if (0 != ((rc.low ^ (rc.low + rc.range)) >> 48))
				rc.range = (((int) rc.low | (TOP - 1)) - (int) rc.low);
			rc.code = (rc.code << 8) | (0xFF & rc.buf.get());
			rc.range <<= 8;
			rc.low <<= 8;
		}
	}

	void arith_compress_O0(ByteBuffer in, ByteBuffer out) {
		int[] F = new int[256], C = new int[256];
		RngCoder[] rc = new RngCoder[4];
		for (int i = 0; i < rc.length; i++)
			rc[i] = new RngCoder();
		int in_size = in.remaining();
		int T = 0, j, n;

		// Compute statistics
		Arrays.fill(F, 0);
		Arrays.fill(C, 0);
		while (in.hasRemaining()) {
			F[in.get()]++;
			T++;
		}
		in.rewind();

		// Normalise so T[i] == 65536
		for (n = j = 0; j < 256; j++)
			if (F[j] != 0)
				n++;

		for (j = 0; j < 256; j++) {
			if (F[j] == 0)
				continue;
			if ((F[j] *= ((double) TOTFREQ - n) / T) == 0)
				F[j] = 1;
		}

		// Encode statistis.
		out.putInt(in_size);
		for (T = j = 0; j < 256; j++) {
			C[j] = T;
			T += F[j];
			if (F[j] != 0) {
				out.put((byte) (0xFF & j));
				out.put((byte) (F[j] >> 8));
				out.put((byte) (F[j] & 0xff));
				// System.out.printf("j=%d, F[j]=%d\n", j, F[j]);
			}
		}
		out.put((byte) 0);

		RC_output(rc[0], ByteBuffer.allocate(in_size));
		// RC_output(rc[1], ByteBuffer.allocate(BLK_SIZE));
		// RC_output(rc[2], ByteBuffer.allocate(BLK_SIZE));
		// RC_output(rc[3], ByteBuffer.allocate(BLK_SIZE));

		RC_StartEncode(rc[0]);
		// RC_StartEncode(rc[1]);
		// RC_StartEncode(rc[2]);
		// RC_StartEncode(rc[3]);

		while (in.hasRemaining()) {
			byte[] cc = new byte[4];
			cc[0] = in.get();
			// cc[1] = in.get();
			// cc[2] = in.get();
			// cc[3] = in.get();

			RC_Encode(rc[0], C[cc[0]], F[cc[0]]);
			// RC_Encode(rc[1], C[cc[1]], F[cc[1]]);
			// RC_Encode(rc[2], C[cc[2]], F[cc[2]]);
			// RC_Encode(rc[3], C[cc[3]], F[cc[3]]);
		}
		in.rewind();

		RC_FinishEncode(rc[0]);
		// RC_FinishEncode(rc[1]);
		// RC_FinishEncode(rc[2]);
		// RC_FinishEncode(rc[3]);

		out.putInt(rc[0].buf.limit());
		out.put(rc[0].buf);
		// out.putInt(RC_size_out(rc[1]));
		// out.put(rc[1].out_buf);
		// out.putInt(RC_size_out(rc[2]));
		// out.put(rc[2].out_buf);
		// out.putInt(RC_size_out(rc[3]));
		// out.put(rc[3].out_buf);
	}

	private static class FC {
		int F, C;
	}

	private static class ari_decoder {
		FC[] fc = new FC[256];
		byte[] R;
	}

	void arith_uncompress_O0(ByteBuffer in, ByteBuffer out_buf) {
		/* Load in the static tables */
		int i, j, x, out_sz;
		RngCoder[] rc = new RngCoder[4];
		int sz;
		ari_decoder D = new ari_decoder();
		for (int k = 0; k < D.fc.length; k++)
			D.fc[k] = new FC();

		ByteBuffer cp = in.duplicate();
		out_sz = cp.getInt();

		// Precompute reverse lookup of frequency.
		{
			j = cp.get();
			x = 0;
			do {
				D.fc[j].F = ((0xFF & cp.get()) << 8) | (0xFF & cp.get());
				D.fc[j].C = x;
				// System.out.printf("j=%d, C=%d, D=%d\n", j, D.fc[j].C,
				// D.fc[j].F);

				/* Build reverse lookup table */
				if (D.R == null)
					D.R = new byte[TOTFREQ];
				if (D.fc[j].F > -1)
					Arrays.fill(D.R, x, x + D.fc[j].F, (byte) (0xFF & j));

				x += D.fc[j].F;
				j = cp.get();
			} while (j != 0);
		}

		{

			for (int index = 0; index < 1; index++) {
				sz = cp.getInt();
				ByteBuffer buf = cp.slice();
				buf.limit(sz);

				rc[index] = new RngCoder();
				RC_input(rc[index], buf);
				RC_StartDecode(rc[index]);
			}

			for (i = 0; i < out_sz; i += 1) {
				int[] freq = new int[4];
				byte[] c = new byte[4];

				freq[0] = RC_GetFreq(rc[0]);
				// freq[1] = RC_GetFreq(rc[1]);
				// freq[2] = RC_GetFreq(rc[2]);
				// freq[3] = RC_GetFreq(rc[3]);

				c[0] = D.R[freq[0]];
				// c[1] = D.R[freq[1]];
				// c[2] = D.R[freq[2]];
				// c[3] = D.R[freq[3]];

				RC_Decode(rc[0], D.fc[c[0]].C, D.fc[c[0]].F);
				// RC_Decode(rc[1], D.fc[c[1]].C, D.fc[c[1]].F);
				// RC_Decode(rc[2], D.fc[c[2]].C, D.fc[c[2]].F);
				// RC_Decode(rc[3], D.fc[c[3]].C, D.fc[c[3]].F);

				out_buf.put(c[0]);
				// System.out.printf("i=%d, c=%d\n", i, c[0]);
			}
			RC_FinishDecode(rc[0]);
			// RC_FinishDecode(rc[1]);
			// RC_FinishDecode(rc[2]);
			// RC_FinishDecode(rc[3]);
		}
	}

	private static void test() {
		ArithStatic as = new ArithStatic();
		byte[] data = new byte[1 * 1000 * 1000];
		Random random = new Random();
		for (int i = 0; i < data.length; i++)
			data[i] = (byte) (random.nextInt(30) + 'A');
		ByteBuffer inBuf = ByteBuffer.wrap(data);
		ByteBuffer outBuf = ByteBuffer
				.allocate(2 * data.length + 256 * 256 * 2);

		int nofTests = 100;
		long start = System.nanoTime();
		for (int i = 0; i < nofTests; i++) {

			inBuf.rewind();
			outBuf.clear();
			as.arith_compress_O0(inBuf, outBuf);
		}
		long end = System.nanoTime();
		outBuf.flip();
		System.out.println("Compressed size: " + outBuf.limit());
		System.out.printf("time %.2f ms.\n", (end - start) / 1000000f);

		ByteBuffer uncBuf = ByteBuffer.allocate(outBuf.capacity());
		start = System.nanoTime();
		for (int i = 0; i < nofTests; i++) {
			uncBuf.clear();
			outBuf.rewind();
			as.arith_uncompress_O0(outBuf, uncBuf);
		}
		end = System.nanoTime();
		System.out.printf("time %.2f ms.\n", (end - start) / 1000000f);
		uncBuf.flip();
		byte[] unc = new byte[uncBuf.limit()];
		uncBuf.get(unc);

		assertArrayEquals(data, unc);
		System.out.println("Over");
	}
}
