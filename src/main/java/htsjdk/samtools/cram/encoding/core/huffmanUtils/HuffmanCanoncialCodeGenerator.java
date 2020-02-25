/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.encoding.core.huffmanUtils;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.utils.ValidationUtils;

import java.util.*;

/**
 * Given a set of {@link HuffmanParams}, creates the set of canonical codes that are be used to
 * read/write symbols from/to an output/input stream.
 * @param <T> type of the symbols in the alphabet being huffman-encoded
 */
public final class HuffmanCanoncialCodeGenerator<T> {
    private final HuffmanParams<T> huffmanParams;
    private final List<HuffmanBitCode<T>> huffmanBitCodesByBitLengthThenCode;
    private final Map<T, HuffmanBitCode<T>> huffmanBitCodesBySymbol;

    private final List<T> symbolsSortedByBitCode;
    private final int[] bitLensSortedByBitCode;
    private final int[] bitCodeToSymbol;

    /**
     * @param huffmanParams {@link HuffmanParams} to use for this helper
     */
    public HuffmanCanoncialCodeGenerator(final HuffmanParams<T> huffmanParams) {
        ValidationUtils.nonNull(huffmanParams, "requires huffman params");
        this.huffmanParams = huffmanParams;
        huffmanBitCodesByBitLengthThenCode = getCanonicalCodeWords();

        final int nSymbols = huffmanBitCodesByBitLengthThenCode.size();
        huffmanBitCodesBySymbol = new HashMap<>(nSymbols);
        huffmanBitCodesByBitLengthThenCode.forEach((bitcode) -> huffmanBitCodesBySymbol.put(bitcode.getSymbol(), bitcode));

        final int[] sortedBitCodes = new int[nSymbols];
        symbolsSortedByBitCode = new ArrayList<>(nSymbols);
        bitLensSortedByBitCode = new int[nSymbols];

        int maxBitCode = 0;
        for (int i = 0; i < nSymbols; i++) {
            final HuffmanBitCode<T> huffmanCode = huffmanBitCodesByBitLengthThenCode.get(i);
            sortedBitCodes[i] = huffmanCode.getCodeWord();
            symbolsSortedByBitCode.add(huffmanCode.getSymbol());
            bitLensSortedByBitCode[i] = huffmanCode.getCodeWordBitLength();
            maxBitCode = Integer.max(maxBitCode, huffmanCode.getCodeWord());
        }

        bitCodeToSymbol = new int[maxBitCode + 1];
        Arrays.fill(bitCodeToSymbol, -1);
        for (int i = 0; i < nSymbols; i++) {
            bitCodeToSymbol[huffmanBitCodesByBitLengthThenCode.get(i).getCodeWord()] = i;
        }
    }

    /**
     * Return the canonical code words for this helper's {@link HuffmanParams} as a list of HuffmanBitCodes.
     * @return list of HuffmanBitCode for this helper's {@link HuffmanParams}
     */
    //VisibleForTesting
    public List<HuffmanBitCode<T>> getCanonicalCodeWords() {
        // group the symbols according to code huffman code word length
        final TreeMap<Integer, SortedSet<T>> symbolsByCodeLength = new TreeMap<>();
        for (int i = 0; i < huffmanParams.getCodeWordLengths().size(); i++) {
            symbolsByCodeLength.computeIfAbsent(
                    huffmanParams.getCodeWordLengths().get(i),
                    k -> new TreeSet<>()).add(huffmanParams.getSymbols().get(i));
        }

        // now remap the symbols to canonical codes
        final List<HuffmanBitCode<T>> canonicalCodes = new ArrayList<>(huffmanParams.getCodeWordLengths().size());
        int codeLength = 0;
        int codeValue = -1;

        // 1. Sort the alphabet ascending using bit-lengths and then using numerical order of the values.
        // 2. The first symbol in the list gets assigned a codeword which is the same length as the symbol’s
        //    original codeword but all zeros. This will often be a single zero (’0’).
        // 3. Each subsequent symbol is assigned the next binary number in sequence, ensuring that following codes
        //    are always higher in value.
        // 4. When you reach a longer codeword, then increment and append zeros until the length of the new
        //    codeword is equal to the length of the old codeword.

        for (final Map.Entry<Integer, SortedSet<T>> symbolsForLength : symbolsByCodeLength.entrySet()) {
            for (final T symbol : symbolsForLength.getValue()) { // Iterate over symbols
                final int bitLength = symbolsForLength.getKey();

                codeValue++;                                // increment bit symbol by 1
                final int delta = bitLength - codeLength;   // new length?
                if (delta != 0) {
                    codeValue = codeValue << delta;             // pad with 0's if new length
                    codeLength += delta;                        // adjust current code length
                }
                if (Integer.bitCount(codeValue) > bitLength) {
                    // canonical code words should be of the same length as the originals
                    throw new IllegalArgumentException(
                            String.format("Bit length (%d) for symbol (%d) out of range",
                                    Integer.bitCount(codeValue),
                                    symbol));
                }

                canonicalCodes.add(new HuffmanBitCode(symbol, codeValue, bitLength));
            }
        }
        // sort by bitcode length, then bitcode
        canonicalCodes.sort(bitCodeComparator);
        return canonicalCodes;
    }

