package htsjdk.samtools.cram.encoding.core.huffmanUtils;

import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.utils.ValidationUtils;

import java.util.HashMap;

/**
 * A utility class that calculates Huffman encoding parameters based on the frequencies of the symbols to be
 * encoded. Note this does not generate the actual (canonical) huffman codes, it only generates non-canonical
 * codes as intermediate step in order to determine the huffman bit code lengths, which are part of the encoding
 * params.
 *
 * @param <T> type of the symbols in the alphabet being huffman-encoded
 */
public class HuffmanParamsCalculator<T> {
    private final HashMap<T, MutableInt> symbolFrequencies = new HashMap<>();

    /**
     * Record one or more observations of a given symbol in the input stream.
     * @param symbol symbol observed
     * @param numberOfObservations number of observations
     */
    public void addSymbolObservations(final T symbol, final int numberOfObservations) {
        ValidationUtils.validateArg(numberOfObservations > 0, "number of observations must be > 0");
        symbolFrequencies.compute(
                symbol,
                (s, f) -> f == null ?
                        new MutableInt(numberOfObservations) :
                        f.incrementValue(numberOfObservations));
    }

    /**
     * @return the HuffmanParams for the given alphabet
     */
    public HuffmanParams<T> getHuffmanParams() {
        ValidationUtils.validateArg(symbolFrequencies.size() > 0, "no symbols to encode");
        final HuffmanTree<T> tree = HuffmanTree.buildTree(symbolFrequencies);
        return tree.getHuffmanParams();
    }

}
