/*
 * The MIT License
 *
 * Copyright (c) 2017 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StringUtilTest extends HtsjdkTest {

    /** Splits with {@link StringUtil#split} into an array of the given capacity and returns the tokens parsed. */
    private static String[] split(final String s, final int capacity) {
        final String[] tokens = new String[capacity];
        final int count = StringUtil.split(s, tokens, ':');
        return Arrays.copyOf(tokens, count);
    }

    /** As {@link #split} but with {@link StringUtil#splitConcatenateExcessTokens}. */
    private static String[] splitConcatenatingExcess(final String s, final int capacity) {
        final String[] tokens = new String[capacity];
        final int count = StringUtil.splitConcatenateExcessTokens(s, tokens, ':');
        return Arrays.copyOf(tokens, count);
    }

    @Test
    public void splitReturnsEachTokenBetweenDelimiters() {
        Assert.assertEquals(split("A:BB", 10), new String[] {"A", "BB"});
        Assert.assertEquals(split("A:BB:C", 10), new String[] {"A", "BB", "C"});
        Assert.assertEquals(split("A:BB:C:DDD", 10), new String[] {"A", "BB", "C", "DDD"});
    }

    @Test
    public void splitDropsAnEmptyTrailingToken() {
        Assert.assertEquals(split("A:BB:", 10), new String[] {"A", "BB"});
        Assert.assertEquals(split("A:", 10), new String[] {"A"});
    }

    @Test
    public void splitReturnsTheWholeStringWhenThereIsNoDelimiter() {
        Assert.assertEquals(split("A", 10), new String[] {"A"});
    }

    @Test
    public void splitConcatenateExcessTokensReturnsEachTokenWhenThereIsRoom() {
        Assert.assertEquals(splitConcatenatingExcess("A:BB", 3), new String[] {"A", "BB"});
        Assert.assertEquals(splitConcatenatingExcess("A:BB:C", 3), new String[] {"A", "BB", "C"});
    }

    @Test
    public void splitConcatenateExcessTokensLeavesExcessTokensJoinedInTheLastToken() {
        Assert.assertEquals(splitConcatenatingExcess("A:BB:C:DDD", 3), new String[] {"A", "BB", "C:DDD"});
    }

    @Test
    public void splitConcatenateExcessTokensKeepsATrailingDelimiterInTheLastToken() {
        Assert.assertEquals(splitConcatenatingExcess("A:BB:C:", 3), new String[] {"A", "BB", "C:"});
    }

    @Test
    public void splitConcatenateExcessTokensDropsAnEmptyTrailingToken() {
        Assert.assertEquals(splitConcatenatingExcess("A:BB:", 3), new String[] {"A", "BB"});
        Assert.assertEquals(splitConcatenatingExcess("A:", 3), new String[] {"A"});
    }

    @Test
    public void splitConcatenateExcessTokensReturnsTheWholeStringWhenThereIsNoDelimiter() {
        Assert.assertEquals(splitConcatenatingExcess("A", 3), new String[] {"A"});
    }

    @Test
    public void joinSeparatesTheTokensWithTheSeparator() {
        Assert.assertEquals(StringUtil.join(",", 1, "hello", 'T'), "1,hello,T");
    }

    @Test
    public void joinOfNoTokensIsEmpty() {
        Assert.assertEquals(StringUtil.join(","), "");
    }

    @Test
    public void hammingDistanceOfTwoEmptyStringsIsZero() {
        Assert.assertEquals(StringUtil.hammingDistance("", ""), 0);
    }

    @Test
    public void hammingDistanceCountsMismatchedPositions() {
        Assert.assertEquals(StringUtil.hammingDistance("ATAC", "GCAT"), 3);
    }

    @Test
    public void hammingDistanceOfIdenticalStringsIsZero() {
        Assert.assertEquals(StringUtil.hammingDistance("ATAGC", "ATAGC"), 0);
    }

    @Test
    public void hammingDistanceIsCaseSensitive() {
        Assert.assertEquals(StringUtil.hammingDistance("ATAC", "atac"), 4);
    }

    @Test
    public void hammingDistanceCountsNsLikeAnyOtherBase() {
        Assert.assertEquals(StringUtil.hammingDistance("nAGTN", "nAGTN"), 0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void hammingDistanceThrowsWhenOnlyOneStringIsEmpty() {
        StringUtil.hammingDistance("", "ABC");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void hammingDistanceThrowsWhenTheStringsDifferInLength() {
        StringUtil.hammingDistance("Abc", "wxyz");
    }

    @Test
    public void isWithinHammingDistanceIsTrueWhenTheDistanceEqualsTheLimit() {
        Assert.assertTrue(StringUtil.isWithinHammingDistance("ATAC", "GCAT", 3));
    }

    @Test
    public void isWithinHammingDistanceIsFalseWhenTheDistanceExceedsTheLimit() {
        for (int limit = 0; limit < 3; limit++) {
            Assert.assertFalse(StringUtil.isWithinHammingDistance("ATAC", "GCAT", limit), "limit " + limit);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void isWithinHammingDistanceThrowsWhenOnlyOneStringIsEmpty() {
        StringUtil.isWithinHammingDistance("", "ABC", 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void isWithinHammingDistanceThrowsWhenTheStringsDifferInLength() {
        StringUtil.isWithinHammingDistance("Abc", "wxyz", 2);
    }

    @Test
    public void toLowerCaseOfAByteAgreesWithCharacterToLowerCaseForAllAscii() {
        for (int i = 0; i <= 127; i++) {
            Assert.assertEquals(StringUtil.toLowerCase((byte) i), (byte) Character.toLowerCase((char) i), "byte " + i);
        }
    }

    @Test
    public void toUpperCaseOfAByteAgreesWithCharacterToUpperCaseForAllAscii() {
        for (int i = 0; i <= 127; i++) {
            Assert.assertEquals(StringUtil.toUpperCase((byte) i), (byte) Character.toUpperCase((char) i), "byte " + i);
        }
    }

    @Test
    public void toUpperCaseOfAByteArrayUpperCasesItInPlace() {
        final byte[] bytes = "atACgtaCGTgatcCAtATATgATtatgacNryuAN".getBytes(StandardCharsets.US_ASCII);
        StringUtil.toUpperCase(bytes);
        Assert.assertEquals(new String(bytes, StandardCharsets.US_ASCII), "ATACGTACGTGATCCATATATGATTATGACNRYUAN");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void assertCharactersNotInStringThrowsWhenTheStringContainsOneOfTheCharacters() {
        StringUtil.assertCharactersNotInString("Hello World!", ' ', '!', '_');
    }

    @Test
    public void assertCharactersNotInStringReturnsTheStringWhenItContainsNoneOfTheCharacters() {
        Assert.assertEquals(StringUtil.assertCharactersNotInString("HelloWorld", ' ', '!', '_'), "HelloWorld");
    }

    private static final String TEXT_FOR_WRAPPING = "This is a little bit\nof text with nice short\nlines.";

    @Test
    public void wordWrapLeavesLinesShorterThanTheLimitUnchanged() {
        Assert.assertEquals(StringUtil.wordWrap(TEXT_FOR_WRAPPING, 50), TEXT_FOR_WRAPPING);
    }

    @Test
    public void wordWrapBreaksLinesLongerThanTheLimit() {
        final List<String> lines =
                StringUtil.wordWrap(TEXT_FOR_WRAPPING, 15).lines().toList();
        Assert.assertEquals(lines.size(), 5);
        for (final String line : lines) {
            Assert.assertTrue(line.length() <= 15, "line too long: '" + line + "'");
        }
    }

    @Test
    public void intValuesToStringOfIntsIsCommaSeparated() {
        final int[] ints = {1, 2, 3, 11, 22, 33, Integer.MIN_VALUE, 0, Integer.MAX_VALUE};
        Assert.assertEquals(StringUtil.intValuesToString(ints), "1, 2, 3, 11, 22, 33, -2147483648, 0, 2147483647");
    }

    @Test
    public void intValuesToStringOfShortsIsCommaSeparated() {
        final short[] shorts = {1, 2, 3, 11, 22, 33, Short.MIN_VALUE, 0, Short.MAX_VALUE};
        Assert.assertEquals(StringUtil.intValuesToString(shorts), "1, 2, 3, 11, 22, 33, -32768, 0, 32767");
    }
}