    /**
     *
     * @param bitOutputStream stream to which the symbol should be written
     * @param symbol symbol from the alphabet to be written to the stream
     * @return the length of the codeword that was written
     */
    public final long write(final BitOutputStream bitOutputStream, final T symbol) {
        final HuffmanBitCode<T> code = huffmanBitCodesBySymbol.get(symbol);
        if (code == null) {
            throw new RuntimeException(String.format(
                    "Attempt to write a symbol (%d) that is not in the symbol alphabet for this huffman encoder (found code word %s).",
                    symbol,
                    code == null ?
                            "null" :
                            code.toString()));
        }
        bitOutputStream.write(code.getCodeWord(), code.getCodeWordBitLength());
        return code.getCodeWordBitLength();
    }

    /**
     * Read a single huffman-encoded symbol from a stream
     * @param bitInputStream stream from which a symbol should read
     * @return symbol read from the stream
     */
    public final T read(final BitInputStream bitInputStream) {
        // iterate through huffman codes in order of increasing bit length until we find a match
        for (int i = 0, previousCodeWordLength = 0, codeWord = 0; i < huffmanBitCodesByBitLengthThenCode.size(); i++) {
            final int newCodeWordLength = huffmanBitCodesByBitLengthThenCode.get(i).getCodeWordBitLength();
            codeWord <<= newCodeWordLength - previousCodeWordLength;
            codeWord |= bitInputStream.readBits(newCodeWordLength - previousCodeWordLength);
            previousCodeWordLength = newCodeWordLength;

            final int symbolIndex = bitCodeToSymbol[codeWord];
            if (symbolIndex > -1 && bitLensSortedByBitCode[symbolIndex] == newCodeWordLength) {
                return symbolsSortedByBitCode.get(symbolIndex);
            }

            // advance to the end of the codewords of this length
            for (int j = i; huffmanBitCodesByBitLengthThenCode.get(j + 1).getCodeWordBitLength() == newCodeWordLength
                    && j < huffmanBitCodesByBitLengthThenCode.size(); j++) {
                i++;
            }
        }

        throw new RuntimeException("Unable to map huffman code from input stream to a valid symbol");
    }

    //VisibleForTesting
    public int getCodeWordLenForValue(final T value) {
        return huffmanBitCodesBySymbol.get(value).getCodeWordBitLength();
    }

    private final Comparator<HuffmanBitCode<T>> bitCodeComparator =
            (final HuffmanBitCode<T> o1, final HuffmanBitCode<T> o2) -> {
                final int result = o1.getCodeWordBitLength() - o2.getCodeWordBitLength();
                    if (result == 0)
                        return o1.getCodeWord() - o2.getCodeWord();
                    else
                        return result;
                };

}
