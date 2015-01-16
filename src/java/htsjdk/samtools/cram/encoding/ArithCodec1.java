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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * 
 * @author Alexander Senf
 */
public class ArithCodec1 extends AbstractBitCodec<byte[]> {
	private byte curBit = 0;
	private int curByte = 0;
	private double min = 0;
	private double max = 1;
	private double localMin = 0;
	private double localMax = 1;

	private long bitCount;

	private ByteArrayOutputStream baos;
	private ArrayList<Integer> fileData;

	private int previous;
	private double[][] freq;

	public ArithCodec1(int[] freqs, byte[][] map) {
		// Data received as collapsed array of two characters, with assoc
		// frequency distribution. Extract that into 2-dim array
		long[] count = new long[257];
		freq = new double[257][256]; // x = prev, y = cur
		for (int i = 0; i < 256; i++) {
			freq[256][i] = 1; // Initialize counts for first-char of a sequence
			count[256]++; // and count characters
		}
		for (int i = 0; i < freqs.length; i++) {
			int x = map[i].length > 1 ? map[i][0] : 256; // coordinate of prev
															// char
			int y = map[i].length > 1 ? map[i][1] : map[i][0]; // coordinate of
																// cur char
			freq[x][y] = freqs[i]; // place symbol count in sparts array
			count[x] += freqs[i]; // count total symbols by col (x fixed, all y)
		}

		// turn into frequency/probability distribution (normalize column-wise -
		// for each x)
		for (int x = 0; x < freq.length; x++) {
			double accum = 0;
			for (int y = 0; y < freq[x].length; y++) {
				if (count[x] > 0) {
					accum += (freq[x][y] / (double) count[x]);
					freq[x][y] = accum;
				}
			}
			if (freq[x][freq[x].length - 1] != 1.0)
				freq[x][freq[x].length - 1] = 1.0;
		}

		this.baos = new ByteArrayOutputStream();
		this.fileData = new ArrayList();
		this.previous = 256; // case where no character has been seen yet
	}

	/*
	 * Reading and expanding a bit stream based on given frequency count
	 */
	private Byte readByte(BitInputStream bis) throws IOException {
		this.baos.reset();
		this.fileData.clear();
		curBit = 0;
		curByte = 0;
		min = 0;
		max = 1;
		localMin = 0;
		localMax = 1;
		previous = 256;

		int read = decodeCharacter(bis);
		this.baos.write(read);
		previous = read;

		return (byte) read;
	}

	public byte[] read(BitInputStream bis, int length) throws IOException {

		this.baos.reset();
		this.fileData.clear();
		curBit = 0;
		curByte = 0;
		min = 0;
		max = 1;
		localMin = 0;
		localMax = 1;
		previous = 256;

		for (int i = 0; i < length; i++) {
			int read = decodeCharacter(bis);
			this.baos.write(read);
			previous = read;
		}

		System.out.println(fileData.size());
		System.out.println(curByte);
		System.out.println(curBit);
		int nofBits = 8 - curBit;
//		int nofBits = (fileData.size() - curByte) * 8 + 8 - curBit;
		System.out.println(nofBits);
		int bits = fileData.get(fileData.size() - 1) ;
//		int bits = (fileData.get(fileData.size() - 2) << 8) | fileData.get(fileData.size() - 1) ;
		
		bis.putBack(curBit, (bits >> curBit) & (((1 << nofBits) - 1)));

		return this.baos.toByteArray();
	}

