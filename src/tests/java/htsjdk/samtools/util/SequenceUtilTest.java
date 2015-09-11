/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.TextCigarCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author alecw@broadinstitute.org
 */
public class SequenceUtilTest {
    private static final String HEADER = "@HD\tVN:1.0\tSO:unsorted\n";
    private static final String SEQUENCE_NAME=
        "@SQ\tSN:phix174.seq\tLN:5386\tUR:/seq/references/PhiX174/v0/PhiX174.fasta\tAS:PhiX174\tM5:3332ed720ac7eaa9b3655c06f6b9e196";

    @Test
    public void testExactMatch() {
        final SAMSequenceDictionary sd1 = makeSequenceDictionary(5386, "/seq/references/PhiX174/v0/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196");
        final SAMSequenceDictionary sd2 = makeSequenceDictionary(5386, "/seq/references/PhiX174/v0/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196");
        SequenceUtil.assertSequenceDictionariesEqual(sd1, sd2);
    }

    @Test(expectedExceptions = SequenceUtil.SequenceListsDifferException.class)
    public void testMismatch() {
        final SAMSequenceDictionary sd1 = makeSequenceDictionary(5386, "/seq/references/PhiX174/v0/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196");
        final SAMSequenceDictionary sd2 = makeSequenceDictionary(5386, "/seq/references/PhiX174/v0/PhiX174.fasta",
                "deadbeef");
        SequenceUtil.assertSequenceDictionariesEqual(sd1, sd2);
        Assert.fail();
    }

    @Test
    public void testFileColonDifference() {
        final SAMSequenceDictionary sd1 = makeSequenceDictionary(5386, "/seq/references/PhiX174/v0/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196");
        final SAMSequenceDictionary sd2 = makeSequenceDictionary(5386, "file:/seq/references/PhiX174/v0/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196");
        SequenceUtil.assertSequenceDictionariesEqual(sd1, sd2);
    }

    @Test
    public void testURDifferent() {
        final SAMSequenceDictionary sd1 = makeSequenceDictionary(5386, "/seq/references/PhiX174/v0/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196");
        final SAMSequenceDictionary sd2 = makeSequenceDictionary(5386, "file:/seq/references/PhiX174/v1/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196");
        SequenceUtil.assertSequenceDictionariesEqual(sd1, sd2);
    }

    private SAMSequenceDictionary makeSequenceDictionary(final int length, final String ur, final String m5) {
        final String s = HEADER +
                String.format("@SQ\tSN:phix174.seq\tLN:%d\tUR:%s\tAS:PhiX174\tM5:%s\n", length, ur, m5);
        return new SAMTextHeaderCodec().decode(new StringLineReader(s), null).getSequenceDictionary();
    }

    @Test(dataProvider = "makeReferenceFromAlignment")
    public void testMakeReferenceFromAlignment(final String seq, final String cigar, final String md,
                                               boolean includeReferenceBasesForDeletions,
                                               final String expectedReference) {
        final SAMRecord rec = new SAMRecord(null);
        rec.setReadName("test");
        rec.setReadString(seq);
        rec.setCigarString(cigar);
        rec.setAttribute(SAMTag.MD.name(), md);
        final byte[] refBases = SequenceUtil.makeReferenceFromAlignment(rec, includeReferenceBasesForDeletions);
        Assert.assertEquals(StringUtil.bytesToString(refBases), expectedReference);
    }

    @DataProvider(name = "makeReferenceFromAlignment")
    public Object[][] testMakeReferenceFromAlignmentDataProvider() {
        return new Object[][] {
               {"ACGTACGTACGT", "12M2H", "4GAAA4", true, "ACGTGAAAACGT"},
                {"ACGTACGTACGT", "2H12M", "12", false, "ACGTACGTACGT"},
                {"ACGTACGTACGT", "4M4I4M2H", "8", false, "ACGT----ACGT"},
                {"ACGTACGTACGT", "2S4M2I4M2S", "8", false, "00GTAC--ACGT00"},
                {"ACGTACGTACGT", "6M2D6M2H", "4GA^TT0TG4", true, "ACGTGATTTGACGT"},
                {"ACGTACGTACGT", "6M2D6M2H", "4GA^TT0TG4", false, "ACGTGATGACGT"},
                // When CIGAR has N, MD will not have skipped bases.
                {"ACGTACGTACGT", "6M2N6M2H", "4GA0TG4", true, "ACGTGANNTGACGT"},
                {"ACGTACGTACGT", "6M2N6M2H", "4GA0TG4", false, "ACGTGATGACGT"},
                {"ACGTACGTACGT", "6M2N6M2H", "4GATG4", true, "ACGTGANNTGACGT"},
                {"ACGTACGTACGT", "6M2N6M2H", "4GATG4", false, "ACGTGATGACGT"},
        };
    }

    @Test(dataProvider = "mismatchCountsDataProvider")
    public void testCountMismatches(final String readString, final String cigar, final String reference, final int expectedNumMismatches) {
        final SAMRecord rec = new SAMRecord(null);
        rec.setReadName("test");
        rec.setReadString(readString);
        rec.setCigarString(cigar);

        final byte[] refBases = StringUtil.stringToBytes(reference);
        final int n = SequenceUtil.countMismatches(rec, refBases, -1);
        Assert.assertEquals(n, expectedNumMismatches);
    }

