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

/**
 * Huffman bit code word consisting of a symbol, the corresponding codeword and codeword bit length.
 * @param <T> type of the symbols in the alphabet being huffman-encoded
 */
public final class HuffmanBitCode<T> {
    private final T symbol;
    private final int codeWord;
    private final int codeWordBitLength;

    public HuffmanBitCode(final T symbol, final int codeWord, final int codeWordBitLength) {
        this.symbol = symbol;
        this.codeWord = codeWord;
        this.codeWordBitLength = codeWordBitLength;
        if (codeWordBitLength > 31) {
            throw new IllegalArgumentException(
                    String.format("Huffman codeword of length %d exceeds the maximum length of 31", codeWordBitLength));
        }
    }

    /**
     * @return the symbol for this bit code
     */
    public T getSymbol() {
        return symbol;
    }

    /**
     * @return the codeword for this bit code
     */
    public int getCodeWord() {
        return codeWord;
    }

    /**
     * @return the codeword bit length for this bit code
     */
    public int getCodeWordBitLength() {
        return codeWordBitLength;
    }

    @Override
    public String toString() {
        return String.format("Symbol: %d CodeWord: %d (%s) BitLength: %d",
                symbol,
                codeWord,
                getBitCodeWithPrefix(),
                codeWordBitLength);
    }

    /**
     * @return the codeword for this bit code as a String, padded out to {@code codeWordBitLength} with leading zeros
     */
    public String getBitCodeWithPrefix() {
        final String codeWordBinaryString = Integer.toBinaryString(codeWord);
        final StringBuffer binaryWordBuffer = new StringBuffer();
        for (int i = codeWordBinaryString.length(); i < codeWordBitLength; i++) {
            binaryWordBuffer.append('0');
        }
        binaryWordBuffer.append(codeWordBinaryString);
        return binaryWordBuffer.toString();
    }

}