	public int decodeCharacter(BitInputStream bis) throws IOException {
		double tempMin = min;
		double tempMax = max;
		byte tempBit = curBit;
		int tempByte = curByte;
		int val = 256;
		if (this.fileData.isEmpty())
			fileData.add(bis.readBits(8));
		double[] probs = freq[previous]; // get correct frequency distribution
		while (true) {
			double cur = (min + max) / 2.0;
			val = -1;
			for (int i = 0; i < probs.length; i++) {
				if (probs[i] > min) {
					if (probs[i] > max)
						val = i;
					break;
				}
			}
			if (val == -1) {
				boolean bit = false;
				if ((fileData.get(curByte) & (128 >> curBit)) != 0)
					bit = true;
				if (bit)
					min = cur;
				else
					max = cur;
				curBit++;
				if (curBit == 8) {
					curBit = 0;
					curByte++;
					if (curByte > fileData.size() - 1) {
						try {
							fileData.add(bis.readBits(8));
						} catch (Throwable t) {
							fileData.add(0);
						}
					}
				}
			} else
				break;
		}
		min = tempMin;
		max = tempMax;
		curBit = tempBit;
		curByte = tempByte;
		while (true) {
			double cur = (min + max) / 2.0;
			int temp = 0;
			for (; temp < probs.length; temp++)
				if (probs[temp] > cur)
					break;
			if (cur < 0 || cur > 1)
				temp = -1;
			if (temp != val) {
				boolean bit = false;
				if ((fileData.get(curByte) & (128 >> curBit)) != 0)
					bit = true;
				if (bit)
					min = cur;
				else
					max = cur;
				curBit++;
				if (curBit == 8) {
					curBit = 0;
					curByte++;
					if (curByte > fileData.size() - 1)
						try {
							fileData.add(bis.readBits(8));
						} catch (Throwable t) {
							fileData.add(0);
						}
				}
			} else {
				tempMin = 0;
				if (val > 0)
					tempMin = probs[val - 1];
				double factor = 1.0 / (probs[val] - tempMin);
				min = factor * (min - tempMin);
				max = factor * (max - tempMin);
				break;
			}
		}
		return val;
	}

	/*
	 * Write compressed output to a bit stream
	 */
	public long write(BitOutputStream bos, byte[] object) throws IOException {

		this.baos.reset();
		curBit = 0;
		curByte = 0;
		min = 0;
		max = 1;
		localMin = 0;
		localMax = 1;
		this.bitCount = 0;
		previous = 256;

		try {
			for (int i = 0; i < object.length; i++) {
				encodeCharacter(bos, object[i] & 0xFF);
				previous = object[i] & 0xFF;
			}
			flush(bos);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		return this.bitCount;
	}

	private void encodeCharacter(BitOutputStream bos, int character) throws Exception {
		double[] prbs = freq[previous];

		if (prbs.length < 2 || prbs[prbs.length - 1] != 1 || character < 0 || character >= prbs.length)
			throw new Exception("Invalid input");
		if (character > 0)
			localMin = prbs[character - 1];
		else
			localMin = 0;
		localMax = prbs[character];
		while (true) {
			double cur = (min + max) / 2.0;
			if (cur < localMin) {
				curByte |= (128 >> curBit); // set bit = 1, left-to-right
				curBit++;
				if (curBit == 8) {
					bos.write(curByte, 8);
					curByte = 0; // byte containing bits to be written
					curBit = 0; // bit-position, left-to-right
					this.bitCount += 8;
				}
				min = cur; // wrote 1 (go higer) adjust min
			} else if (cur >= localMax) {
				curBit++;
				if (curBit == 8) {
					bos.write(curByte, 8);
					curByte = 0;
					curBit = 0;
					this.bitCount += 8;
				}
				max = cur; // wrote 0 (go lower) adjust max
			} else {
				double factor = 1.0 / (localMax - localMin);
				min = factor * (min - localMin);
				max = factor * (max - localMin);
				break;
			}
		}
	}

	private void flush(BitOutputStream bos) throws IOException {
		if (curBit != 0) {
			while (true) {
				while (true) {
					double cur = (min + max) / 2.0;
					double mid = (localMin + localMax) / 2.0;
					if (cur < mid) {
						curByte |= (128 >> curBit);
						min = cur;
					} else
						max = cur;
					curBit++;
					if (curBit == 8) {
						bos.write(curByte, 8);
						curByte = 0;
						curBit = 0;
						this.bitCount += 8;
						break;
					}
				}
				double cur = (min + max) / 2.0;
				if (cur >= localMin && cur < localMax)
					break;
			}
		}
		bos.close();
	}

	/*
	 * Compress and count bits in the end
	 */
	@Override
	public long numberOfBits(byte[] object) {
		NullOutputStream baos = new NullOutputStream();
		DefaultBitOutputStream nBos = new DefaultBitOutputStream(baos);

		this.baos.reset();
		curBit = 0;
		curByte = 0;
		min = 0;
		max = 1;
		localMin = 0;
		localMax = 1;
		this.bitCount = 0;
		previous = 256;

		try {
			for (int i = 0; i < object.length; i++) {
				encodeCharacter(nBos, object[i] & 0xFF);
				previous = object[i];
			}
			flush(nBos);
		} catch (Exception ex) {
			;
		}

		return this.bitCount;
	}

	/** Writes to nowhere */
	private class NullOutputStream extends OutputStream {
		@Override
		public void write(int b) throws IOException {
			; //
		}
	}

	@Override
	public byte[] read(BitInputStream bis) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
