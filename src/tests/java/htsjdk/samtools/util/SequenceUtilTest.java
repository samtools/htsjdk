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

    @DataProvider
    public Object[][] compatibleNonEqualLists(){
        final String s = HEADER +
                String.format("@SQ\tSN:phix174.seq\tLN:%d\tUR:%s\tAS:PhiX174\tM5:%s\n", 5386, "/seq/references/PhiX174/v0/PhiX174.fasta", "3332ed720ac7eaa9b3655c06f6b9e196")+
                String.format("@SQ\tSN:phix175.seq\tLN:%d\tUR:%s\tAS:HiMom\tM5:%s\n", 5385, "/seq/references/PhiX174/v0/HiMom.fasta", "deadbeed");

        return new Object[][]{ {makeSequenceDictionary(5386, "/seq/references/PhiX174/v0/PhiX174.fasta",
                "3332ed720ac7eaa9b3655c06f6b9e196"),
                new SAMTextHeaderCodec().decode(new StringLineReader(s), null).getSequenceDictionary()}};
    }

    @Test(dataProvider = "compatibleNonEqualLists")
    public void testCompatible(SAMSequenceDictionary sd1, SAMSequenceDictionary sd2) {
         SequenceUtil.assertSequenceDictionariesEqual(sd1, sd2, true);
    }

    @Test(dataProvider = "compatibleNonEqualLists",expectedExceptions = SequenceUtil.SequenceListsDifferException.class)
    public void testinCompatible(SAMSequenceDictionary sd1, SAMSequenceDictionary sd2) {
        SequenceUtil.assertSequenceDictionariesEqual(sd1, sd2, false);
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
    public void testCountMismatches(final String readString, final String cigar, final String reference,
                                    final int expectedMismatchesExact, final int expectedMismatchesAmbiguous) {
        final SAMRecord rec = new SAMRecord(null);
        rec.setReadName("test");
        rec.setReadString(readString);
        rec.setCigarString(cigar);

        final byte[] refBases = StringUtil.stringToBytes(reference);

        final int nExact = SequenceUtil.countMismatches(rec, refBases, -1, false, false);
        Assert.assertEquals(nExact, expectedMismatchesExact);

        final int nAmbiguous = SequenceUtil.countMismatches(rec, refBases, -1, false, true);
        Assert.assertEquals(nAmbiguous, expectedMismatchesAmbiguous);
    }

    @DataProvider(name="mismatchCountsDataProvider")
    public Object[][] testMakeMismatchCountsDataProvider() {
        // note: R=A|G
        return new Object[][] {
                {"A", "1M", "A", 0, 0},
                {"A", "1M", "R", 1, 0},
                {"G", "1M", "R", 1, 0},
                {"C", "1M", "R", 1, 1},
                {"T", "1M", "R", 1, 1},
                {"N", "1M", "R", 1, 1},
                {"R", "1M", "A", 1, 1},
                {"R", "1M", "C", 1, 1},
                {"R", "1M", "G", 1, 1},
                {"R", "1M", "T", 1, 1},
                {"R", "1M", "N", 1, 0},
                {"R", "1M", "R", 0, 0},
                {"N", "1M", "N", 0, 0}
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

    @DataProvider(name = "testBisulfiteConversionDataProvider")
    public Object[][] testBisulfiteConversionDataProvider() {
        // C ref -> T read on the positive strand, and G ref -> A read on the negative strand
        return new Object[][] {
                {'C', 'T', false, false},
                {'C', 'A', false, false},
                {'C', 'C', false, false},
                {'T', 'C', true, false},
                {'G', 'T', false, false},
                {'G', 'A', false, false},
                {'G', 'G', false, false},
                {'A', 'G', false, true}
        };
    }

    @Test(dataProvider = "testBisulfiteConversionDataProvider")
    public void testBisulfiteConversion(final char readBase, final char refBase, final boolean posStrandExpected, final boolean negStrandExpected) {
        final boolean posStrand = SequenceUtil.isBisulfiteConverted((byte) readBase, (byte) refBase, false);
        Assert.assertEquals(posStrand, posStrandExpected);
        final boolean negStrand = SequenceUtil.isBisulfiteConverted((byte) readBase, (byte) refBase, true);
        Assert.assertEquals(negStrand, negStrandExpected);
    }

    @Test(dataProvider = "basesEqualDataProvider")
    public void testBasesEqual(final char base1, final char base2,
                               final boolean expectedB1EqualsB2,
                               final boolean expectedB1ReadMatchesB2Ref,
                               final boolean expectedB2ReadMatchesB1Ref) {

        final char[] base1UcLc = new char[] { toUpperCase(base1), toLowerCase(base1) };
        final char[] base2UcLc = new char[] { toUpperCase(base2), toLowerCase(base2) };
        // Test over all permutations - uc vs uc, uc vs lc, lc vs uc, lc vs lc
        for (char theBase1 : base1UcLc) {
            for (char theBase2 : base2UcLc) {
                // for equality, order should not matter
                final boolean b1EqualsB2 = SequenceUtil.basesEqual((byte) theBase1, (byte) theBase2);
                Assert.assertEquals(b1EqualsB2, expectedB1EqualsB2, "basesEqual test failed for '" + theBase1 + "' vs. '" + theBase2 + "'");
                final boolean b2EqualsB1 = SequenceUtil.basesEqual((byte) theBase2, (byte) theBase1);
                Assert.assertEquals(b2EqualsB1, expectedB1EqualsB2, "basesEqual test failed for '" + theBase1 + "' vs. '" + theBase2 + "'");

                // for ambiguous read/ref matching, the order does matter
                final boolean b1ReadMatchesB2Ref = SequenceUtil.readBaseMatchesRefBaseWithAmbiguity((byte) theBase1, (byte) theBase2);
                Assert.assertEquals(b1ReadMatchesB2Ref, expectedB1ReadMatchesB2Ref, "readBaseMatchesRefBaseWithAmbiguity test failed for '" + theBase1 + "' vs. '" + theBase2 + "'");
                final boolean b2ReadMatchesB1Ref = SequenceUtil.readBaseMatchesRefBaseWithAmbiguity((byte) theBase2, (byte) theBase1);
                Assert.assertEquals(b2ReadMatchesB1Ref, expectedB2ReadMatchesB1Ref, "readBaseMatchesRefBaseWithAmbiguity test failed for '" + theBase1 + "' vs. '" + theBase2 + "'");
            }
        }
    }

    /*
     * For reference:
     * M = A|C
     * R = A|G
     * W = A|T
     * S = C|G
     * Y = C|T
     * K = G|T
     * V = A|C|G
     * H = A|C|T
     * D = A|G|T
     * B = C|G|T
     * N = A|C|G|T
     */
    @DataProvider(name="basesEqualDataProvider")
    public Object[][] testBasesEqualDataProvider() {
        return new Object[][] {
                {'A', 'A', true, true, true},
                {'A', 'C', false, false, false},
                {'A', 'G', false, false, false},
                {'A', 'T', false, false, false},
                {'A', 'M', false, true, false},
                {'A', 'R', false, true, false},
                {'A', 'W', false, true, false},
                {'A', 'S', false, false, false},
                {'A', 'Y', false, false, false},
                {'A', 'K', false, false, false},
                {'A', 'V', false, true, false},
                {'A', 'H', false, true, false},
                {'A', 'D', false, true, false},
                {'A', 'B', false, false, false},
                {'A', 'N', false, true, false},
                {'C', 'C', true, true, true},
                {'C', 'G', false, false, false},
                {'C', 'T', false, false, false},
                {'C', 'M', false, true, false},
                {'C', 'R', false, false, false},
                {'C', 'W', false, false, false},
                {'C', 'S', false, true, false},
                {'C', 'Y', false, true, false},
                {'C', 'K', false, false, false},
                {'C', 'V', false, true, false},
                {'C', 'H', false, true, false},
                {'C', 'D', false, false, false},
                {'C', 'N', false, true, false},
                {'G', 'G', true, true, true},
                {'G', 'T', false, false, false},
                {'G', 'M', false, false, false},
                {'G', 'R', false, true, false},
                {'G', 'W', false, false, false},
                {'G', 'S', false, true, false},
                {'G', 'Y', false, false, false},
                {'G', 'K', false, true, false},
                {'G', 'V', false, true, false},
                {'G', 'H', false, false, false},
                {'G', 'N', false, true, false},
                {'T', 'T', true, true, true},
                {'T', 'W', false, true, false},
                {'T', 'Y', false, true, false},
                {'T', 'V', false, false, false},
                {'M', 'T', false, false, false},
                {'M', 'M', true, true, true},
                {'M', 'R', false, false, false},
                {'M', 'W', false, false, false},
                {'M', 'S', false, false, false},
                {'M', 'Y', false, false, false},
                {'M', 'V', false, true, false},
                {'M', 'N', false, true, false},
                {'R', 'T', false, false, false},
                {'R', 'R', true, true, true},
                {'R', 'W', false, false, false},
                {'R', 'S', false, false, false},
                {'R', 'Y', false, false, false},
                {'R', 'V', false, true, false},
                {'W', 'W', true, true, true},
                {'W', 'Y', false, false, false},
                {'S', 'T', false, false, false},
                {'S', 'W', false, false, false},
                {'S', 'S', true, true, true},
                {'S', 'Y', false, false, false},
                {'S', 'V', false, true, false},
                {'Y', 'Y', true, true, true},
                {'K', 'T', false, false, true},
                {'K', 'M', false, false, false},
                {'K', 'R', false, false, false},
                {'K', 'W', false, false, false},
                {'K', 'S', false, false, false},
                {'K', 'Y', false, false, false},
                {'K', 'K', true, true, true},
                {'K', 'V', false, false, false},
                {'K', 'N', false, true, false},
                {'V', 'W', false, false, false},
                {'V', 'Y', false, false, false},
                {'V', 'V', true, true, true},
                {'H', 'T', false, false, true},
                {'H', 'M', false, false, true},
                {'H', 'R', false, false, false},
                {'H', 'W', false, false, true},
                {'H', 'S', false, false, false},
                {'H', 'Y', false, false, true},
                {'H', 'K', false, false, false},
                {'H', 'V', false, false, false},
                {'H', 'H', true, true, true},
                {'H', 'N', false, true, false},
                {'D', 'G', false, false, true},
                {'D', 'T', false, false, true},
                {'D', 'M', false, false, false},
                {'D', 'R', false, false, true},
                {'D', 'W', false, false, true},
                {'D', 'S', false, false, false},
                {'D', 'Y', false, false, false},
                {'D', 'K', false, false, true},
                {'D', 'V', false, false, false},
                {'D', 'H', false, false, false},
                {'D', 'D', true, true, true},
                {'D', 'N', false, true, false},
                {'B', 'C', false, false, true},
                {'B', 'G', false, false, true},
                {'B', 'T', false, false, true},
                {'B', 'M', false, false, false},
                {'B', 'R', false, false, false},
                {'B', 'W', false, false, false},
                {'B', 'S', false, false, true},
                {'B', 'Y', false, false, true},
                {'B', 'K', false, false, true},
                {'B', 'V', false, false, false},
                {'B', 'H', false, false, false},
                {'B', 'D', false, false, false},
                {'B', 'B', true, true, true},
                {'B', 'N', false, true, false},
                {'N', 'T', false, false, true},
                {'N', 'R', false, false, true},
                {'N', 'W', false, false, true},
                {'N', 'S', false, false, true},
                {'N', 'Y', false, false, true},
                {'N', 'V', false, false, true},
                {'N', 'N', true, true, true}
        };
    }

    private char toUpperCase(final char base) {
        return base > 90 ? (char) (base - 32) : base;
    }

    private char toLowerCase(final char base) {
        return (char) (toUpperCase(base) + 32);
    }

    @Test(dataProvider = "testGetSamReadNameFromFastqHeader")
    public void testGetSamReadNameFromFastqHeader(final String fastqHeader,
                                                  final String expectedSamReadName) {
        Assert.assertEquals(SequenceUtil.getSamReadNameFromFastqHeader(fastqHeader), expectedSamReadName);
    }

    @DataProvider(name = "testGetSamReadNameFromFastqHeader")
    public Object[][] testGetSamReadNameFromFastqHeaderTestCases() {
        return new Object[][] {
                {"Simple:Name",          "Simple:Name"},
                {"Simple:Name",          "Simple:Name"},
                {"Name/1",               "Name"},
                {"Name/2",               "Name"},
                {"Name/3",               "Name/3"},
                {"Simple:Name Blank",    "Simple:Name"},
                {"Simple:Name Blank /1", "Simple:Name"},
                {"Name/1/2",             "Name"}
        };
    }
}