    @DataProvider(name="mismatchCountsDataProvider")
    public Object[][] testMakeMismatchCountsDataProvider() {
        return new Object[][] {
                {"A", "1M", "A", 0},
                {"A", "1M", "R", 0},     // R=A|G
                {"G", "1M", "R", 0},     // R=A|G
                {"C", "1M", "R", 1},     // R=A|G
                {"T", "1M", "R", 1},     // R=A|G
                {"N", "1M", "R", 0},     // R=A|G
                {"R", "1M", "A", 0},     // R=A|G
                {"R", "1M", "C", 1},     // R=A|G
                {"R", "1M", "G", 0},     // R=A|G
                {"R", "1M", "T", 1},     // R=A|G
                {"R", "1M", "N", 0},     // R=A|G
                {"N", "1M", "N", 0}      // R=A|G
        };
    }

    @Test(dataProvider = "countInsertedAndDeletedBasesTestCases")
    public void testCountInsertedAndDeletedBases(final String cigarString, final int insertedBases, final int deletedBases) {
        final Cigar cigar = TextCigarCodec.decode(cigarString);
        Assert.assertEquals(SequenceUtil.countInsertedBases(cigar), insertedBases);
        Assert.assertEquals(SequenceUtil.countDeletedBases(cigar), deletedBases);
    }

    @DataProvider(name = "countInsertedAndDeletedBasesTestCases")
    public Object[][] countInsertedAndDeletedBasesTestCases() {
        return new Object[][] {
                {"2H2S32M", 0, 0},
                {"2H2S32M12I2M2I3M", 14, 0},
                {"32M2D10M", 0, 2},
                {"32M2D10M3D1M", 0, 5},
                {"2H2S32M12I2M3D1M2I3M2D1M", 14, 5}
        };
    }

    @DataProvider(name = "testKmerGenerationTestCases")
    public Object[][] testKmerGenerationTestCases() {
        return new Object[][] {
                {0, new String[]{""}},
                {1, new String[]{"A","C","G","T"}},
                {2, new String[]{"AA","AC","AG","AT","CA","CC","CG","CT","GA","GC","GG","GT","TA","TC","TG","TT"}}
        };
    }

    @Test(dataProvider = "testKmerGenerationTestCases")
    public void testKmerGeneration(final int length, final String[] expectedKmers) {
        final Set<String> actualSet = new HashSet<String>();
        for (final byte[] kmer : SequenceUtil.generateAllKmers(length)) {
            actualSet.add(StringUtil.bytesToString(kmer));
        }
        final Set<String> expectedSet = new HashSet<String>(Arrays.asList(expectedKmers));
        Assert.assertTrue(actualSet.equals(expectedSet));
    }

    @Test(dataProvider = "basesEqualDataProvider")
    public void testBasesEqual(final char base1, final char base2, final boolean expectedResult) {
        final char[] base1UcLc = new char[] { toUpperCase(base1), toLowerCase(base1) };
        final char[] base2UcLc = new char[] { toUpperCase(base2), toLowerCase(base2) };
        // Test over all permutations - uc vs uc, uc vs lc, lc vs uc, lc vs lc
        for (char theBase1 : base1UcLc) {
            for (char theBase2 : base2UcLc) {
                // Test that order does not matter
                boolean result = SequenceUtil.basesEqual((byte) theBase1, (byte) theBase2);
                Assert.assertEquals(result, expectedResult, "basesEqual test failed for '" + theBase1 + "' vs. '" + theBase2 + "'");

                result = SequenceUtil.basesEqual((byte) theBase2, (byte) theBase1);
                Assert.assertEquals(result, expectedResult, "basesEqual test failed for '" + theBase2 + "' vs. '" + theBase1 + "'");
            }
        }
    }

