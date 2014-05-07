package htsjdk.samtools.cram.encoding.arith;

import java.nio.ByteBuffer;
import java.util.Arrays;

class Decoder0 {
	private static final int TF_SHIFT = 16;
	private static final int TOTFREQ = (1 << TF_SHIFT);

	public static class FC {
		public int F, C;
	}

	public static class ari_decoder {
		public FC[] fc = new FC[256];
		public byte[] R;
	}

	void arith_uncompress_O0(ByteBuffer in, ByteBuffer out_buf) {
		/* Load in the static tables */
		int codecs = 1;
		RngCoder[] rc = new RngCoder[codecs];

		ari_decoder D = new ari_decoder();
		for (int k = 0; k < D.fc.length; k++)
			D.fc[k] = new FC();

		ByteBuffer cp = in.duplicate();
		int out_sz = cp.getInt();

		// Precompute reverse lookup of frequency.
		{
			int j = cp.get();
			int x = 0;
			do {
				D.fc[j].F = ((0xFF & cp.get()) << 8) | (0xFF & cp.get());
				D.fc[j].C = x;

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
			int sz;
			for (int index = 0; index < rc.length; index++) {
				sz = cp.getInt();
				ByteBuffer buf = cp.slice();
				buf.limit(sz);

				rc[index] = new RngCoder();
				rc[index].RC_input(buf);
				rc[index].RC_StartDecode();
			}

			int[] freq = new int[rc.length];
			byte[] c = new byte[rc.length];
			final int index = 0;
			for (int i = 0; i < out_sz; i += rc.length) {
				// for (int index = 0; index < rc.length; index++)
				freq[index] = rc[index].RC_GetFreq();

				// for (int index = 0; index < rc.length; index++)
				c[index] = D.R[freq[index]];

				// for (int index = 0; index < rc.length; index++)
				rc[index].RC_Decode(D.fc[c[index]].C, D.fc[c[index]].F);

				// for (int index = 0; index < rc.length; index++)
				out_buf.put(c[index]);
			}

			// for (int index = 0; index < rc.length; index++)
			rc[index].RC_FinishDecode();
		}
	}
}
