/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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

import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;

/**
 * @author alecw@broadinstitute.org
 */
public class SamLocusIteratorTest {
    private SamReader createSamFileReader(final String samExample) {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(samExample.getBytes());
        return SamReaderFactory.makeDefault().open(SamInputResource.of(inputStream));
    }

    private SamLocusIterator createSamLocusIterator(final SamReader samReader) {
        final SamLocusIterator ret = new SamLocusIterator(samReader);
        ret.setEmitUncoveredLoci(false);
        return ret;
    }

    @Test
    public void testBasicIterator() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);


        // make sure we accumulated depth of 2 for each position
        int pos = 165;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(pos++, li.getPosition());
            Assert.assertEquals(2, li.getRecordAndPositions().size());
            // make sure that we are not accumulating indels
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }

    }

    @Test
    public void testBasicIteratorWithIndels() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);
        sli.setIncludeIndels(true);

        // make sure we accumulated depth of 2 for each position
        int pos = 165;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(pos++, li.getPosition());
            Assert.assertEquals(2, li.getRecordAndPositions().size());
            // make sure that there are no indels if accumulating
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }

    }

    @Test
    public void testEmitUncoveredLoci() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = new SamLocusIterator(samReader);

        // make sure we accumulated depth of 2 for each position
        int pos = 1;
        final int coveredStart = 165;
        final int coveredEnd = CoordMath.getEnd(coveredStart, seq1.length());
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(li.getPosition(), pos++);
            final int expectedReads;
            if (li.getPosition() >= coveredStart && li.getPosition() <= coveredEnd) {
                expectedReads = 2;
            } else {
                expectedReads = 0;
            }
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedReads);
            // make sure that we are not accumulating indels
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }
        Assert.assertEquals(pos, 100001);

    }

    @Test
    public void testEmitUncoveredLociWithIndels() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = new SamLocusIterator(samReader);
        sli.setIncludeIndels(true);

        // make sure we accumulated depth of 2 for each position
        int pos = 1;
        final int coveredStart = 165;
        final int coveredEnd = CoordMath.getEnd(coveredStart, seq1.length());
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(li.getPosition(), pos++);
            final int expectedReads;
            if (li.getPosition() >= coveredStart && li.getPosition() <= coveredEnd) {
                expectedReads = 2;
            } else {
                expectedReads = 0;
            }
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedReads);
            // make sure that there are no indels if accumulating
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }
        Assert.assertEquals(pos, 100001);

    }

    @Test
    public void testQualityFilter() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String qual2 = "+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*"; // phred 10,9...
        final String s1 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String s2 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual2 + "\n";
        final String exampleSam = sqHeader + s1 + s2;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);
        sli.setQualityScoreCutoff(10);


        // make sure we accumulated depth 2 for even positions, 1 for odd positions
        int pos = 165;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals((pos % 2 == 0) ? 1 : 2, li.getRecordAndPositions().size());
            Assert.assertEquals(pos++, li.getPosition());
            // make sure that we are not accumulating indels
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }

    }

    @Test
    public void testQualityFilterWithIndels() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String qual2 = "+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*"; // phred 10,9...
        final String s1 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String s2 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual2 + "\n";
        final String exampleSam = sqHeader + s1 + s2;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);
        sli.setQualityScoreCutoff(10);
        sli.setIncludeIndels(true);

        // make sure we accumulated depth 2 for even positions, 1 for odd positions
        int pos = 165;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals((pos % 2 == 0) ? 1 : 2, li.getRecordAndPositions().size());
            Assert.assertEquals(pos++, li.getPosition());
            // make sure that there are no indels if accumulating
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }

    }

    /**
     * Try all CIGAR operands (except H and P) and confirm that loci produced by SamLocusIterator are as expected.
     */
    @Test
    public void testSimpleGappedAlignment() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t3S3M3N3M3D3M3I18M3S\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);


        // make sure we accumulated depth of 2 for each position
        final int[] expectedReferencePositions = new int[]{
                // 3S
                165, 166, 167, // 3M
                // 3N
                171, 172, 173, // 3M
                // 3D
                177, 178, 179, // 3M
                // 3I
                180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197}; // 18M

        final int[] expectedReadOffsets = new int[]{
                // 3S
                3, 4, 5, // 3M
                // 3N
                6, 7, 8, // 3M
                // 3D
                9, 10, 11, // 3M
                // 3I
                15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32 // 3M
        };
        int i = 0;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(li.getRecordAndPositions().size(), 2);
            Assert.assertEquals(li.getPosition(), expectedReferencePositions[i]);
            Assert.assertEquals(li.getRecordAndPositions().get(0).getOffset(), expectedReadOffsets[i]);
            Assert.assertEquals(li.getRecordAndPositions().get(1).getOffset(), expectedReadOffsets[i]);
            // make sure that we are not accumulating indels
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            ++i;
        }
    }

    /**
     * Try all CIGAR operands (except H and P) and confirm that loci produced by SamLocusIterator are as expected with indels
     */
    @Test
    public void testSimpleGappedAlignmentWithIndels() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t3S3M3N3M3D3M3I18M3S\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);
        sli.setIncludeIndels(true);

        // make sure we accumulated depth of 2 for each position
        final int[] expectedPositions = new int[]{
                // 3S
                165, 166, 167, // 3M
                // 3N
                171, 172, 173, // 3M
                174, 175, 176, // 3D
                177, 178, 179, // 3M
                // 3I
                180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197}; // 18M

        final int[] expectedReadOffsets = new int[]{
                // 3S
                3, 4, 5, // 3M
                // 3N
                6, 7, 8, // 3M
                8, 8, 8, // 3D previous 0-based offset
                9, 10, 11, // 3M
                // 3I
                15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32 // 3M
        };

        // to check the range of the insertion
        final int firstDelBase = 174;
        final int lastDelBase = 176;

        final int expectedInsertionPosition = 179; // previous reference base
        final int expectedInsertionOffset = 12; // first read base in the insertion

        int i = 0;
        for (final SamLocusIterator.LocusInfo li : sli) {
            // check if the LocusInfo is the expected
            Assert.assertEquals(li.getPosition(), expectedPositions[i]);
            // check the insertions
            if(li.getPosition() == expectedInsertionPosition) {
                // check the accumulated coverage
                Assert.assertEquals(2, li.getInsertedInRecord().size());
                // check the record offset
                Assert.assertEquals(expectedInsertionOffset, li.getInsertedInRecord().get(0).getOffset());
                Assert.assertEquals(expectedInsertionOffset, li.getInsertedInRecord().get(1).getOffset());
            } else {
                Assert.assertEquals(0, li.getInsertedInRecord().size());
            }
            // check the range of deletions
            if(expectedPositions[i] >= firstDelBase && expectedPositions[i] <= lastDelBase) {
                // check the coverage for insertion and normal records
                Assert.assertEquals(li.getDeletedInRecord().size(), 2);
                Assert.assertEquals(li.getRecordAndPositions().size(), 0);
                // check the offset for the deletion
                Assert.assertEquals(li.getDeletedInRecord().get(0).getOffset(), expectedReadOffsets[i]);
                Assert.assertEquals(li.getDeletedInRecord().get(1).getOffset(), expectedReadOffsets[i]);
            } else {
                // if it is not a deletion, perform the same test as before
                Assert.assertEquals(li.getRecordAndPositions().size(), 2);
                Assert.assertEquals(li.getRecordAndPositions().get(0).getOffset(), expectedReadOffsets[i]);
                Assert.assertEquals(li.getRecordAndPositions().get(1).getOffset(), expectedReadOffsets[i]);
            }
            ++i;
        }
    }

    /**
     * Test two reads that overlap because one has a deletion in the middle of it.
     */
    @Test
    public void testOverlappingGappedAlignments() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        // Were it not for the gap, these two reads would not overlap
        final String s1 = "3851612\t16\tchrM\t165\t255\t18M10D18M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String s2 = "3851613\t16\tchrM\t206\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s2;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);
        // 5 base overlap btw the two reads
        final int numBasesCovered = 36 + 36 - 5;
        final int[] expectedReferencePositions = new int[numBasesCovered];
        final int[] expectedDepths = new int[numBasesCovered];
        final int[][] expectedReadOffsets = new int[numBasesCovered][];

        int i;
        // First 18 bases are from the first read
        for (i = 0; i < 18; ++i) {
            expectedReferencePositions[i] = 165 + i;
            expectedDepths[i] = 1;
            expectedReadOffsets[i] = new int[]{i};
        }
        // Gap of 10, then 13 bases from the first read
        for (; i < 36 - 5; ++i) {
            expectedReferencePositions[i] = 165 + 10 + i;
            expectedDepths[i] = 1;
            expectedReadOffsets[i] = new int[]{i};
        }
        // Last 5 bases of first read overlap first 5 bases of second read
        for (; i < 36; ++i) {
            expectedReferencePositions[i] = 165 + 10 + i;
            expectedDepths[i] = 2;
            expectedReadOffsets[i] = new int[]{i, i - 31};

        }
        // Last 31 bases of 2nd read
        for (; i < 36 + 36 - 5; ++i) {
            expectedReferencePositions[i] = 165 + 10 + i;
            expectedDepths[i] = 1;
            expectedReadOffsets[i] = new int[]{i - 31};
        }

        i = 0;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedDepths[i]);
            Assert.assertEquals(li.getPosition(), expectedReferencePositions[i]);
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedReadOffsets[i].length);
            for (int j = 0; j < expectedReadOffsets[i].length; ++j) {
                Assert.assertEquals(li.getRecordAndPositions().get(j).getOffset(), expectedReadOffsets[i][j]);
            }
            // make sure that we are not accumulating indels
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            ++i;
        }
    }

    /**
     * Test two reads that overlap because one has a deletion in the middle of it.
     */
    @Test
    public void testOverlappingGappedAlignmentsWithIndels() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        // Were it not for the gap, these two reads would not overlap
        final String s1 = "3851612\t16\tchrM\t165\t255\t18M10D18M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String s2 = "3851613\t16\tchrM\t206\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s2;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);
        sli.setIncludeIndels(true);
        // 46 for the gapped alignment, and 5 base overlap btw the two reads
        final int numBasesCovered = 46 + 36 - 5;
        final int[] expectedReferencePositions = new int[numBasesCovered];
        final int[] expectedDepths = new int[numBasesCovered];
        final int[] expectedDelDepths = new int[numBasesCovered];
        final int[][] expectedReadOffsets = new int[numBasesCovered][];
        final int expectedDelOffset = 17; // previous 0-based offset

        int i;
        // First 18 bases are from the first read
        for (i = 0; i < 18; ++i) {
            expectedReferencePositions[i] = 165 + i;
            expectedDepths[i] = 1;
            expectedDelDepths[i] = 0;
            expectedReadOffsets[i] = new int[]{i};
        }
        // Gap of 10
        for(; i < 18 + 10; ++i) {
            expectedReferencePositions[i] = 165 + i;
            expectedDepths[i] = 0;
            expectedDelDepths[i] = 1;
            expectedReadOffsets[i] = new int[0];
        }
        // the next bases for the first read (without the 5 overlapping)
        for(; i < 46 - 5; ++i) {
            expectedReferencePositions[i] = 165 + i;
            expectedDepths[i] = 1;
            expectedDelDepths[i] = 0;
            expectedReadOffsets[i] = new int[]{i - 10};
        }
        // last 5 bases of the first read overlap first 5 bases of second read
        for(; i < 46; ++i) {
            expectedReferencePositions[i] = 165 + i;
            expectedDepths[i] = 2;
            expectedDelDepths[i] = 0;
            expectedReadOffsets[i] = new int[]{i - 10, i + 10 - 46 - 5 };
        }
        // Last 31 bases of 2nd read
        for(; i < numBasesCovered; ++i) {
            expectedReferencePositions[i] = 165 + i;
            expectedDepths[i] = 1;
            expectedDelDepths[i] = 0;
            expectedReadOffsets[i] = new int[]{i + 10 - 46 - 5};
        }
        i = 0;
        for (final SamLocusIterator.LocusInfo li : sli) {
            // checking the same as without indels
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedDepths[i]);
            Assert.assertEquals(li.getPosition(), expectedReferencePositions[i]);
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedReadOffsets[i].length);
            for (int j = 0; j < expectedReadOffsets[i].length; ++j) {
                Assert.assertEquals(li.getRecordAndPositions().get(j).getOffset(), expectedReadOffsets[i][j], li.toString());
            }
            // check the deletions
            Assert.assertEquals(li.getDeletedInRecord().size(), expectedDelDepths[i]);
            if(expectedDelDepths[i] != 0) {
                Assert.assertEquals(li.getDeletedInRecord().get(0).getOffset(), expectedDelOffset);
            }
            // checking that insertions are not accumulating
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            ++i;
        }
    }

    /**
     * Test two reads that start with an insertion
     */
    @Test
    public void testStartWithInsertion() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t3I33M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);

        // make sure we accumulated depth of 2 for each position
        int pos = 165;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(pos++, li.getPosition());
            Assert.assertEquals(2, li.getRecordAndPositions().size());
            // make sure that we are not accumulating indels
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }
    }

    @Test
    public void testStartWithInsertionWithIndels() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++"; // phred 10
        final String s1 = "3851612\t16\tchrM\t165\t255\t3I33M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final SamLocusIterator sli = createSamLocusIterator(samReader);
        sli.setIncludeIndels(true);

        // make sure that it starts in the previous position
        int pos = 164;
        boolean indelPos = true;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(li.getPosition(), pos);
            // check the first position
            if(indelPos) {
                // no accumulation for match reads
                Assert.assertEquals(li.getRecordAndPositions().size(), 0);
                // no accumulation of deletions
                Assert.assertEquals(li.getDeletedInRecord().size(), 0);
                // accumulation of 2 for insertion
                Assert.assertEquals(li.getInsertedInRecord().size(), 2);
                // and the offset is the first in the read
                Assert.assertEquals(li.getInsertedInRecord().get(0).getOffset(), 0);
                Assert.assertEquals(li.getInsertedInRecord().get(1).getOffset(), 0);
                indelPos = false;
            } else {
                Assert.assertEquals(2, li.getRecordAndPositions().size());
                Assert.assertEquals(li.getRecordAndPositions().get(0).getOffset(), pos - 165 + 3);
                // make sure that we are not accumulating indels
                Assert.assertEquals(li.getDeletedInRecord().size(), 0);
                Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            }
            pos++;
        }
    }
}
