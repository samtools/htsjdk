package htsjdk.samtools.cram.encoding.arith;

import java.nio.ByteBuffer;
import java.util.Arrays;

class Encoder0 {
	private static final int TF_SHIFT = 16;
	private static final int TOTFREQ = (1 << TF_SHIFT);

	void arith_compress_O0(ByteBuffer in, ByteBuffer out) {
		int in_size = in.remaining();
		int codecs = 1;
		RngCoder[] rc = new RngCoder[codecs];
		for (int i = 0; i < rc.length; i++) {
			rc[i] = new RngCoder();
			rc[i].RC_output(ByteBuffer.allocate(in_size));
			rc[i].RC_StartEncode();
		}

		int[] F = new int[256], C = new int[256];

		{
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
				}
			}
			out.put((byte) 0);
		}

		byte[] cc = new byte[rc.length];
		while (in.hasRemaining()) {
			for (int index = 0; index < rc.length; index++)
				cc[index] = in.get();

			for (int index = 0; index < rc.length; index++)
				rc[index].RC_Encode(C[cc[index]], F[cc[index]]);
		}
		in.rewind();

		for (int index = 0; index < rc.length; index++) {
			rc[index].RC_FinishEncode();
			out.putInt(rc[index].buf.limit());
			out.put(rc[index].buf);
		}
	}
}
