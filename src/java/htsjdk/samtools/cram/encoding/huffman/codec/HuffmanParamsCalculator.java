package htsjdk.samtools.cram.encoding.huffman.codec;

import htsjdk.samtools.cram.common.MutableInt;
import htsjdk.samtools.cram.encoding.huffman.HuffmanCode;
import htsjdk.samtools.cram.encoding.huffman.HuffmanTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * The purpose is to calculate Huffman encoding parameters.
 * Critical: ALL values must be shown to the calculator before calling the calculate() method.
 */
public class HuffmanParamsCalculator {
    private final HashMap<Integer, MutableInt> countMap = new HashMap<Integer, MutableInt>();
    private int[] values = new int[]{};
    private int[] bitLens = new int[]{};

    /**
     * Show a value to the calculator
     *
     * @param value an integer value to be encoded
     */
    public void add(final int value) {
        MutableInt counter = countMap.get(value);
        if (counter == null) {
            counter = new MutableInt();
            countMap.put(value, counter);
        }
        counter.value++;
    }

    /**
     * Show a value to the calculator more than once.
     *
     * @param value an integer value to be encoded
     * @param inc   how many times the value occurs
     */
    public void add(final Integer value, final int inc) {
        MutableInt counter = countMap.get(value);
        if (counter == null) {
            counter = new MutableInt();
            countMap.put(value, counter);
        }
        counter.value += inc;
    }

    /**
     * Show a value to the calculator more than once.
     *
     * @param value an integer value to be encoded
     * @param inc   how many times the value occurs
     */
    public void add(final Byte value, final int inc) {
        MutableInt counter = countMap.get(value & 0xFF);
        if (counter == null) {
            counter = new MutableInt();
            countMap.put(value & 0xFF, counter);
        }
        counter.value += inc;
    }

    /**
     * Returns values as an arrays of bytes assuming there is no byte overflow.
     *
     * @return an array of values as array of bytes instead of ints
     */
    public byte[] getValuesAsBytes() {
        final byte[] byteValues = new byte[getValues().length];
        for (int i = 0; i < byteValues.length; i++) {
            byteValues[i] = (byte) (0xFF & getValues()[i]);
        }

        return byteValues;
    }

    /**
     * Call this method after all values have been shown. This will calculate Huffman encoding parameters:
     * distinct values and bit lengths associated with each of them.
     */
    public void calculate() {
        final HuffmanTree<Integer> tree;
        {
            final int size = countMap.size();
            final int[] frequencies = new int[size];
            final int[] values = new int[size];

            int i = 0;
            for (final Integer key : countMap.keySet()) {
                values[i] = key;
                frequencies[i] = countMap.get(key).value;
                i++;
            }
            tree = HuffmanCode.buildTree(frequencies, autobox(values));
        }

        final List<Integer> valueList = new ArrayList<Integer>();
        final List<Integer> lens = new ArrayList<Integer>();
        HuffmanCode.getValuesAndBitLengths(valueList, lens, tree);

        final BitCode[] codes = new BitCode[valueList.size()];
        for (int i = 0; i < valueList.size(); i++) {
            codes[i] = new BitCode(valueList.get(i), lens.get(i));
        }
        Arrays.sort(codes);

        values = new int[codes.length];
        bitLens = new int[codes.length];

        for (int i = 0; i < codes.length; i++) {
            final BitCode code = codes[i];
            getBitLens()[i] = code.length;
            getValues()[i] = code.value;
        }
    }

    private static Integer[] autobox(final int[] array) {
        final Integer[] newArray = new Integer[array.length];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    public int[] getValues() {
        return values;
    }

    public int[] getBitLens() {
        return bitLens;
    }

    private static class BitCode implements Comparable<BitCode> {
        final int value;
        final int length;

        public BitCode(final int value, final int length) {
            this.value = value;
            this.length = length;
        }

        @Override
        public int compareTo(@SuppressWarnings("NullableProblems") final BitCode o) {
            final int result = value - o.value;
            if (result != 0) {
                return result;
            }
            return length - o.length;
        }
    }
}