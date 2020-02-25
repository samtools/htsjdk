package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.core.huffmanUtils.*;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.ReadTag;
import htsjdk.samtools.util.Tuple;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class HuffmanTest extends HtsjdkTest {

    @DataProvider(name="singleSymbolAlphabets")
    public Object[][] singleSymbolAlphabets() {
        return new Object[][] {
                // Single-symbol alphabets should always have a codeword with bitLength == 0,
                // no matter how many observations occur in the plaintext.

                // symbol, number of times to write/read back
                {0, 1},
                {0, 50},
                {27, 1},
                {27, 50},
                {99, 1},
                {99, 50},
                {255, 1},
                {255, 50},
                {256, 1},
                {256, 50},
                {257, 1},
                {65535, 1},
                {65535, 50},
                {65536, 1},
                {Integer.MAX_VALUE, 1},
                {Integer.MAX_VALUE, 50},
                {Integer.MIN_VALUE, 1},
                {Integer.MIN_VALUE, 50}
        };
    }

    @Test(dataProvider = "singleSymbolAlphabets")
    public void testSingleSymbolAlphabetCodeWordLengthIsZero(final Integer testSymbol, final int nWriteReads) throws IOException {
        // the huffman code for the one symbol in a single symbol alphabet should always have bitLength == 0
        final HuffmanParams<Integer> huffmanParams = new HuffmanParams(
                Collections.singletonList(testSymbol),
                Collections.singletonList(0));
        final HuffmanCanoncialCodeGenerator<Integer> helper = new HuffmanCanoncialCodeGenerator(huffmanParams);
        Assert.assertEquals(helper.getCanonicalCodeWords().size(), 1);
        Assert.assertEquals(helper.getCodeWordLenForValue(testSymbol), 0);

        byte[] roundTripBytes;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final DefaultBitOutputStream bos = new DefaultBitOutputStream(baos)) {
            IntStream.of(nWriteReads).forEach(n -> Assert.assertEquals(helper.write(bos, testSymbol),0));
            roundTripBytes = baos.toByteArray();
        }

        // since our codeword has bitLength == 0, no bits ever get written to the stream, yet we can still
        // retrieve our data (and we could continue to pull from the stream indefinitely)
        Assert.assertEquals(roundTripBytes.length, 0);
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(roundTripBytes);
             final DefaultBitInputStream bis = new DefaultBitInputStream(bais)) {
            IntStream.of(nWriteReads).forEach(n -> Assert.assertEquals(helper.read(bis), testSymbol));
        }
    }

    @DataProvider(name="verySmallAlphabetsWithFrequencies")
    public Object[][] verySmallAlphabetsWithFrequencies() {
        return new Object[][] {
                {
                        // with 2 symbols, both codewords should all be at most bitLength == 1
                        new LinkedHashMap<Byte, Integer>() {{
                            put((byte)'a',  1000);
                            put((byte)'b',  1001);
                        }}, 1
                },
                {
                        // with 3 symbols, all codewords should be at most bitLength == 2
                        new LinkedHashMap<Byte, Integer>() {{
                            put((byte)'a',  1000);
                            put((byte)'b',  1001);
                            put((byte)'c',  1002);
                        }}, 2
                }
        };
    }

    @Test(dataProvider="verySmallAlphabetsWithFrequencies")
    public void testVerySmallAlphabet(
            final Map<Byte, Integer> symbolFrequencies,
            final int maxExpectedCodeLength) throws IOException {
        final HuffmanCanoncialCodeGenerator<Byte> huffmanHelper = doHuffmanTest(symbolFrequencies);

        // validate that the codewords all have a bitLength no longer than the maximum
        final List<HuffmanBitCode<Byte>> canonicalHuffmanCodeWords = huffmanHelper.getCanonicalCodeWords();
        final List<String> huffmanCodeStrings = canonicalHuffmanCodeWords.stream()
                .map(hc -> hc.getBitCodeWithPrefix())
                .collect(Collectors.toList());
        huffmanCodeStrings.forEach(
                huffmanCodeString -> Assert.assertTrue(huffmanCodeString.length() <= maxExpectedCodeLength));
    }

    @DataProvider(name="integerAlphabetsWithFrequencies")
    public Object[][] integerAlphabetsWithFrequencies() {
        return new Object[][] {
                { // symbol, frequency (in order of increasing frequency)
                        // alphabet is tag ID values
                        new LinkedHashMap<Integer, Integer>() {{
                            put(ReadTag.nameType3BytesToInt("OQ", 'Z'), 178);
                            put(ReadTag.nameType3BytesToInt("X0", 'C'), 179);
                            put(ReadTag.nameType3BytesToInt("X0", 'c'), 180);
                            put(ReadTag.nameType3BytesToInt("X0", 's'), 181);
                            put(ReadTag.nameType3BytesToInt("X1", 'C'), 182);
                            put(ReadTag.nameType3BytesToInt("X1", 'c'), 183);
                            put(ReadTag.nameType3BytesToInt("X1", 's'), 184);
                            put(ReadTag.nameType3BytesToInt("XA", 'Z'), 185);
                            put(ReadTag.nameType3BytesToInt("XC", 'c'), 186);
                            put(ReadTag.nameType3BytesToInt("XT", 'A'), 187);
                            put(ReadTag.nameType3BytesToInt("OP", 'i'), 188);
                            put(ReadTag.nameType3BytesToInt("OC", 'Z'), 189);
                            put(ReadTag.nameType3BytesToInt("BQ", 'Z'), 190);
                            put(ReadTag.nameType3BytesToInt("AM", 'c'), 191);
                        }},
                },
                {
                        // some edge case integer symbols  (in order of increasing frequency)
                        new LinkedHashMap<Integer, Integer>() {{
                            put(Integer.MIN_VALUE,  1000);
                            put(-1000000,           1001);
                            put(-1,                 1002);
                            put(0,                  1003);
                            put(1,                  1004);
                            put(65535,              1005);
                            put(65536,              1006);
                            put(1000000,            1007);
                            put(1000001,            1008);
                            put(Integer.MAX_VALUE,  1009);
                        }},
                },
        };
    }

    @Test(dataProvider="integerAlphabetsWithFrequencies")
    public void testIntegerAlphabet(final Map<Integer, Integer> symbolFrequencies) throws IOException {
        doHuffmanTest(symbolFrequencies);
    }

    @Test
    public void testByteAlphabet() throws IOException {
        final HashMap<Byte, Integer> symbolFrequencies = getAllByteSymbolsWithFrequencies();
        doHuffmanTest(symbolFrequencies);
    }

    @DataProvider(name="dnaBaseAlphabetWithFrequencies")
    public Object[][] dnaBaseAlphabetWithFrequencies() {
        return new Object[][]{
                {
                    new LinkedHashMap<Byte, Integer>() {{
                        put((byte) 'a', 1000);
                        put((byte) 'c', 1001);
                        put((byte) 'g', 1002);
                        put((byte) 't', 1003);
                    }}
                }
        };
    }

    @Test(dataProvider="dnaBaseAlphabetWithFrequencies")
    public void testDNABaseAlphabet(final Map<Integer, Integer> symbolFrequencies) throws IOException {
        doHuffmanTest(symbolFrequencies);
    }

    @DataProvider(name="canonicalCodeWords")
    public Object[][] getCanonicalCodeWords() {
        return new Object[][]{
                // symbol -> (frequency, canonical codeword as a string)
                {
                    // with narrow symbol frequency variance (5-10), we get little codeword length variance (between 2-3 bits)
                    new LinkedHashMap<Integer, Tuple<Integer, String>>() {{
                        put(60, new Tuple<>(10, "00"));
                        put(61, new Tuple<>(9, "01"));
                        put(62, new Tuple<>(8, "100"));
                        put(63, new Tuple<>(7, "101"));
                        put(64, new Tuple<>(6, "110"));
                        put(65, new Tuple<>(5, "111"));
                    }}
                },
                {
                     // with wider symbol frequency variance (5-100), we get larger codeword length variance (between 1-4 bits)
                     new LinkedHashMap<Integer, Tuple<Integer, String>>() {{
                         put(60, new Tuple<>(100, "0"));
                         put(61, new Tuple<>(9, "100"));
                         put(62, new Tuple<>(8, "101"));
                         put(63, new Tuple<>(7, "110"));
                         put(64, new Tuple<>(6, "1110"));
                         put(65, new Tuple<>(5, "1111"));
                     }}
                }
        };
    }

    @Test(dataProvider = "canonicalCodeWords")
    public void testCanonicalCodeWords(final Map<Integer, Tuple<Integer, String>> symbolCodeWordLengths) {
        final HuffmanParamsCalculator<Integer> huffmanCalculator = new HuffmanParamsCalculator();
        for (final Map.Entry<Integer, Tuple<Integer, String>> entry: symbolCodeWordLengths.entrySet()) {
            huffmanCalculator.addSymbolObservations(entry.getKey(), entry.getValue().a);
        }

        final HuffmanParams<Integer> huffmanParams = huffmanCalculator.getHuffmanParams();
        final HuffmanCanoncialCodeGenerator<Integer> codeWordGenerator = new HuffmanCanoncialCodeGenerator(huffmanParams);
        final List<HuffmanBitCode<Integer>> codeWords = codeWordGenerator.getCanonicalCodeWords();

        for (final HuffmanBitCode<Integer> hc : codeWords) {
            Assert.assertEquals(
                    hc.getBitCodeWithPrefix(),
                    symbolCodeWordLengths.get(hc.getSymbol()).b
            );
        }
    }

    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void testExceedMaximumCodewordLength() {
        final HuffmanParamsCalculator<Integer> huffmanCalculator = new HuffmanParamsCalculator();

        // a frequency distribution that matches the first 34 symbols of the fibonacci sequence results
        // in a completely right-skewed huffman tree with canonical codewords that exceeds the 31 bit
        // maximum codeword length
        final int N_SYMBOLS = 34;
        Stream.iterate(new Tuple<>(0, 1), f -> new Tuple<>(f.b, f.a + f.b))
                .filter(f -> f.a != 0)
                .limit(N_SYMBOLS)
                .forEach(f -> huffmanCalculator.addSymbolObservations(f.a, f.a));

        huffmanCalculator.getHuffmanParams();
    }

    private static <T> HuffmanCanoncialCodeGenerator<T> doHuffmanTest(final Map<T, Integer> symbolFrequencies) throws IOException {
        final HuffmanParamsCalculator<T> huffmanCalculator = new HuffmanParamsCalculator();
        for (final Map.Entry<T, Integer> entry: symbolFrequencies.entrySet()) {
            huffmanCalculator.addSymbolObservations(entry.getKey(), entry.getValue());
        }

        final HuffmanParams<T> huffmanParams = huffmanCalculator.getHuffmanParams();
        final HuffmanCanoncialCodeGenerator<T> helper = new HuffmanCanoncialCodeGenerator(huffmanParams);

        // first, validate that the symbols with the most observations have the shortest codes
        int lastCodeLen = Integer.MAX_VALUE;
        for (final Map.Entry<T, Integer> entry: symbolFrequencies.entrySet()) {
            final int codeWordLenForValue = helper.getCodeWordLenForValue(entry.getKey());
            Assert.assertTrue(codeWordLenForValue <= lastCodeLen);
            lastCodeLen = codeWordLenForValue;
        }

        // validate that the HuffmanCodes satisfy the prefix rule that no codeword is a prefix of any other codeword
        final List<HuffmanBitCode<T>> canonicalHuffmanCodes = helper.getCanonicalCodeWords();
        final List<String> huffmanCodeStrings = canonicalHuffmanCodes.stream()
                .map(hc -> hc.getBitCodeWithPrefix()).collect(Collectors.toList());
        for (final String sourceCodeString : huffmanCodeStrings) {
            for (final String targetCodeString : huffmanCodeStrings) {
                if (!targetCodeString.equals(sourceCodeString)) {
                    Assert.assertFalse(targetCodeString.startsWith(sourceCodeString),
                            String.format("Target huffman code (%s) is prefix for source huffman code (%s)",
                                    targetCodeString,
                                    sourceCodeString));
                }
            }
        }

        // write each symbol to a stream a number of times that corresponds to it's frequency (writing the symbol
        // that number of times isn't essential for this test since any number of times greater than one would
        // suffice to ensure that we can read the symbol back by reading its code, but it matches the expected
        // usage pattern)
        byte[] roundTripBytes;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final DefaultBitOutputStream bos = new DefaultBitOutputStream(baos)) {
            for (Map.Entry<T, Integer> entry: symbolFrequencies.entrySet()) {
                for (int j = 0; j < entry.getValue(); j++) {
                    helper.write(bos, entry.getKey());
                }
            }
            bos.flush();
            roundTripBytes = baos.toByteArray();
        }

        try (final ByteArrayInputStream bais = new ByteArrayInputStream(roundTripBytes);
             final DefaultBitInputStream bis = new DefaultBitInputStream(bais)) {
            for (final Map.Entry<T, Integer> entry: symbolFrequencies.entrySet()) {
                for (int j = 0; j < entry.getValue(); j++) {
                    Assert.assertEquals(helper.read(bis), entry.getKey());
                }
            }
        }
        return helper;
    }

    private HashMap<Byte, Integer> getAllByteSymbolsWithFrequencies() {
        final byte STARTING_BYTE_SYMBOL = -128;
        final int NSYMBOLS = 256;
        final int STARTING_FREQUENCY = 100;

        final HashMap<Byte, Integer> symbolFrequencies= new LinkedHashMap<>(NSYMBOLS);

        // add every possible byte symbol, each with increasing frequency
        int frequency = STARTING_FREQUENCY;
        byte b = STARTING_BYTE_SYMBOL;
        for (int i = 0; i < NSYMBOLS ; i++) {
            symbolFrequencies.put(b++, frequency++);
        }
        return symbolFrequencies;
    }

}
