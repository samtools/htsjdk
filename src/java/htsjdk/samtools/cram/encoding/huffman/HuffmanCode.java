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
package htsjdk.samtools.cram.encoding.huffman;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HuffmanCode {

    public static <T> HuffmanTree<T> buildTree(final int[] charFrequencies, final T[] values) {
        final LinkedList<HuffmanTree<T>> list = new LinkedList<HuffmanTree<T>>();
        for (int i = 0; i < charFrequencies.length; i++)
            if (charFrequencies[i] > 0)
                list.add(new HuffmanLeaf<T>(charFrequencies[i], values[i]));

        final Comparator<HuffmanTree<T>> comparator = new Comparator<HuffmanTree<T>>() {

            @Override
            public int compare(final HuffmanTree<T> o1, final HuffmanTree<T> o2) {
                return o1.frequency - o2.frequency;
            }
        };

        while (list.size() > 1) {
            Collections.sort(list, comparator);
            // dumpList(list) ;
            final HuffmanTree<T> left = list.remove();
            final HuffmanTree<T> right = list.remove();
            list.add(new HuffmanNode<T>(left, right));
        }
        return list.isEmpty() ? null : list.remove();
    }

    public static <T> void getValuesAndBitLengths(final List<T> values,
                                                  final List<Integer> lens, final HuffmanTree<T> tree) {
        final TreeMap<T, HuffmanBitCode<T>> codes = new TreeMap<T, HuffmanBitCode<T>>();
        getBitCode(tree, new HuffmanBitCode<T>(), codes);

        for (final T value : codes.keySet()) {
            final HuffmanBitCode<T> code = codes.get(value);
            values.add(value);
            lens.add(code.bitLength);
        }
    }

    private static class HuffmanBitCode<T> {
        long bitCode;
        int bitLength;
    }

    private static <T> void getBitCode(final HuffmanTree<T> tree,
                                       final HuffmanBitCode<T> code, final Map<T, HuffmanBitCode<T>> codes) {
        if (tree instanceof HuffmanLeaf) {
            final HuffmanLeaf<T> leaf = (HuffmanLeaf<T>) tree;
            final HuffmanBitCode<T> readyCode = new HuffmanBitCode<T>();
            readyCode.bitCode = code.bitCode;
            readyCode.bitLength = code.bitLength;
            codes.put(leaf.value, readyCode);

        } else if (tree instanceof HuffmanNode) {
            final HuffmanNode<T> node = (HuffmanNode<T>) tree;

            // traverse left
            code.bitCode = code.bitCode << 1;
            code.bitLength++;

            getBitCode(node.left, code, codes);
            code.bitCode = code.bitCode >>> 1;
            code.bitLength--;

            // traverse right
            code.bitCode = code.bitCode << 1 | 1;
            code.bitLength++;

            getBitCode(node.right, code, codes);
            code.bitCode = code.bitCode >>> 1;
            code.bitLength--;
        }
    }
}
