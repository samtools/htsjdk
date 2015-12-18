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

import htsjdk.samtools.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author alecw@broadinstitute.org
 */
public class SamLocusIteratorTest {

    /** Coverage for tests with the same reads */
    final static int coverage = 2;

    /** the read length for the testss */
    final static int readLength = 36;

    final static SAMFileHeader header = new SAMFileHeader();

    static {
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        SAMSequenceDictionary dict = new SAMSequenceDictionary();
        dict.addSequence(new SAMSequenceRecord("chrM", 100000));
        header.setSequenceDictionary(dict);
    }

    /** Get the record builder for the tests with the default parameters that are needed */
    private static SAMRecordSetBuilder getRecordBuilder() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        builder.setHeader(header);
        builder.setReadLength(readLength);
        return builder;
    }

    /** Create the SamLocusIterator with the builder*/
    private SamLocusIterator createSamLocusIterator(final SAMRecordSetBuilder builder) {
        final SamLocusIterator ret = new SamLocusIterator(builder.getSamReader());
        ret.setEmitUncoveredLoci(false);
        return ret;
    }

    @Test
    public void testBasicIterator() {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for(int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record"+i, 0, startPosition, true, false, "36M", null, 10);
        }
        // test both for include indels and do not include indels
        for (final boolean incIndels : new boolean[]{false, true}){
            final SamLocusIterator sli = createSamLocusIterator(builder);
            sli.setIncludeIndels(incIndels);
            // make sure we accumulated depth for each position
            int pos = startPosition;
            for (final SamLocusIterator.LocusInfo li : sli) {
                Assert.assertEquals(li.getPosition(), pos++);
                Assert.assertEquals(li.getRecordAndPositions().size(), coverage);
                // make sure that we are not accumulating indels
                Assert.assertEquals(li.getDeletedInRecord().size(), 0);
                Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            }
        }
    }

    @Test
    public void testEmitUncoveredLoci() {

        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for(int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record"+i, 0, startPosition, true, false, "36M", null, 10);
        }

        final int coveredEnd = CoordMath.getEnd(startPosition, readLength);

        // test both for include indels and do not include indels
        for (final boolean incIndels : new boolean[]{false, true}) {
            final SamLocusIterator sli = createSamLocusIterator(builder);
            sli.setIncludeIndels(incIndels);
            // make sure we accumulated depth of 2 for each position
            int pos = 1;
            for (final SamLocusIterator.LocusInfo li : sli) {
                Assert.assertEquals(li.getPosition(), pos++);
                final int expectedReads;
                if (li.getPosition() >= startPosition && li.getPosition() <= coveredEnd) {
                    expectedReads = coverage;
                } else {
                    expectedReads = 0;
                }
                Assert.assertEquals(li.getRecordAndPositions().size(), expectedReads);
                // make sure that we are not accumulating indels
                Assert.assertEquals(li.getDeletedInRecord().size(), 0);
                Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            }
            Assert.assertEquals(pos, header.getSequence(0).getSequenceLength() + 1);
        }
    }

    @Test
    public void testQualityFilter() {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for(int i = 0; i < coverage; i++) {
            final String qualityString;
            // half of the reads have a different quality
            if(i % 2 == 0) {
                qualityString = null;
            } else {
                qualityString = "+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*+*";
            }
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record"+i, 0, startPosition, true, false, "36M", qualityString, 10);
        }

        // test both for include indels and do not include indels
        for (final boolean incIndels : new boolean[]{false, true}) {
            final SamLocusIterator sli = createSamLocusIterator(builder);
            sli.setQualityScoreCutoff(10);
            sli.setIncludeIndels(incIndels);
            // make sure we accumulated depth coverage for even positions, coverage/2 for odd positions
            int pos = startPosition;
            for (final SamLocusIterator.LocusInfo li : sli) {
                Assert.assertEquals(li.getRecordAndPositions().size(), (pos % 2 == 0) ? coverage / 2 : coverage);
                Assert.assertEquals(li.getPosition(), pos++);
                // make sure that we are not accumulating indels
                Assert.assertEquals(li.getDeletedInRecord().size(), 0);
                Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            }
        }
    }

    /**
     * Try all CIGAR operands (except H and P) and confirm that loci produced by SamLocusIterator are as expected.
     */
    @Test
    public void testSimpleGappedAlignment() {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for(int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record"+i, 0, startPosition, true, false, "3S3M3N3M3D3M3I18M3S", null, 10);
        }

        final SamLocusIterator sli = createSamLocusIterator(builder);

        // make sure we accumulated depth for each position
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
            Assert.assertEquals(li.getRecordAndPositions().size(), coverage);
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
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for(int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record"+i, 0, startPosition, true, false, "3S3M3N3M3D3M3I18M3S", null, 10);
        }

        final SamLocusIterator sli = createSamLocusIterator(builder);
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
                Assert.assertEquals(li.getInsertedInRecord().size(), coverage);
                // check the record offset
                Assert.assertEquals(li.getInsertedInRecord().get(0).getOffset(), expectedInsertionOffset);
                Assert.assertEquals(li.getInsertedInRecord().get(1).getOffset(), expectedInsertionOffset);
            } else {
                Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            }
            // check the range of deletions
            if(expectedPositions[i] >= firstDelBase && expectedPositions[i] <= lastDelBase) {
                // check the coverage for insertion and normal records
                Assert.assertEquals(li.getDeletedInRecord().size(), coverage);
                Assert.assertEquals(li.getRecordAndPositions().size(), 0);
                // check the offset for the deletion
                Assert.assertEquals(li.getDeletedInRecord().get(0).getOffset(), expectedReadOffsets[i]);
                Assert.assertEquals(li.getDeletedInRecord().get(1).getOffset(), expectedReadOffsets[i]);
            } else {
                // if it is not a deletion, perform the same test as before
                Assert.assertEquals(li.getRecordAndPositions().size(), coverage);
                Assert.assertEquals(li.getDeletedInRecord().size(), 0);
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
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        // Were it not for the gap, these two reads would not overlap
        builder.addFrag("record1", 0, startPosition, true, false, "18M10D18M", null, 10);
        builder.addFrag("record2", 0, startPosition, true, false, "36M", null, 10);

        final SamLocusIterator sli = createSamLocusIterator(builder);

        // 5 base overlap btw the two reads
        final int numBasesCovered = 36 + 36 - 5;
        final int[] expectedReferencePositions = new int[numBasesCovered];
        final int[] expectedDepths = new int[numBasesCovered];
        final int[][] expectedReadOffsets = new int[numBasesCovered][];

        int i;
        // First 18 bases are from the first read
        for (i = 0; i < 18; ++i) {
            expectedReferencePositions[i] = startPosition + i;
            expectedDepths[i] = 1;
            expectedReadOffsets[i] = new int[]{i};
        }
        // Gap of 10, then 13 bases from the first read
        for (; i < 36 - 5; ++i) {
            expectedReferencePositions[i] = startPosition + 10 + i;
            expectedDepths[i] = 1;
            expectedReadOffsets[i] = new int[]{i};
        }
        // Last 5 bases of first read overlap first 5 bases of second read
        for (; i < 36; ++i) {
            expectedReferencePositions[i] = startPosition + 10 + i;
            expectedDepths[i] = 2;
            expectedReadOffsets[i] = new int[]{i, i - 31};

        }
        // Last 31 bases of 2nd read
        for (; i < 36 + 36 - 5; ++i) {
            expectedReferencePositions[i] = startPosition + 10 + i;
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
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        // Were it not for the gap, these two reads would not overlap
        builder.addFrag("record1", 0, startPosition, true, false, "18M10D18M", null, 10);
        builder.addFrag("record2", 0, startPosition, true, false, "36M", null, 10);

        final SamLocusIterator sli = createSamLocusIterator(builder);
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
            expectedReferencePositions[i] = startPosition + i;
            expectedDepths[i] = 1;
            expectedDelDepths[i] = 0;
            expectedReadOffsets[i] = new int[]{i};
        }
        // Gap of 10
        for(; i < 18 + 10; ++i) {
            expectedReferencePositions[i] = startPosition + i;
            expectedDepths[i] = 0;
            expectedDelDepths[i] = 1;
            expectedReadOffsets[i] = new int[0];
        }
        // the next bases for the first read (without the 5 overlapping)
        for(; i < 46 - 5; ++i) {
            expectedReferencePositions[i] = startPosition + i;
            expectedDepths[i] = 1;
            expectedDelDepths[i] = 0;
            expectedReadOffsets[i] = new int[]{i - 10};
        }
        // last 5 bases of the first read overlap first 5 bases of second read
        for(; i < 46; ++i) {
            expectedReferencePositions[i] = startPosition + i;
            expectedDepths[i] = 2;
            expectedDelDepths[i] = 0;
            expectedReadOffsets[i] = new int[]{i - 10, i + 10 - 46 - 5 };
        }
        // Last 31 bases of 2nd read
        for(; i < numBasesCovered; ++i) {
            expectedReferencePositions[i] = startPosition + i;
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
                Assert.assertEquals(li.getRecordAndPositions().get(j).getOffset(), expectedReadOffsets[i][j]);
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
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for(int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record"+i, 0, startPosition, true, false, "3I33M", null, 10);
        }

        final SamLocusIterator sli = createSamLocusIterator(builder);

        // make sure we accumulated depth for each position
        int pos = startPosition;
        for (final SamLocusIterator.LocusInfo li : sli) {
            Assert.assertEquals(li.getPosition(), pos++);
            Assert.assertEquals(li.getRecordAndPositions().size(), coverage);
            // make sure that we are not accumulating indels
            Assert.assertEquals(li.getDeletedInRecord().size(), 0);
            Assert.assertEquals(li.getInsertedInRecord().size(), 0);
        }
    }

    @Test
    public void testStartWithInsertionWithIndels() {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for(int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record"+i, 0, startPosition, true, false, "3I33M", null, 10);
        }

        final SamLocusIterator sli = createSamLocusIterator(builder);
        sli.setIncludeIndels(true);

        // make sure that it starts in the previous position
        int pos = startPosition - 1;
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
                Assert.assertEquals(li.getInsertedInRecord().size(), coverage);
                // and the offset is the first in the read
                Assert.assertEquals(li.getInsertedInRecord().get(0).getOffset(), 0);
                Assert.assertEquals(li.getInsertedInRecord().get(1).getOffset(), 0);
                indelPos = false;
            } else {
                Assert.assertEquals(2, li.getRecordAndPositions().size());
                Assert.assertEquals(li.getRecordAndPositions().get(0).getOffset(), pos - startPosition + 3);
                // make sure that we are not accumulating indels
                Assert.assertEquals(li.getDeletedInRecord().size(), 0);
                Assert.assertEquals(li.getInsertedInRecord().size(), 0);
            }
            pos++;
        }
    }
}
