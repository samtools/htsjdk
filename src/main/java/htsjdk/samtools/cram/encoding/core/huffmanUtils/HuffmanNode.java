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

import java.util.Map;

/**
 * A node in the {@code HuffmanTree}.
 * @param <T> type of the symbols in the alphabet being huffman-encoded
 */
class HuffmanNode<T> extends HuffmanTree<T> {
    public final HuffmanTree left, right;

    /**
     * A node in a {@code HuffmanTree}.
     * @param left left node
     * @param right right node
     */
    public HuffmanNode(final HuffmanTree<T> left, final HuffmanTree<T> right) {
        super(left.frequency + right.frequency);
        this.left = left;
        this.right = right;
    }

    /**
     * Populate a map with the HuffmanBitCode<T> for each symbol in the alphabet
     */
    @Override
    public void getCodeWords(int codeWord, int codeWordLength, final Map<T, HuffmanBitCode<T>> symbolsToCodes) {
        // traverse left
        codeWord <<= 1;
        codeWordLength++;
        left.getCodeWords(codeWord, codeWordLength, symbolsToCodes);

        // traverse right
        codeWord = codeWord | 1;
        right.getCodeWords(codeWord, codeWordLength, symbolsToCodes);
    }
}