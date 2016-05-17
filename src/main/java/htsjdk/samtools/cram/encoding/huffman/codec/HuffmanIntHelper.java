/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.encoding.huffman.codec;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

class HuffmanIntHelper {
    TreeMap<Integer, HuffmanBitCode> codes;

    private final int[] values;
    private final int[] bitLengths;
    private TreeMap<Integer, SortedSet<Integer>> codeBook;

    private final HuffmanBitCode[] sortedCodes;
    private final HuffmanBitCode[] sortedByValue;
    private final int[] sortedValues;
    private final int[] sortedValuesByBitCode;
    private final int[] sortedBitLensByBitCode;
    private final int[] bitCodeToValue;

    public HuffmanIntHelper(final int[] values, final int[] bitLengths) {
        this.values = values;
        this.bitLengths = bitLengths;

        buildCodeBook();
        buildCodes();

        final ArrayList<HuffmanBitCode> list = new ArrayList<HuffmanBitCode>(
                codes.size());
        list.addAll(codes.values());
        Collections.sort(list, bitCodeComparator);
        sortedCodes = list.toArray(new HuffmanBitCode[list
                .size()]);

        sortedValues = Arrays.copyOf(values, values.length);
        Arrays.sort(sortedValues);
        {
            int i = 0;
            sortedByValue = new HuffmanBitCode[sortedValues.length];
            for (final int value : sortedValues)
                sortedByValue[i++] = codes.get(value);
        }

        final int[] sortedBitCodes = new int[sortedCodes.length];
        sortedValuesByBitCode = new int[sortedCodes.length];
        sortedBitLensByBitCode = new int[sortedCodes.length];
        int maxBitCode = 0;
        for (int i = 0; i < sortedBitCodes.length; i++) {
            sortedBitCodes[i] = sortedCodes[i].bitCode;
            sortedValuesByBitCode[i] = sortedCodes[i].value;
            sortedBitLensByBitCode[i] = sortedCodes[i].bitLength;
            if (maxBitCode < sortedCodes[i].bitCode)
                maxBitCode = sortedCodes[i].bitCode;
        }

        bitCodeToValue = new int[maxBitCode + 1];
        Arrays.fill(bitCodeToValue, -1);
        for (int i = 0; i < sortedBitCodes.length; i++) {
            bitCodeToValue[sortedCodes[i].bitCode] = i;
        }
    }

    private void buildCodeBook() {
        codeBook = new TreeMap<Integer, SortedSet<Integer>>();
        for (int i = 0; i < values.length; i++) {
            if (codeBook.containsKey(bitLengths[i]))
                codeBook.get(bitLengths[i]).add(values[i]);
            else {
                final TreeSet<Integer> entry = new TreeSet<Integer>();
                entry.add(values[i]);
                codeBook.put(bitLengths[i], entry);
            }
        }
    }

    private void buildCodes() {
        codes = new TreeMap<Integer, HuffmanBitCode>();
        int codeLength = 0, codeValue = -1;
        for (final Object key : codeBook.keySet()) { // Iterate over code lengths

            @SuppressWarnings("SuspiciousMethodCalls") final SortedSet<Integer> get = codeBook.get(key);
            final int intKey = Integer.parseInt(key.toString());
            for (final Integer entry : get) { // Iterate over symbols
                final HuffmanBitCode code = new HuffmanBitCode();
                code.bitLength = intKey; // given: bit length
                code.value = entry; // given: symbol

                codeValue++; // increment bit value by 1
                final int delta = intKey - codeLength; // new length?
                codeValue = codeValue << delta; // pad with 0's
                code.bitCode = codeValue; // calculated: huffman code
                codeLength += delta; // adjust current code length

                if (NumberOfSetBits(codeValue) > intKey)
                    throw new IllegalArgumentException("Symbol out of range");

                codes.put(entry, code); // Store HuffmanBitCode

            }

        }
    }

    public final long write(final BitOutputStream bitOutputStream, final int value) throws IOException {
        final int index = Arrays.binarySearch(sortedValues, value);
        final HuffmanBitCode code = sortedByValue[index];
        if (code.value != value)
            throw new RuntimeException(String.format(
                    "Searching for %d but found %s.", value, code.toString()));
        bitOutputStream.write(code.bitCode, code.bitLength);
        return code.bitLength;
    }

    public final int read(final BitInputStream bitInputStream) throws IOException {
        int prevLen = 0;
        int bits = 0;
        for (int i = 0; i < sortedCodes.length; i++) {
            final int length = sortedCodes[i].bitLength;
            bits <<= length - prevLen;
            bits |= bitInputStream.readBits(length - prevLen);
            prevLen = length;
            { // Variant 2:
                final int index = bitCodeToValue[bits];
                if (index > -1 && sortedBitLensByBitCode[index] == length)
                    return sortedValuesByBitCode[index];

                for (int j = i; sortedCodes[j + 1].bitLength == length
                        && j < sortedCodes.length; j++)
                    i++;
            }
        }

        throw new RuntimeException("Not found.");
    }

    private static final Comparator<HuffmanBitCode> bitCodeComparator = new Comparator<HuffmanBitCode>() {

        @Override
        public int compare(final HuffmanBitCode o1, final HuffmanBitCode o2) {
            final int result = o1.bitLength - o2.bitLength;
            if (result == 0)
                return o1.bitCode - o2.bitCode;
            else
                return result;
        }
    };

    private static int NumberOfSetBits(int i) {
        i = i - ((i >> 1) & 0x55555555);
        i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
        return (((i + (i >> 4)) & 0x0F0F0F0F) * 0x01010101) >> 24;
    }
}
