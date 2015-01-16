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


public class CanonicalHuffmanIntegerCodec extends AbstractBitCodec<Integer> {

	private TreeMap<Integer, HuffmanBitCode> codes;
	private Integer[] codeLentghSorted;
	private Map<Integer, Map<Long, Integer>> codeCache = new HashMap<Integer, Map<Long, Integer>>();
	private Map<Long, Integer>[] codeMaps;

	/*
	 * values[]: the alphabet (provided as Integers) bitLengths[]: the number of
	 * bits of symbil's huffman code
	 */
	public CanonicalHuffmanIntegerCodec(int[] values, int[] bitLengths) {
		super();

		// 1. Sort by (a) bit length and (b) by symbol value -----------
		SortedMap codebook = new TreeMap<Integer, SortedSet<Integer>>();
		for (int i = 0; i < values.length; i++) {
			if (codebook.containsKey(bitLengths[i]))
				((TreeSet) codebook.get(bitLengths[i])).add(values[i]);
			else {
				TreeSet<Integer> entry = new TreeSet<Integer>();
				entry.add(values[i]);
				codebook.put(bitLengths[i], entry);
			}
		}
		codeLentghSorted = new Integer[codebook.size()];
		int keys = 0;

		// 2. Calculate and Assign Canonical Huffman Codes -------------
		int codeLength = 0, codeValue = -1; // first Canonical is always 0
		codes = new TreeMap<Integer, HuffmanBitCode>();
		Set keySet = codebook.keySet();
		for (Object key : keySet) { // Iterate over code lengths
			int iKey = Integer.parseInt(key.toString());
			codeLentghSorted[keys++] = iKey;

			TreeSet<Integer> get = (TreeSet<Integer>) codebook.get(key);
			for (Integer entry : get) { // Iterate over symbols
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

				codes.put(entry, code); // Store HuffmanBitCode

				Map<Long, Integer> codeMap = codeCache.get(code.bitLentgh);
				if (codeMap == null) {
					codeMap = new HashMap<Long, Integer>();
					codeCache.put(code.bitLentgh, codeMap);
				}
				codeMap.put(new Long(code.bitCode), code.value);
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
	public Integer read(BitInputStream bis) throws IOException {
		long buf = 0; // huffman code
		int bitsRead = 0;
		for (int len : codeLentghSorted) {
			buf = buf << (len - bitsRead);

			long readLongBits = bis.readLongBits(len - bitsRead);

			buf = buf | readLongBits;

			bitsRead = len;
			Map<Long, Integer> codeMap = codeMaps[len];
			Integer result = codeMap.get(buf);
			if (result != null) {
				return result;
			}
		}
		throw new RuntimeException("Bit code not found. Current state: "
				+ bitsRead + " bits read, buf=" + buf);
	}

	@Override
	public long write(BitOutputStream bos, Integer object) throws IOException {
		HuffmanBitCode bitCode = codes.get(object);
		if (bitCode == null)
			throw new RuntimeException("Huffman code not found for value: "
					+ object);
		bos.write(bitCode.bitCode, bitCode.bitLentgh);
		return bitCode.bitLentgh;
	}

	@Override
	public long numberOfBits(Integer object) {
		HuffmanBitCode bitCode;
		try {
			bitCode = codes.get(object);
			return bitCode.bitLentgh ;
		} catch (NullPointerException e) {
			throw new RuntimeException("Value " + object + " not found.", e) ;
		}
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
	public Integer read(BitInputStream bis, int len) throws IOException {
		throw new RuntimeException("Not implemented");
	}
}
