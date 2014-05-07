package htsjdk.samtools.cram.encoding.arith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class Test_rANS {

	private static byte[] generateRandomData(int size) {
		byte[] data = new byte[size];
		Random random = new Random();
		// random.nextBytes(data);
		for (int i = 0; i < size; i++)
			data[i] = (byte) random.nextInt(40);
		return data;
	}

	public static void main(String[] args) {
		byte[] data = generateRandomData(1000 * 1000 * 10);
		ByteBuffer inBuf = ByteBuffer.wrap(data);
		ByteBuffer compBuf = ByteBuffer.allocate(data.length * 2 + 100000);
		ByteBuffer uncBuf = ByteBuffer.allocate(data.length);
		inBuf.order(ByteOrder.LITTLE_ENDIAN);
		compBuf.order(ByteOrder.LITTLE_ENDIAN);
		uncBuf.order(ByteOrder.LITTLE_ENDIAN);

		for (int i = 0; i < 100; i++) {
			for (int order : new int[] { 1 }) {
				for (int way : new int[] { 4 }) {
					inBuf.rewind();
					compBuf.clear();
					uncBuf.clear();
					test(order, way, inBuf, compBuf, uncBuf);
				}
			}
		}
	}

	private static void test(int order, int way, ByteBuffer inBuf, ByteBuffer compBuf, ByteBuffer uncBuf) {
		long eStart = 0, eEnd = 0, dStart = 0, dEnd = 0;
		int in_size = inBuf.remaining();

		if (order == 0) {
			rANS_Encoder0 e0 = new rANS_Encoder0();
			eStart = System.nanoTime();
			e0.rans_compress_O0(inBuf, compBuf);
			eEnd = System.nanoTime();

			// start = System.nanoTime() ;
			// e0.rans_compress_O0(compBuf, compBuf) ;
			// end = System.nanoTime() ;
		} else {

			switch (way) {
			case 1:
				rANS_Encoder1_1way e1w1 = new rANS_Encoder1_1way();
				eStart = System.nanoTime();
				e1w1.rans_compress_O1(inBuf, compBuf);
				eEnd = System.nanoTime();

				rANS_Decoder1_1way d1w1 = new rANS_Decoder1_1way();
				dStart = System.nanoTime();
				d1w1.rans_uncompress_O1(compBuf, uncBuf);
				dEnd = System.nanoTime();
				break;
			case 4:
				rANS_Encoder1_4way e1w4 = new rANS_Encoder1_4way();
				eStart = System.nanoTime();
				e1w4.rans_compress_O1(inBuf, compBuf);
				eEnd = System.nanoTime();

				rANS_Decoder1_4way d1w4 = new rANS_Decoder1_4way();
				dStart = System.nanoTime();
				d1w4.rans_uncompress_O1(compBuf, uncBuf);
				dEnd = System.nanoTime();
				break;

			case 8:
				rANS_Encoder1 e1w8 = new rANS_Encoder1();
				eStart = System.nanoTime();
				e1w8.rans_compress_O1(inBuf, compBuf);
				eEnd = System.nanoTime();

				rANS_Decoder1 d1w8 = new rANS_Decoder1();
				dStart = System.nanoTime();
				d1w8.rans_uncompress_O1(compBuf, uncBuf);
				dEnd = System.nanoTime();
				break;

			default:
				break;
			}
		}
		System.out.printf("Order %d, %d-way:\t%.2fMB/s\t%.2fMB/s.\n", order, way, 1000f * in_size / (eEnd - eStart),
				1000f * in_size / (dEnd - dStart));
	}
}
