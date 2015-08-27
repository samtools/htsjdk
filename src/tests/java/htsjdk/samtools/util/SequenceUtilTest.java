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

    @Test(dataProvider = "baseMatchesReferenceTestCases")
    public void testBaseMatchesReference(byte read, byte reference, boolean expectation) {
        Assert.assertEquals(SequenceUtil.baseMatchesReference(read, reference), expectation);
    }

    @DataProvider(name = "baseMatchesReferenceTestCases")
    public Object[][] baseMatchesReferenceTestCases() {
        return new Object[][] {
                {(byte)'a', (byte)'a', true},
                {(byte)'a', (byte)'A', true},
                {(byte)'a', (byte)'c', false},
                {(byte)'N', (byte)'W', true},
                {(byte)'N', (byte)'a', false},
        };
    }

    @Test(dataProvider = "testSamNmTestCases")
    public void testSamNmTag(String readSequence, String referenceSequence, int expectedMismatches) {
        // Just set up a really basic SAM record to only return exactly the sequence in readSequence:
        final SAMRecord rec = new SAMRecord(null);
        rec.setReadName(readSequence);
        rec.setReadString(readSequence);
        rec.setCigarString(readSequence.length() + "M");
        rec.setAlignmentStart(1);

        Assert.assertEquals(SequenceUtil.calculateSamNmTag(rec, referenceSequence.getBytes()), expectedMismatches);

        // Also test the alternative, deprecated version:
        // TODO (Remove when this deprecated method is removed)
        Assert.assertEquals(SequenceUtil.calculateSamNmTag(rec, referenceSequence.toCharArray(), 0), expectedMismatches);
    }

    @DataProvider(name = "testSamNmTestCases")
    public Object[][] testSamNmTestCases() {
        return new Object[][] {
                // Base case:
                {"acTG", "acTG", 0},
                // Mismatch detection - 1 error:
                {"acTC", "acTG", 1},
                // Mismatch detection - 3 errors:
                {"acTC", "aaAA", 3},
                // Case insensitivity:
                {"aCtG", "AcTg", 0},
                // Allow 'N' in read to match 'W' in reference (i.e. 'convert' the reference W into N before matching):
                {"aCNG", "AcWg", 0},
                // Ensure 'N' in read matches 'N' in reference:
                {"aCNG", "AcNg", 0},
                // Don't allow 'N' in read to match A, C, T or G:
                {"NNNN", "ACtg", 4},

        };
    }
}
