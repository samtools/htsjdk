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
package htsjdk.samtools.cram.encoding.core.huffmanUtils;

import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.utils.ValidationUtils;

import java.util.*;

/**
 * HuffmanTree class for creating huffman codes from a set of frequencies for symbols in an alphabet.
 * @param <T> type of the symbols in the alphabet being huffman-encoded
 */
abstract class HuffmanTree<T> implements Comparable<HuffmanTree<T>> {
    public final int frequency;

    protected HuffmanTree(final int freq) {
        frequency = freq;
    }

    /**
     * Return the {@link HuffmanTree} for the given alphabet symbol frequencies
     * @param symbolFrequencies
     * @param <T> type param of symbols int he alphabet
     * @return the {@link HuffmanTree} for the given alphabet symbol frequencies
     */
    public static <T> HuffmanTree<T> buildTree(final HashMap<T, MutableInt> symbolFrequencies) {
        ValidationUtils.nonNull(symbolFrequencies, "non-null symbol frequencies required");
        ValidationUtils.nonNull(symbolFrequencies.size() > 0, "non-zero symbol frequencies required");

        final LinkedList<HuffmanTree> list = new LinkedList<>();
        symbolFrequencies.forEach((s, f) -> list.add(new HuffmanLeaf(s, f.value)));

        while (list.size() > 1) {
            Collections.sort(list);
            final HuffmanTree left = list.remove();
            final HuffmanTree right = list.remove();
            list.add(new HuffmanNode(left, right));
        }
        return list.isEmpty() ? null : list.remove();
    }

    /**
     * Get the (non-canonical) huffman params (alphabet symbols and codeword lengths) for the the symbols in this
     * tree's alphabet.
     */
    public HuffmanParams<T> getHuffmanParams() {
        final TreeMap<T, HuffmanBitCode<T>> codeWords = new TreeMap<>();
        getCodeWords(0,0, codeWords);

        final List<T> symbols = new ArrayList();
        final List<Integer> codeWordLengths = new ArrayList();
        for (final T symbol : codeWords.keySet()) {
            final HuffmanBitCode code = codeWords.get(symbol);
            symbols.add(symbol);
            codeWordLengths.add(code.getCodeWordBitLength());
        }

        return new HuffmanParams<T>(symbols, codeWordLengths);
    }

    /**
     * Traverse the huffman tree and get a map of symbols to {@link HuffmanBitCode}
     * @param codeWord starting codeword
     * @param codeWordLength starting codeword bit length
     * @param symbolsToCodes map to populate
     */
    public abstract void getCodeWords(int codeWord, int codeWordLength, final Map<T, HuffmanBitCode<T>> symbolsToCodes);

    @Override
    public int compareTo(final HuffmanTree tree) {
        return frequency - tree.frequency;
    }

}
