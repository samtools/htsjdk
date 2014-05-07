package htsjdk.samtools.cram.encoding.arith;

import java.nio.ByteBuffer;

class FrequencyTable {
	public static final int[] Order0_FT(ByteBuffer data, int TOTFREQ) {
		int[] F = new int[256];
		int T = 0;
		while (data.hasRemaining()) {
			F[data.get() & 0xFF]++;
			T++;
		}

		// Normalise so T[i] == 65536
		int n, j;
		for (n = j = 0; j < 256; j++)
			if (F[j] != 0)
				n++;

		for (j = 0; j < 256; j++) {
			if (F[j] == 0)
				continue;
			if ((F[j] *= ((double) TOTFREQ - n) / T) == 0)
				F[j] = 1;
		}
		return F;
	}

	public static final int[][] Order1_FT(ByteBuffer data, int TOTFREQ, int slices, int[] T) {
		int[][] F = new int[256][256];

		// Normalise so T[i] == 65536
		int j, i, last, c;
		int in_size = data.remaining();
		for (last = i = 0; i < in_size; i++) {
			c = data.get() & 0xFF;
			F[last][c]++;
			T[last]++;
			last = c;
		}

		for (int s = 1; s < slices; s++) {
			F[0][0xFF & data.get(s * (in_size / slices))]++;
		}
		T[0] += slices - 1;

		// Normalise so T[i] == 65536
		for (i = 0; i < 256; i++) {
			int t = T[i], t2, n;

			if (t == 0)
				continue;

			for (n = j = 0; j < 256; j++)
				if (F[i][j] != 0)
					n++;

			for (t2 = j = 0; j < 256; j++) {
				if (F[i][j] == 0)
					continue;
				if ((F[i][j] *= ((double) TOTFREQ - n) / t) == 0)
					F[i][j] = 1;
				t2 += F[i][j];
			}

			assert (t2 <= TOTFREQ);
		}
		return F;
	}
}
