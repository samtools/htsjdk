/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ByteBufferUtils {

	public static final int readUnsignedITF8(InputStream is) throws IOException {
		int b1 = is.read();
		if (b1 == -1)
			throw new EOFException();

		if ((b1 & 128) == 0)
			return b1;

		if ((b1 & 64) == 0)
			return ((b1 & 127) << 8) | is.read();

		if ((b1 & 32) == 0) {
			int b2 = is.read();
			int b3 = is.read();
			return ((b1 & 63) << 16) | b2 << 8 | b3;
		}

		if ((b1 & 16) == 0)
			return ((b1 & 31) << 24) | is.read() << 16 | is.read() << 8 | is.read();

		return ((b1 & 15) << 28) | is.read() << 20 | is.read() << 12 | is.read() << 4 | (15 & is.read());
	}

	public static final int writeUnsignedITF8(int value, OutputStream os) throws IOException {
		if ((value >>> 7) == 0) {
			os.write(value);
			return 8;
		}

		if ((value >>> 14) == 0) {
			os.write(((value >> 8) | 128));
			os.write((value & 0xFF));
			return 16;
		}

		if ((value >>> 21) == 0) {
			os.write(((value >> 16) | 192));
			os.write(((value >> 8) & 0xFF));
			os.write((value & 0xFF));
			return 24;
		}

		if ((value >>> 28) == 0) {
			os.write(((value >> 24) | 224));
			os.write(((value >> 16) & 0xFF));
			os.write(((value >> 8) & 0xFF));
			os.write((value & 0xFF));
			return 32;
		}

		os.write(((value >> 28) | 240));
		os.write(((value >> 20) & 0xFF));
		os.write(((value >> 12) & 0xFF));
		os.write(((value >> 4) & 0xFF));
		os.write((value & 0xFF));
		return 40;
	}

	public static final long readUnsignedLTF8(InputStream is) throws IOException {
		int b1 = is.read();
		if (b1 == -1)
			throw new EOFException();

		if ((b1 & 128) == 0)
			return b1;

		if ((b1 & 64) == 0)
			return ((b1 & 127) << 8) | is.read();

		if ((b1 & 32) == 0) {
			int b2 = is.read();
			int b3 = is.read();
			return ((b1 & 63) << 16) | b2 << 8 | b3;
		}

		if ((b1 & 16) == 0) {
			long result = ((long) (b1 & 31) << 24);
			result |= is.read() << 16;
			result |= is.read() << 8;
			result |= is.read();
			return result;
		}

		if ((b1 & 8) == 0) {
			long value = ((long) (b1 & 15) << 32);
			value |= ((0xFF & ((long) is.read())) << 24);
			value |= (is.read() << 16);
			value |= (is.read() << 8);
			value |= is.read();
			return value;
		}

		if ((b1 & 4) == 0) {
			long result = ((long) (b1 & 7) << 40);
			result |= (0xFF & ((long) is.read())) << 32;
			result |= (0xFF & ((long) is.read())) << 24;
			result |= is.read() << 16;
			result |= is.read() << 8;
			result |= is.read();
			return result;
		}

		if ((b1 & 2) == 0) {
			long result = ((long) (b1 & 3) << 48);
			result |= (0xFF & ((long) is.read())) << 40;
			result |= (0xFF & ((long) is.read())) << 32;
			result |= (0xFF & ((long) is.read())) << 24;
			result |= is.read() << 16;
			result |= is.read() << 8;
			result |= is.read();
			return result;
		}

		if ((b1 & 1) == 0) {
			long result = (0xFF & ((long) is.read())) << 48;
			result |= (0xFF & ((long) is.read())) << 40;
			result |= (0xFF & ((long) is.read())) << 32;
			result |= (0xFF & ((long) is.read())) << 24;
			result |= is.read() << 16;
			result |= is.read() << 8;
			result |= is.read();
			return result;
		}

		long result = (0xFF & ((long) is.read())) << 56;
		result |= (0xFF & ((long) is.read())) << 48;
		result |= (0xFF & ((long) is.read())) << 40;
		result |= (0xFF & ((long) is.read())) << 32;
		result |= (0xFF & ((long) is.read())) << 24;
		result |= is.read() << 16;
		result |= is.read() << 8;
		result |= is.read();
		return result;
	}

	public static final int writeUnsignedLTF8(long value, OutputStream os) throws IOException {
		if ((value >>> 7) == 0) {
			// no contol bits
			os.write((int) value);
			return 8;
		}

		if ((value >>> 14) == 0) {
			// one control bit
			os.write((int) ((value >> 8) | 0x80));
			os.write((int) (value & 0xFF));
			return 16;
		}

		if ((value >>> 21) == 0) {
			// two control bits
			os.write((int) ((value >> 16) | 0xC0));
			os.write((int) ((value >> 8) & 0xFF));
			os.write((int) (value & 0xFF));
			return 24;
		}

		if ((value >>> 28) == 0) {
			// three control bits
			os.write((int) ((value >> 24) | 0xE0));
			os.write((int) ((value >> 16) & 0xFF));
			os.write((int) ((value >> 8) & 0xFF));
			os.write((int) (value & 0xFF));
			return 32;
		}

		if ((value >>> 35) == 0) {
			// four control bits
			os.write((int) ((value >> 32) | 0xF0));
			os.write((int) ((value >> 24) & 0xFF));
			os.write((int) ((value >> 16) & 0xFF));
			os.write((int) ((value >> 8) & 0xFF));
			os.write((int) (value & 0xFF));
			return 40;
		}

		if ((value >>> 42) == 0) {
			// five control bits
			os.write((int) ((value >> 40) | 0xF8));
			os.write((int) ((value >> 32) & 0xFF));
			os.write((int) ((value >> 24) & 0xFF));
			os.write((int) ((value >> 16) & 0xFF));
			os.write((int) ((value >> 8) & 0xFF));
			os.write((int) (value & 0xFF));
			return 48;
		}

		if ((value >>> 49) == 0) {
			// six control bits
			os.write((int) ((value >> 48) | 0xFC));
			os.write((int) ((value >> 40) & 0xFF));
			os.write((int) ((value >> 32) & 0xFF));
			os.write((int) ((value >> 24) & 0xFF));
			os.write((int) ((value >> 16) & 0xFF));
			os.write((int) ((value >> 8) & 0xFF));
			os.write((int) (value & 0xFF));
			return 56;
		}

		if ((value >>> 56) == 0) {
			// seven control bits
			os.write(0xFE);
			os.write((int) ((value >> 48) & 0xFF));
			os.write((int) ((value >> 40) & 0xFF));
			os.write((int) ((value >> 32) & 0xFF));
			os.write((int) ((value >> 24) & 0xFF));
			os.write((int) ((value >> 16) & 0xFF));
			os.write((int) ((value >> 8) & 0xFF));
			os.write((int) (value & 0xFF));
			return 64;
		}

		// eight control bits
		os.write((0xFF));
		os.write((int) ((value >> 56) & 0xFF));
		os.write((int) ((value >> 48) & 0xFF));
		os.write((int) ((value >> 40) & 0xFF));
		os.write((int) ((value >> 32) & 0xFF));
		os.write((int) ((value >> 28) & 0xFF));
		os.write((int) ((value >> 16) & 0xFF));
		os.write((int) ((value >> 8) & 0xFF));
		os.write((int) (value & 0xFF));
		return 72;
	}

	public static final int readUnsignedITF8(byte[] data) {
		ByteBuffer buf = ByteBuffer.wrap(data);
		int value = ByteBufferUtils.readUnsignedITF8(buf);
		buf.clear();

		return value;
	}

	public static final byte[] writeUnsignedITF8(int value) {
		ByteBuffer buf = ByteBuffer.allocate(10);
		ByteBufferUtils.writeUnsignedITF8(value, buf);

		buf.flip();
		byte[] array = new byte[buf.limit()];
		buf.get(array);

		buf.clear();
		return array;
	}

	public static final int readUnsignedITF8(ByteBuffer buf) {
		int b1 = 0xFF & buf.get();

		if ((b1 & 128) == 0)
			return b1;

		if ((b1 & 64) == 0)
			return ((b1 & 127) << 8) | (0xFF & buf.get());

		if ((b1 & 32) == 0) {
			int b2 = 0xFF & buf.get();
			int b3 = 0xFF & buf.get();
			return ((b1 & 63) << 16) | b2 << 8 | b3;
		}

		if ((b1 & 16) == 0)
			return ((b1 & 31) << 24) | (0xFF & buf.get()) << 16 | (0xFF & buf.get()) << 8 | (0xFF & buf.get());

		return ((b1 & 15) << 28) | (0xFF & buf.get()) << 20 | (0xFF & buf.get()) << 12 | (0xFF & buf.get()) << 4
				| (15 & buf.get());
	}

	public static final void writeUnsignedITF8(int value, ByteBuffer buf) {
		if ((value >>> 7) == 0) {
			buf.put((byte) value);
			return;
		}

		if ((value >>> 14) == 0) {
			buf.put((byte) ((value >> 8) | 128));
			buf.put((byte) (value & 0xFF));
			return;
		}

		if ((value >>> 21) == 0) {
			buf.put((byte) ((value >> 16) | 192));
			buf.put((byte) ((value >> 8) & 0xFF));
			buf.put((byte) (value & 0xFF));
			return;
		}

		if ((value >>> 28) == 0) {
			buf.put((byte) ((value >> 24) | 224));
			buf.put((byte) ((value >> 16) & 0xFF));
			buf.put((byte) ((value >> 8) & 0xFF));
			buf.put((byte) (value & 0xFF));
			return;
		}

		buf.put((byte) ((value >> 28) | 240));
		buf.put((byte) ((value >> 20) & 0xFF));
		buf.put((byte) ((value >> 12) & 0xFF));
		buf.put((byte) ((value >> 4) & 0xFF));
		buf.put((byte) (value & 0xFF));
	}

	private static ExposedByteArrayOutputStream ltf8TestBAOS = new ExposedByteArrayOutputStream();
	private static ByteArrayInputStream ltf8TestBAIS = new ByteArrayInputStream(ltf8TestBAOS.getBuffer());

	private static boolean testLTF8(long value) throws IOException {
		ltf8TestBAOS.reset();
		int len = writeUnsignedLTF8(value, ltf8TestBAOS);

		if (len > 8 * 9) {
			System.out.println("Written length is too big: " + len);
			return false;
		}

		ltf8TestBAIS.reset();
		long result = readUnsignedLTF8(ltf8TestBAIS);

		if (value != result) {
			System.out.printf("Value=%d, result=%d\n", value, result);
			return false;
		}
		return true;
	}

	public static void main(String[] args) throws IOException {

		sun.misc.CRC16 crc16 = new sun.misc.CRC16();
		String eof = "00 00 00 00 ff ff ff ff ff e0 45 4f 46 00 00 00 00 01 00";
		eof = "01";
		for (byte b : ByteBufferUtils.bytesFromHex(eof))
			crc16.update(b);
		crc16.reset();
		crc16.update((byte) 1);
		System.out.println("crc16: " + crc16.value);

		System.out.println("fnv1a_32: " + fnv1a_32("a".getBytes()));
		System.out.println("FNV_1a: " + my_fnv1a_32("a".getBytes()));
		System.out.println(0x811c9dc5L);
		System.out.println(0xe40c292cL);

		byte[] data = new byte[1024 * 1024];
		new Random().nextBytes(data);

		// BigInteger hash = new BigInteger("0");
		long hash = 0;
		Adler32 a = new Adler32();
		long aValue = 0;
		long time1;
		long time2;

		time1 = System.currentTimeMillis();
		for (int i = 0; i < 100; i++)
			hash += my_fnv1a_32(data);
		time2 = System.currentTimeMillis();

		System.out.println(aValue);
		System.out.printf("FNV_1A: %.2f s\n", (time2 - time1) / 1000f);

		CRC32 crc32 = new CRC32();
		time1 = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			crc32.reset();
			crc32.update(data);
			hash += crc32.getValue();
		}
		time2 = System.currentTimeMillis();

		System.out.println(aValue);
		System.out.printf("CRC32: %.2f s\n", (time2 - time1) / 1000f);

		time1 = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			a.reset();
			a.update(data);
			aValue += a.getValue();
		}

		time2 = System.currentTimeMillis();
		System.out.println(hash);
		System.out.printf("Adler32: %.2f s\n", (time2 - time1) / 1000f);

		if (true)
			return;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		writeUnsignedITF8(-1, baos);
		baos.close();
		System.out.println(Arrays.toString(baos.toByteArray()));

		int n = readUnsignedITF8(new byte[] { (byte) (0xFF & 224), 69, 79, 70 });
		System.out.println(n);

		{

			// test LTF8

			if (!testLTF8(1125899906842622l))
				return;
			if (!testLTF8(562949953421312l))
				return;
			if (!testLTF8(4294967296l))
				return;
			if (!testLTF8(-1l))
				return;
			if (!testLTF8(268435456L))
				return;
			if (!testLTF8(2147483648L))
				return;

			testLTF8(0);
			testLTF8(0);
			testLTF8(1);
			testLTF8(127);
			testLTF8(128);
			testLTF8(255);
			testLTF8(256);
			for (int i = 0; i <= 64; i++) {
				testLTF8((1L << i) - 2);
				testLTF8((1L << i) - 1);
				testLTF8(1L << i);
				testLTF8((1L << i) + 1);
				testLTF8((1L << i) + 1);
			}

			System.out.println("===========  LTF8 tests ok ================");
		}

		{
			// test ITF8
			ByteBuffer buf = ByteBuffer.allocate(5);

			// Read 192 but expecting 16384
			writeUnsignedITF8(16384, buf);
			buf.flip();
			int v = readUnsignedITF8(buf);
			System.out.println(v);

			long time = System.nanoTime();
			for (int i = Integer.MIN_VALUE; i < Integer.MIN_VALUE + 10; i++) {
				buf.clear();
				writeUnsignedITF8(i, buf);
				buf.flip();
				byte[] bytes = new byte[buf.limit()];
				buf.get(bytes);
				buf.position(0);
				System.out.printf("%d=%s\n", i, toHex(bytes));
				int value = readUnsignedITF8(buf);
				if (i != value)
					throw new RuntimeException("Read " + value + " but expecting " + i);

				if (System.nanoTime() - time > 1000 * 1000 * 1000) {
					time = System.nanoTime();
					System.out.println("i=" + i);
				}
			}

			System.out.println("Done.");

			String s = "e07d027300948bfffffed66bc0c34d2405826dd9c2d7";

			byte[] b = new byte[s.length() / 2];
			for (int i = 0; i < s.length(); i += 2) {
				b[i / 2] = (byte) Integer.valueOf(s.substring(i, i + 2), 16).intValue();
			}
			System.out.println(Arrays.toString(b));

			buf = ByteBuffer.wrap(b);
			int i1 = readUnsignedITF8(buf);
			System.out.println(buf.get());
			System.out.println(buf.get());
			System.out.println(buf.get());

			int i2 = readUnsignedITF8(buf);
			System.out.printf("i1=%d, i2=%d\n", i1, i2);

			b = writeUnsignedITF8(-4757);
			StringBuffer sb = new StringBuffer();
			for (byte t : b) {
				System.out.printf("byte %d, hex %s\n", t, Integer.toHexString(0xFF & t));
				sb.append(Integer.toHexString(0xFF & t));
			}
			System.out.println(sb.toString());
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuffer sb = new StringBuffer();
		for (byte t : bytes) {
			sb.append(Integer.toHexString(0xFF & t));
		}
		return sb.toString();
	}

	/**
	 * Unsigned little-endiann 4 byte integer
	 * 
	 * @param is
	 *            input stream to read from
	 * @return an integer value read
	 * @throws IOException
	 */
	public static int int32(InputStream is) throws IOException {
		return is.read() | is.read() << 8 | is.read() << 16 | is.read() << 24;
	}

	public static int int32(byte[] data) throws IOException {
		if (data.length != 4)
			throw new IllegalArgumentException("Expecting a 4-byte integer. ");
		return (0xFF & data[0]) | ((0xFF & data[1]) << 8) | ((0xFF & data[2]) << 16) | ((0xFF & data[3]) << 24);
	}

	public static int writeInt32(int value, OutputStream os) throws IOException {
		os.write((byte) value);
		os.write((byte) (value >> 8));
		os.write((byte) (value >> 16));
		os.write((byte) (value >> 24));
		return 4;
	}

	/**
	 * Unsigned little-endiann 4 byte integer
	 * 
	 * @param is
	 *            byte buffer to read from
	 * @return an integer value read
	 * @throws IOException
	 */
	public static int int32(ByteBuffer buf) throws IOException {
		return buf.get() | buf.get() << 8 | buf.get() << 16 | buf.get() << 24;
	}

	public static int[] array(InputStream is) throws IOException {
		int size = readUnsignedITF8(is);
		int[] array = new int[size];
		for (int i = 0; i < size; i++)
			array[i] = readUnsignedITF8(is);

		return array;
	}

	public static int write(int[] array, OutputStream os) throws IOException {
		int len = writeUnsignedITF8(array.length, os);
		for (int i = 0; i < array.length; i++)
			len += writeUnsignedITF8(array[i], os);

		return len;
	}

	public static int readFully(byte[] data, InputStream is) throws IOException {
		return readFully(data, data.length, 0, is);
	}

	public static int readFully(byte[] data, int length, int offset, InputStream inputStream) throws IOException {
		if (length < 0)
			throw new IndexOutOfBoundsException();
		int n = 0;
		while (n < length) {
			int count = inputStream.read(data, offset + n, length - n);
			if (count < 0)
				throw new EOFException();
			n += count;
		}
		return n;
	}

	public static long copyLarge(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[1024 * 4];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	public static byte[] readFully(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		long count = copyLarge(input, output);
		if (count > Integer.MAX_VALUE)
			throw new RuntimeException("Failed to copy data because the size is over 2g limit. ");
		return output.toByteArray();
	}

	public static byte[] gunzip(byte[] data) throws IOException {
		GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
		return readFully(gis);
	}

	public static int GZIP_COMPRESSION_LEVEL = Integer.valueOf(System.getProperty("gzip.compression.level", "5"));

	public static byte[] gzip(byte[] data) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gos = new GZIPOutputStream(baos) {
			{
				def.setLevel(GZIP_COMPRESSION_LEVEL);
			}
		};
		long count = copyLarge(new ByteArrayInputStream(data), gos);
		gos.close();

		return baos.toByteArray();
	}

	public static String substring(ByteBuffer buf, int len) {
		byte[] data = new byte[Math.min(len, buf.limit())];
		buf.get(data);
		return new String(data);
	}

	public static final long FNV_1a(byte[] data) {
		long hash = 2166136261L;
		long octet = 0;
		ByteBuffer b = ByteBuffer.wrap(data);
		for (int i = 0; i < data.length / 8; i++) {
			hash = hash | b.getLong();
			hash = hash * 1099511628211L;
		}

		for (int i = 0; i < data.length % 8; i++) {
			octet = octet | (data[data.length - (data.length % 8) + i] << (64 - (i * 8)));
		}
		hash = hash * 1099511628211L;

		return hash;
	}

	private static BigInteger HASH = new BigInteger("811c9dc5", 16);
	private static final BigInteger PRIME32 = new BigInteger("01000193", 16);
	private static final BigInteger MOD32 = new BigInteger("2").pow(32);

	public static final BigInteger fnv1a_32(byte[] data) {
		for (byte b : data) {
			HASH = HASH.xor(BigInteger.valueOf(b & 0xff));
			HASH = HASH.multiply(PRIME32).mod(MOD32);
		}

		return HASH;
	}

	private static long prime32 = PRIME32.longValue();
	private static long mod32 = MOD32.longValue();

	public static final long my_fnv1a(byte[] data) {
		long hash = 0x811c9dc5L;
		for (byte b : data) {
			hash = hash ^ (b & 0xff);
			hash = (hash * 0x01000193L) % 0x100000000L;
		}

		return hash;
	}

	public static final long my_fnv1a_32(byte[] data) {
		long hash = 0x811c9dc5L;
		for (byte b : data) {
			hash = hash ^ (b & 0xff);
			hash = (hash * 0x01000193);
		}

		return hash % 0x100000000L;
	}

	public static byte[] bytesFromHex(String s) {
		String clean = s.replaceAll("[^0-9a-f]", "");
		if (clean.length() % 2 != 0)
			throw new RuntimeException("Not a hex string: " + s);
		byte data[] = new byte[clean.length() / 2];
		for (int i = 0; i < clean.length(); i += 2) {
			data[i / 2] = (Integer.decode("0x" + clean.charAt(i) + clean.charAt(i + 1))).byteValue();
		}
		return data;
	}

	public static void reverse(byte[] array, int offset, int size) {
		if (array == null) {
			return;
		}
		int i = offset;
		int j = size - 1;
		byte tmp;
		while (j > i) {
			tmp = array[j];
			array[j] = array[i];
			array[i] = tmp;
			j--;
			i++;
		}
	}

	public static void reverse(ByteBuffer ptr) {
		byte tmp = 0;
		if (ptr.hasArray()) {
			reverse(ptr.array(), ptr.arrayOffset(), ptr.limit());
		} else {
			for (int i = 0; i < ptr.limit(); i++) {
				tmp = ptr.get(i);
				ptr.put(i, ptr.get(ptr.limit() - i - 1));
				ptr.put(ptr.limit() - i - 1, tmp);
			}
		}
	}
}
