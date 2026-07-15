/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.CollectionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SAMRecordQueryNameNaturalComparatorTest extends HtsjdkTest {
    private static final SAMRecordQueryNameNaturalComparator COMPARATOR = new SAMRecordQueryNameNaturalComparator();

    /** Convenience wrapper returning the sign (-1/0/1) of a natural read-name comparison. */
    private static int cmp(final String a, final String b) {
        return Integer.signum(SAMRecordQueryNameNaturalComparator.compareNatural(a, b));
    }

    @Test
    public void mixedNamesSortInNaturalOrder() {
        // A set of names with a single, unambiguous natural ordering (no leading-zero ties, which
        // samtools treats as equal). Numeric runs order by value: abc03 (3) < abc5 (5) < abc8 (8) < abc17 (17).
        final List<String> names = CollectionUtil.makeList(
                "abc", "abc+5", "abc- 5", "abc.d", "abc03", "abc5", "abc8", "abc17", "abc17.+", "abc17.2", "abc17.d",
                "abc59", "abcd");

        final List<String> shuffled = new ArrayList<>(names);
        Collections.reverse(shuffled); // start from a non-sorted order so sorting does real work
        shuffled.sort(SAMRecordQueryNameNaturalComparator::compareNatural);

        Assert.assertEquals(shuffled, names);
    }

    @Test
    public void numericRunsCompareByValueNotLexicographically() {
        // The whole point of natural ordering: read2 < read10 even though "10" < "2" lexicographically.
        Assert.assertEquals(cmp("read2", "read10"), -1);
        Assert.assertEquals(cmp("read10", "read2"), 1);
        Assert.assertEquals(cmp("read9", "read10"), -1);
    }

    @Test
    public void pureNumericNamesSortByValue() {
        Assert.assertEquals(cmp("2", "10"), -1);
        Assert.assertEquals(cmp("10", "100"), -1);
        Assert.assertEquals(cmp("100", "99"), 1);
    }

    @Test
    public void leadingZerosDoNotAffectOrder() {
        // Numeric runs with equal value compare equal regardless of leading zeros, matching samtools.
        Assert.assertEquals(cmp("x008", "x08"), 0);
        Assert.assertEquals(cmp("x08", "x8"), 0);
        Assert.assertEquals(cmp("x008", "x8"), 0);
        // All-zero runs are likewise equal regardless of how many zeros.
        Assert.assertEquals(cmp("00", "0"), 0);
    }

    @Test
    public void runsWithDifferentValuesAreOrderedRegardlessOfLeadingZeros() {
        // "010" (value 10) vs "09" (value 9): compared by value, not by the leading zero.
        Assert.assertEquals(cmp("010", "09"), 1);
        Assert.assertEquals(cmp("007", "8"), -1);
    }

    @Test
    public void largeNumericRunsDoNotOverflow() {
        // Numeric runs beyond Long.MAX_VALUE (9223372036854775807) must still order by value.
        // A run parsed into a long would overflow here; the digit-by-digit comparison must not.
        final String twoPow64 = "read18446744073709551616"; // 2^64, 20 digits
        Assert.assertEquals(cmp("read9", twoPow64), -1, "single digit must sort before a 20-digit number");
        Assert.assertEquals(cmp(twoPow64, "read9"), 1);

        // Two 20-digit numbers differing only in the final digit.
        Assert.assertEquals(cmp("read18446744073709551616", "read18446744073709551617"), -1);

        // A 21-digit number is larger than any 20-digit number.
        Assert.assertEquals(cmp("read99999999999999999999", "read100000000000000000000"), -1);
    }

    @Test
    public void equalNamesCompareEqual() {
        Assert.assertEquals(cmp("read1", "read1"), 0);
        Assert.assertEquals(cmp("abc17.2", "abc17.2"), 0);
        Assert.assertEquals(cmp("", ""), 0);
    }

    @Test
    public void shorterPrefixSortsBeforeLongerName() {
        Assert.assertEquals(cmp("abc", "abc0"), -1);
        Assert.assertEquals(cmp("abc", "abcd"), -1);
        Assert.assertEquals(cmp("read", "read1"), -1);
    }

    @Test
    public void emptyNameSortsBeforeAnyNonEmptyName() {
        Assert.assertEquals(cmp("", "a"), -1);
        Assert.assertEquals(cmp("a", ""), 1);
    }

    @Test
    public void comparisonIsAntisymmetric() {
        final List<String> names = CollectionUtil.makeList(
                "abc", "abc03", "abc8", "abc08", "read2", "read10", "read18446744073709551616", "", "abc17.2");
        for (final String a : names) {
            for (final String b : names) {
                Assert.assertEquals(cmp(a, b), -cmp(b, a), "antisymmetry violated for '" + a + "' vs '" + b + "'");
            }
        }
    }

    @Test
    public void recordLevelComparisonUsesNaturalReadNameOrder() {
        // Confirm the natural ordering is wired through compare(SAMRecord, SAMRecord) via fileOrderCompare.
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord read2 = unmapped(header, "read2");
        final SAMRecord read10 = unmapped(header, "read10");

        Assert.assertTrue(COMPARATOR.compare(read2, read10) < 0);
        Assert.assertTrue(COMPARATOR.compare(read10, read2) > 0);
        Assert.assertEquals(COMPARATOR.fileOrderCompare(read2, read10), -1);
    }

    @Test
    public void inheritedPairTieBreakStillApplies() {
        // With identical read names, the inherited queryname tie-breaking still orders first-of-pair
        // before second-of-pair.
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord first = unmapped(header, "q");
        first.setReadPairedFlag(true);
        first.setFirstOfPairFlag(true);
        final SAMRecord second = unmapped(header, "q");
        second.setReadPairedFlag(true);
        second.setSecondOfPairFlag(true);

        Assert.assertEquals(COMPARATOR.fileOrderCompare(first, second), 0, "same read name compares equal");
        Assert.assertTrue(COMPARATOR.compare(first, second) < 0, "first of pair sorts before second of pair");
        Assert.assertTrue(COMPARATOR.compare(second, first) > 0);
    }

    private static SAMRecord unmapped(final SAMFileHeader header, final String name) {
        final SAMRecord record = new SAMRecord(header);
        record.setReadName(name);
        record.setReadUnmappedFlag(true);
        return record;
    }
}