    @DataProvider(name="basesEqualDataProvider")
    public Object[][] testBasesEqualDataProvider() {
        return new Object[][] {
                {'A', 'A', true},
                {'A', 'C', false},
                {'A', 'G', false},
                {'A', 'T', false},
                {'A', 'M', true},       // M = A|C
                {'A', 'R', true},       // R = A|G
                {'A', 'W', true},       // W = A|T
                {'A', 'S', false},      // S = C|G
                {'A', 'Y', false},      // Y = C|T
                {'A', 'K', false},      // K = G|T
                {'A', 'V', true},      // V = A|C|G
                {'A', 'H', true},      // H = A|C|T
                {'A', 'D', true},      // D = A|G|T
                {'A', 'B', false},      // B = C|G|T
                {'A', 'N', true},      // N = A|C|G|T

                {'C', 'C', true},
                {'C', 'G', false},
                {'C', 'T', false},
                {'C', 'M', true},       // M = A|C
                {'C', 'R', false},       // R = A|G
                {'C', 'W', false},       // W = A|T
                {'C', 'S', true},      // S = C|G
                {'C', 'Y', true},      // Y = C|T
                {'C', 'K', false},      // K = G|T
                {'C', 'V', true},      // V = A|C|G
                {'C', 'H', true},      // H = A|C|T
                {'C', 'D', false},      // D = A|G|T
                {'C', 'B', true},      // B = C|G|T
                {'C', 'N', true},      // N = A|C|G|T

                {'G', 'G', true},
                {'G', 'T', false},
                {'G', 'M', false},       // M = A|C
                {'G', 'R', true},       // R = A|G
                {'G', 'W', false},       // W = A|T
                {'G', 'S', true},      // S = C|G
                {'G', 'Y', false},      // Y = C|T
                {'G', 'K', true},      // K = G|T
                {'G', 'V', true},      // V = A|C|G
                {'G', 'H', false},      // H = A|C|T
                {'G', 'D', true},      // D = A|G|T
                {'G', 'B', true},      // B = C|G|T
                {'G', 'N', true},      // N = A|C|G|T

                {'T', 'T', true},
                {'T', 'M', false},       // M = A|C
                {'T', 'R', false},       // R = A|G
                {'T', 'W', true},       // W = A|T
                {'T', 'S', false},      // S = C|G
                {'T', 'Y', true},      // Y = C|T
                {'T', 'K', true},      // K = G|T
                {'T', 'V', false},      // V = A|C|G
                {'T', 'H', true},      // H = A|C|T
                {'T', 'D', true},      // D = A|G|T
                {'T', 'B', true},      // B = C|G|T
                {'T', 'N', true},      // N = A|C|G|T

                {'M', 'M', true},       // M = A|C
                {'M', 'R', true},       // R = A|G
                {'M', 'W', true},       // W = A|T
                {'M', 'S', true},      // S = C|G
                {'M', 'Y', true},      // Y = C|T
                {'M', 'K', false},      // K = G|T
                {'M', 'V', true},      // V = A|C|G
                {'M', 'H', true},      // H = A|C|T
                {'M', 'D', true},      // D = A|G|T
                {'M', 'B', true},      // B = C|G|T
                {'M', 'N', true},      // N = A|C|G|T

                {'R', 'R', true},       // R = A|G
                {'R', 'W', true},       // W = A|T
                {'R', 'S', true},      // S = C|G
                {'R', 'Y', false},      // Y = C|T
                {'R', 'K', true},      // K = G|T
                {'R', 'V', true},      // V = A|C|G
                {'R', 'H', true},      // H = A|C|T
                {'R', 'D', true},      // D = A|G|T
                {'R', 'B', true},      // B = C|G|T
                {'R', 'N', true},      // N = A|C|G|T

                {'W', 'W', true},       // W = A|T
                {'W', 'S', false},      // S = C|G
                {'W', 'Y', true},      // Y = C|T
                {'W', 'K', true},      // K = G|T
                {'W', 'V', true},      // V = A|C|G
                {'W', 'H', true},      // H = A|C|T
                {'W', 'D', true},      // D = A|G|T
                {'W', 'B', true},      // B = C|G|T
                {'W', 'N', true},      // N = A|C|G|T

                {'S', 'S', true},      // S = C|G
                {'S', 'Y', true},      // Y = C|T
                {'S', 'K', true},      // K = G|T
                {'S', 'V', true},      // V = A|C|G
                {'S', 'H', true},      // H = A|C|T
                {'S', 'D', true},      // D = A|G|T
                {'S', 'B', true},      // B = C|G|T
                {'S', 'N', true},      // N = A|C|G|T

                {'Y', 'Y', true},      // Y = C|T
                {'Y', 'K', true},      // K = G|T
                {'Y', 'V', true},      // V = A|C|G
                {'Y', 'H', true},      // H = A|C|T
                {'Y', 'D', true},      // D = A|G|T
                {'Y', 'B', true},      // B = C|G|T
                {'Y', 'N', true},      // N = A|C|G|T

                {'K', 'K', true},      // K = G|T
                {'K', 'V', true},      // V = A|C|G
                {'K', 'H', true},      // H = A|C|T
                {'K', 'D', true},      // D = A|G|T
                {'K', 'B', true},      // B = C|G|T
                {'K', 'N', true},      // N = A|C|G|T

                {'V', 'V', true},      // V = A|C|G
                {'V', 'H', true},      // H = A|C|T
                {'V', 'D', true},      // D = A|G|T
                {'V', 'B', true},      // B = C|G|T
                {'V', 'N', true},      // N = A|C|G|T

                {'H', 'H', true},      // H = A|C|T
                {'H', 'D', true},      // D = A|G|T
                {'H', 'B', true},      // B = C|G|T
                {'H', 'N', true},      // N = A|C|G|T

                {'D', 'D', true},      // D = A|G|T
                {'D', 'B', true},      // B = C|G|T
                {'D', 'N', true},      // N = A|C|G|T

                {'B', 'B', true},      // B = C|G|T
                {'B', 'N', true},      // N = A|C|G|T

                {'N', 'n', true}
        };
    }

    private char toUpperCase(final char base) {
        return base > 90 ? (char) (base - 32) : base;
    }

    private char toLowerCase(final char base) {
        return (char) (toUpperCase(base) + 32);
    }
}
