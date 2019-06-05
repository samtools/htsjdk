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

import htsjdk.utils.ValidationUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class for carrying around  encoding parameters for a canonical Huffman encoder.
 *
 * The HuffmanParams consist of an array of symbols and an array of corresponding codeWordLengths.
 * The actual codewords themselves are not part of the params since they can be recalculated on demand.
 * Therefore, the params are independent of the canonicalization state (the "canonical" huffman params
 * are the same as the non-canonical params for a given set of symbol/frequencies; it is only the code
 * words themselves are different after canonicalization; the code word lengths are the preserved).
 *
 * @param <T> type of the symbols in the alphabet being huffman-encoded
 */
public final class HuffmanParams<T> {
    private final List<T> symbols;
    private final List<Integer> codeWordLengths;

    /**
     * @param symbols symbols being encoded
     * @param codeWordLengths code word lengths for each symbol
     */
    public HuffmanParams(final List<T> symbols, final List<Integer> codeWordLengths) {
        ValidationUtils.nonNull(symbols, "requires non-null symbols");
        ValidationUtils.nonNull(symbols.size() > 0, "requires symbols");
        ValidationUtils.nonNull(codeWordLengths, "requires non-null codeWordLengths");
        ValidationUtils.nonNull(codeWordLengths, "requires codeWordLengths");
        ValidationUtils.nonNull(symbols.size() == codeWordLengths.size(),
                "symbols and codeWordLengths out of sync");

        this.symbols = Collections.unmodifiableList(symbols);
        this.codeWordLengths = Collections.unmodifiableList(codeWordLengths);
    }

    /**
     * @return the list of symbols for these params
     */
    public List<T> getSymbols() {
        return symbols;
    }

    /**
     * @return the list of code word bit lengths for these params
     */
    public List<Integer> getCodeWordLengths() {
        return codeWordLengths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HuffmanParams<?> that = (HuffmanParams<?>) o;

        if (!getSymbols().equals(that.getSymbols())) return false;
        return getCodeWordLengths().equals(that.getCodeWordLengths());
    }

    @Override
    public int hashCode() {
        int result = getSymbols().hashCode();
        result = 31 * result + getCodeWordLengths().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("Symbols: %s BitLengths %s",
                symbols.stream().map(Object::toString).collect(Collectors.joining(";")),
                codeWordLengths.stream().map(Object::toString).collect(Collectors.joining(";")));
    }
}
