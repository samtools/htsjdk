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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;


public class CanonicalHuffmanByteCodec extends AbstractBitCodec<Byte> {

	private TreeMap<Byte, HuffmanBitCode> codes;
	private HuffmanBitCode[] bitCodes = new HuffmanBitCode[256];
	private Integer[] codeLentghSorted;
	private Map<Integer, Map<Long, Byte>> codeCache = new HashMap<Integer, Map<Long, Byte>>();
	private Map<Long, Byte>[] codeMaps;

	/*
	 * values[]: the alphabet (provided as Integers) bitLengths[]: the number of
	 * bits of symbil's huffman code
	 */
	public CanonicalHuffmanByteCodec(byte[] values, int[] bitLengths) {
		super();

		// 1. Sort by (a) bit length and (b) by symbol value -----------
		SortedMap codebook = new TreeMap<Integer, SortedSet<Integer>>();
		for (int i = 0; i < values.length; i++) {
			if (codebook.containsKey(bitLengths[i]))
				((TreeSet) codebook.get(bitLengths[i])).add(values[i]);
			else {
				TreeSet<Byte> entry = new TreeSet<Byte>();
				entry.add(values[i]);
				codebook.put(bitLengths[i], entry);
			}
		}
		codeLentghSorted = new Integer[codebook.size()];
		int keys = 0;

		// 2. Calculate and Assign Canonical Huffman Codes -------------
		int codeLength = 0, codeValue = -1; // first Canonical is always 0
		codes = new TreeMap<Byte, HuffmanBitCode>();
		Set keySet = codebook.keySet();
		for (Object key : keySet) { // Iterate over code lengths
			int iKey = Integer.parseInt(key.toString());
			codeLentghSorted[keys++] = iKey;

			TreeSet<Byte> get = (TreeSet<Byte>) codebook.get(key);
			for (Byte entry : get) { // Iterate over symbols
				HuffmanBitCode code = new HuffmanBitCode();
				code.bitLentgh = iKey; // given: bit length
				code.value = entry; // given: symbol

				codeValue++; // increment bit value by 1
				int delta = iKey - codeLength; // new length?
				codeValue = codeValue << delta; // pad with 0's
				code.bitCode = codeValue; // calculated: huffman code
				codeLength += delta; // adjust current code len

				if (NumberOfSetBits(codeValue) > iKey)
					throw new IllegalArgumentException("Symbol out of range");

				bitCodes[entry & 0xFF] = code; // Store Bit Code
				codes.put(entry, code); // Store HuffmanBitCode

				Map<Long, Byte> codeMap = codeCache.get(code.bitLentgh);
				if (codeMap == null) {
					codeMap = new HashMap<Long, Byte>();
					codeCache.put(code.bitLentgh, codeMap);
				}
				codeMap.put(new Long(code.bitCode), (byte) (0xFF & code.value));
			}

		}

		// 3. Done. Just have to populate codeMaps ---------------------
		if (codeLentghSorted.length > 0)
			codeMaps = new Map[codeLentghSorted[codeLentghSorted.length - 1] + 1];
		else
			codeMaps = new Map[1];
		for (int len : codeLentghSorted) { // Iterate over code lengths
			codeMaps[len] = codeCache.get(len);
		}
	}

	@Override
	public Byte read(BitInputStream bis) throws IOException {
		long buf = 0; // huffman code
		int bitsRead = 0;
		for (int len : codeLentghSorted) {
			buf = buf << (len - bitsRead);

			long readLongBits = bis.readLongBits(len - bitsRead);

			buf = buf | readLongBits;

			bitsRead = len;
			Map<Long, Byte> codeMap = codeMaps[len];
			Byte result = codeMap.get(buf);
			if (result != null) {
				return result;
			}
		}
		throw new RuntimeException("Bit code not found. Current state: "
				+ bitsRead + " bits read, buf=" + buf);
	}

	@Override
	public long write(BitOutputStream bos, Byte object) throws IOException {
		HuffmanBitCode bitCode = bitCodes[object];
		if (bitCode == null)
			throw new RuntimeException("Huffman code not found for value: "
					+ object);
		bos.write(bitCode.bitCode, bitCode.bitLentgh);
		return bitCode.bitLentgh;
	}

	@Override
	public long numberOfBits(Byte object) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	private static class HuffmanBitCode {
		int bitCode;
		int bitLentgh;
		int value;
	}

	private int NumberOfSetBits(int i) {
		i = i - ((i >> 1) & 0x55555555);
		i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
		return (((i + (i >> 4)) & 0x0F0F0F0F) * 0x01010101) >> 24;
	}

	@Override
	public Byte read(BitInputStream bis, int len) throws IOException {
		throw new RuntimeException("Not implemented");
	}
}
