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
package htsjdk.samtools.cram.encoding.huffint;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

class HelperByte {
	TreeMap<Integer, HuffmanBitCode> codes;

	int[] values;
	int[] bitLengths;
	private TreeMap<Integer, SortedSet<Integer>> codebook;

	final HuffmanBitCode[] sortedCodes;
	final byte[] sortedValues;
	final int[] sortedValuesByBitCode;
	final int[] sortedBitLensByBitCode;
	final int[] bitCodeToValue;
	final HuffmanBitCode[] valueToCode;

	HelperByte(byte[] values, int[] bitLengths) {
		this.values = new int[values.length];
		for (int i = 0; i < values.length; i++)
			this.values[i] = 0xFF & values[i];

		this.bitLengths = bitLengths;

		buildCodeBook();
		buildCodes();

		ArrayList<HuffmanBitCode> list = new ArrayList<HuffmanBitCode>(
				codes.size());
		list.addAll(codes.values());
		Collections.sort(list, bitCodeComparator);
		sortedCodes = (HuffmanBitCode[]) list.toArray(new HuffmanBitCode[list
				.size()]);

		// System.out.println("Sorted codes:");
		// for (HuffmanBitCode code : sortedCodes)
		// System.out.println(code);

		sortedValues = Arrays.copyOf(values, values.length);
		Arrays.sort(sortedValues);
		// System.out.println("Sorted values:");
		// for (int value : sortedValues)
		// System.out.println(value);

		sortedValuesByBitCode = new int[sortedCodes.length];
		sortedBitLensByBitCode = new int[sortedCodes.length];
		int maxBitCode = 0;
		for (int i = 0; i < sortedCodes.length; i++) {
			sortedValuesByBitCode[i] = sortedCodes[i].value;
			sortedBitLensByBitCode[i] = sortedCodes[i].bitLentgh;
			if (maxBitCode < sortedCodes[i].bitCode)
				maxBitCode = sortedCodes[i].bitCode;
		}

		bitCodeToValue = new int[maxBitCode + 1];
		Arrays.fill(bitCodeToValue, -1);
		for (int i = 0; i < sortedCodes.length; i++) {
			bitCodeToValue[sortedCodes[i].bitCode] = i;
		}

		valueToCode = new HuffmanBitCode[255];
		Arrays.fill(valueToCode, null);
		for (HuffmanBitCode code : sortedCodes) {
			valueToCode[code.value] = code;
		}
	}

	private void buildCodeBook() {
		codebook = new TreeMap<Integer, SortedSet<Integer>>();
		for (int i = 0; i < values.length; i++) {
			if (codebook.containsKey(bitLengths[i]))
				((TreeSet) codebook.get(bitLengths[i])).add(values[i]);
			else {
				TreeSet<Integer> entry = new TreeSet<Integer>();
				entry.add(values[i]);
				codebook.put(bitLengths[i], entry);
			}
		}
	}

	private void buildCodes() {
		codes = new TreeMap<Integer, HuffmanBitCode>();
		Set keySet = codebook.keySet();
		int codeLength = 0, codeValue = -1;
		for (Object key : keySet) { // Iterate over code lengths
			int iKey = Integer.parseInt(key.toString());

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

			}

		}
	}

	final long write(final BitOutputStream bos, final byte value)
			throws IOException {
		HuffmanBitCode code = valueToCode[value];
		if (code.value != value)
			throw new RuntimeException(String.format(
					"Searching for %d but found %s.", value, code.toString()));
		bos.write(code.bitCode, code.bitLentgh);
		// System.out.println("Writing: " + code.toString());
		return code.bitLentgh;
	}

	final byte read(final BitInputStream bis) throws IOException {
		int prevLen = 0;
		int bits = 0;
		for (int i = 0; i < sortedCodes.length; i++) {
			int len = sortedCodes[i].bitLentgh;
			bits <<= len - prevLen;
			bits |= bis.readBits(len - prevLen);
			prevLen = len;

			int index = bitCodeToValue[bits];
			if (index > -1 && sortedBitLensByBitCode[index] == len)
				return (byte) (0xFF & sortedValuesByBitCode[index]);

			for (int j = i; sortedCodes[j + 1].bitLentgh == len
					&& j < sortedCodes.length; j++)
				i++;
		}

		throw new RuntimeException("Not found.");
	}

	static Comparator<HuffmanBitCode> bitCodeComparator = new Comparator<HuffmanBitCode>() {

		@Override
		public int compare(HuffmanBitCode o1, HuffmanBitCode o2) {
			int result = o1.bitLentgh - o2.bitLentgh;
			if (result == 0)
				return o1.bitCode - o2.bitCode;
			else
				return result;
		}
	};
	static Comparator<HuffmanBitCode> valueComparator = new Comparator<HuffmanBitCode>() {

		@Override
		public int compare(HuffmanBitCode o1, HuffmanBitCode o2) {
			return o1.value - o2.value;
		}
	};

	private static int NumberOfSetBits(int i) {
		i = i - ((i >> 1) & 0x55555555);
		i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
		return (((i + (i >> 4)) & 0x0F0F0F0F) * 0x01010101) >> 24;
	}

}
