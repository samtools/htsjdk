package htsjdk.samtools.cram.encoding.arith;

import static org.junit.Assert.assertArrayEquals;

import java.nio.ByteBuffer;
import java.util.Random;

public class RngCoder {
	private static final int TOP = (1 << 23);
	private static final int TF_SHIFT = 16;
	// private static final int TOTFREQ = (1 << TF_SHIFT);
	private static final long RANGE_INIT = Integer.MAX_VALUE;

	private long low;
	private int range;
	private int code;
	ByteBuffer buf;

	double[] RC_recip = new double[65536];

	void RC_input(ByteBuffer in) {
		buf = in.duplicate();
	}

	void RC_output(ByteBuffer out) {
		buf = out.duplicate();
	}

	void RC_StartEncode() {
		low = 0;
		range = (int) RANGE_INIT;
	}

	void RC_StartDecode() {
		code = 0;
		low = 0;
		range = (int) RANGE_INIT;
		for (int i = 0; i < 7; i++)
			code = (code << 8) | (0xFF & buf.get());
	}

	void RC_FinishEncode() {
		for (int i = 0; i < 8; i++) {
			buf.put((byte) (0xFF & (low >> 48)));
			low <<= 8;
		}
		buf.flip();
	}

	void RC_FinishDecode() {
	}

	void RC_Encode(int cumFreq, int freq) {
		low += cumFreq * (range >>= TF_SHIFT);
		range *= freq;

		// if (cumFreq + freq > TOTFREQ)
		// throw new RuntimeException();

		while (range < TOP) {
			if (0 != ((low ^ (low + range)) >> 48))
				range = (((int) low | (TOP - 1)) - (int) low);

			buf.put((byte) (0xFF & (low >> 48)));
			range <<= 8;
			low <<= 8;
		}
	}

	int RC_GetFreq() {
		return ((code / (range >>= TF_SHIFT)));
	}

	void RC_Decode(int cumFreq, int freq) {
		int temp = (cumFreq * range);
		low += temp;
		code -= temp;
		range *= freq;

		while (range < TOP) {
			if (0 != ((low ^ (low + range)) >> 48))
				range = (((int) low | (TOP - 1)) - (int) low);
			code = (code << 8) | (0xFF & buf.get());
			range <<= 8;
			low <<= 8;
		}
	}

	public static void main(String[] args) {
		Encoder0 e = new Encoder0();
		byte[] data = new byte[1 * 1000 * 1000];
		Random random = new Random();
		for (int i = 0; i < data.length; i++)
			data[i] = (byte) (random.nextInt(30) + 'A');
		ByteBuffer inBuf = ByteBuffer.wrap(data);
		ByteBuffer outBuf = ByteBuffer.allocate(2 * data.length + 256 * 256 * 2);

		int nofTests = 100;
		long start = System.nanoTime();
		for (int i = 0; i < nofTests; i++) {

			inBuf.rewind();
			outBuf.clear();
			e.arith_compress_O0(inBuf, outBuf);
		}
		long end = System.nanoTime();
		outBuf.flip();
		System.out.println("Compressed size: " + outBuf.limit());
		System.out.printf("time %.2f ms.\n", (end - start) / 1000000f);

		Decoder0 d = new Decoder0();
		ByteBuffer uncBuf = ByteBuffer.allocate(outBuf.capacity());
		start = System.nanoTime();
		for (int i = 0; i < nofTests; i++) {
			uncBuf.clear();
			outBuf.rewind();
			d.arith_uncompress_O0(outBuf, uncBuf);
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
