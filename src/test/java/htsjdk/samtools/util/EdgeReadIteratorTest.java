/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Tests check that for each alignment block of processed reads, iterator returns a <code>EdgingRecordAndOffset</code>
 * with type <code>BEGIN</code> for the reference position of read start and a <code>EdgingRecordAndOffset</code> with
 * type <code>END</code> for the reference position + 1 of read end.
 */
public class EdgeReadIteratorTest extends AbstractLocusIteratorTestTemplate {

    @Override
    @Test
    public void testBasicIterator() {
        final EdgeReadIterator sli = new EdgeReadIterator(createSamFileReader());
        int pos = 1;
        for (final AbstractLocusInfo<EdgingRecordAndOffset> li : sli) {
            if (pos == 1 || pos == 37) {
                assertEquals(pos++, li.getPosition());
                assertEquals(2, li.getRecordAndOffsets().size());
            } else {
                assertEquals(pos++, li.getPosition());
                assertEquals(0, li.getRecordAndOffsets().size());
            }
        }

    }

    /**
     * Since EdgeReadIterator does not support emitting uncovered loci, this test just check that
     * iterator return correctly aligned objects for start and end of a read.
     */
    @Override
    @Test
    public void testEmitUncoveredLoci() {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for (int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record" + i, 0, startPosition, true, false, "36M", null, 10);
        }
        final int coveredEnd = CoordMath.getEnd(startPosition, readLength) + 1;
        final EdgeReadIterator sli = new EdgeReadIterator(builder.getSamReader());

        int pos = 1;
        final int coveredStart = 165;
        for (final AbstractLocusInfo li : sli) {
            assertEquals(li.getPosition(), pos++);
            final int expectedReads;
            if (li.getPosition() == coveredStart || li.getPosition() == coveredEnd) {
                expectedReads = 2;
            } else {
                expectedReads = 0;
            }
            assertEquals(li.getRecordAndOffsets().size(), expectedReads);
        }
        assertEquals(pos, 100001);
    }

    /**
     * Try all CIGAR operands (except H and P) and confirm that loci produced by SamLocusIterator are as expected.
     */
    @Override
    @Test
    public void testSimpleGappedAlignment() {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 165;
        for (int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record" + i, 0, startPosition, true, false, "3S3M3N3M3D3M3I1N18M3S", null, 10);
        }
        final EdgeReadIterator sli = new EdgeReadIterator(builder.getSamReader());
        while (sli.hasNext()) {
            AbstractLocusInfo<EdgingRecordAndOffset> info = sli.next();
            int pos = info.getPosition();
            if (pos == startPosition || pos == startPosition + 6 || pos == startPosition + 12 || pos == startPosition + 16) {
                assertEquals(EdgingRecordAndOffset.Type.BEGIN, info.getRecordAndOffsets().get(0).getType());
                assertEquals(EdgingRecordAndOffset.Type.BEGIN, info.getRecordAndOffsets().get(1).getType());
            } else if (pos == startPosition + 3 || pos == startPosition + 9 || pos == startPosition + 15 || pos == startPosition + 34) {
                assertEquals(EdgingRecordAndOffset.Type.END, info.getRecordAndOffsets().get(0).getType());
                assertEquals(EdgingRecordAndOffset.Type.END, info.getRecordAndOffsets().get(1).getType());
            }
        }
    }

    /**
     * Test two reads that overlap because one has a deletion in the middle of it.
     */
    @Override
    @Test
    public void testOverlappingGappedAlignmentsWithoutIndels() {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        // add records up to coverage for the test in that position
        final int startPosition = 1;
        // Were it not for the gap, these two reads would not overlap

        builder.addFrag("record1", 0, startPosition, true, false, "18M10D18M", null, 10);
        builder.addFrag("record2", 0, 41, true, false, "36M", null, 10);

        final EdgeReadIterator sli = new EdgeReadIterator(builder.getSamReader());
        // 5 base overlap btw the two reads
        final int numBasesCovered = 81;
        final int[] expectedReferencePositions = new int[numBasesCovered];
        final int[] expectedDepths = new int[numBasesCovered];
        final int[][] expectedReadOffsets = new int[numBasesCovered][];
        List<Integer> start = Arrays.asList(0, 28, 40);
        List<Integer> end = Arrays.asList(19, 47, 77);

        int i;
        // First 18 bases are from the first read
        expectedDepths[0] = 1;
        expectedReferencePositions[0] = 1;
        expectedReadOffsets[0] = new int[]{0};

        for (i = 1; i < 18; ++i) {
            fillEmptyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }
        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 19;
        expectedReadOffsets[i++] = new int[]{0};

        for (; i < 28; ++i) {
            fillEmptyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        // Gap of 10, then 13 bases from the first read
        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 29;
        expectedReadOffsets[i++] = new int[]{18};

        for (; i < 40; ++i) {
            fillEmptyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 41;
        expectedReadOffsets[i++] = new int[]{0};

        for (; i < 46; ++i) {
            fillEmptyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 47;
        expectedReadOffsets[i++] = new int[]{18};

        // Last 5 bases of first read overlap first 5 bases of second read
        for (; i < 76; ++i) {
            fillEmptyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 77;
        expectedReadOffsets[i++] = new int[]{0};

        // Last 31 bases of 2nd read

        for (; i <= 80; ++i) {
            fillEmptyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        i = 0;
        for (final AbstractLocusInfo<EdgingRecordAndOffset> li : sli) {
            assertEquals(li.getRecordAndOffsets().size(), expectedDepths[i]);
            assertEquals(li.getPosition(), expectedReferencePositions[i]);
            assertEquals(li.getRecordAndOffsets().size(), expectedReadOffsets[i].length);
            for (int j = 0; j < expectedReadOffsets[i].length; ++j) {
                assertEquals(li.getRecordAndOffsets().get(j).getOffset(), expectedReadOffsets[i][j]);
                if (start.contains(li.getPosition() - 1)) {
                    assertEquals(li.getRecordAndOffsets().get(j).getType(), EdgingRecordAndOffset.Type.BEGIN);
                }
                if (end.contains(li.getPosition() - 1)) {
                    assertEquals(li.getRecordAndOffsets().get(j).getType(), EdgingRecordAndOffset.Type.END);
                }
            }
            ++i;
            if (i == 80) {
                break;
            }
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetQualityCutOff() {
        final EdgeReadIterator sli = new EdgeReadIterator(createSamFileReader());

        sli.setQualityScoreCutoff(10);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetMaxReadsToAccumulatePerLocus() {
        final EdgeReadIterator sli = new EdgeReadIterator(createSamFileReader());

        sli.setMaxReadsToAccumulatePerLocus(100);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetEmitUncoveredLoci() {
        final EdgeReadIterator sli = new EdgeReadIterator(createSamFileReader());

        sli.setEmitUncoveredLoci(false);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetIncludeIndels() {
        final EdgeReadIterator sli = new EdgeReadIterator(createSamFileReader());

        sli.setIncludeIndels(true);
    }

    /**
     * Tests that reads, that don't intersect given interval list, are excluded from iterator
     */
    @Test
    public void testNotIntersectingInterval() {
        SamReader samReader = createSamFileReader(createSamFileHeader("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n"));

        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t50\t60\t+\ttest");
        EdgeReadIterator iterator = new EdgeReadIterator(samReader, intervals);
        int locusPosition = 50;
        while (iterator.hasNext()) {
            AbstractLocusInfo<EdgingRecordAndOffset> next = iterator.next();
            assertEquals(locusPosition++, next.getPosition());
            assertEquals(0, next.getRecordAndOffsets().size());
        }
        assertEquals(61, locusPosition);
    }

    /**
     * Tests that for reads, that intersect given interval list read start is shifted to the start of the interval and
     * length is adjusted to the end of the interval.
     */
    @Test
    public void testIntersectingInterval() {
        SamReader samReader = createSamFileReader(createSamFileHeader("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n"));
        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t5\t15\t+\ttest");
        EdgeReadIterator iterator = new EdgeReadIterator(samReader, intervals);
        int locusPosition = 5;
        while (iterator.hasNext()) {
            AbstractLocusInfo<EdgingRecordAndOffset> next = iterator.next();
            int position = next.getPosition();
            assertEquals(locusPosition++, position);
            if (position == 5) {
                assertEquals(2, next.getRecordAndOffsets().size());
                for (EdgingRecordAndOffset record : next.getRecordAndOffsets()) {
                    assertEquals(11, record.getLength());
                }
            } else {
                assertEquals(0, next.getRecordAndOffsets().size());
            }
        }
        assertEquals(16, locusPosition);
    }

    /**
     * Test for mixed reads: intersecting and not the interval
     */
    @Test
    public void testIntersectingAndNotInterval() {

        final SAMRecordSetBuilder builder = getRecordBuilder();
        builder.setHeader(createSamFileHeader("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n"));
        // add records up to coverage for the test in that position
        final int startPosition = 40;
        // Were it not for the gap, these two reads would not overlap
        builder.addFrag("record2", 0, startPosition, true, false, "36M", null, 10);

        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t40\t80\t+\ttest");

        EdgeReadIterator iterator = new EdgeReadIterator(builder.getSamReader(), intervals);
        int locusPosition = 40;
        while (iterator.hasNext()) {
            final AbstractLocusInfo<EdgingRecordAndOffset> next = iterator.next();
            int position = next.getPosition();
            assertEquals(locusPosition++, position);
            if (position == 40) {
                assertEquals(1, next.getRecordAndOffsets().size());
                for (EdgingRecordAndOffset record : next.getRecordAndOffsets()) {
                    assertEquals(36, record.getLength());
                    assertEquals(EdgingRecordAndOffset.Type.BEGIN, record.getType());
                }
            } else if (position == 76) {
                assertEquals(1, next.getRecordAndOffsets().size());
                for (EdgingRecordAndOffset record : next.getRecordAndOffsets()) {
                    assertEquals(36, record.getLength());
                    assertEquals(EdgingRecordAndOffset.Type.END, record.getType());
                }
            } else {
                assertEquals(0, next.getRecordAndOffsets().size());
            }
        }
        assertEquals(81, locusPosition);
    }

    @Test
    public void testNoGapsInLocusAccumulator() {
        final SamReader reader = SamReaderFactory.make().open(new File("src/test/resources/htsjdk/samtools/util/sliver.sam"));
        final EdgeReadIterator iterator = new EdgeReadIterator(reader, null);

        AbstractLocusInfo<EdgingRecordAndOffset> previous = null;
        int counter = 0;
        while (iterator.hasNext() && (previous == null || previous.getPosition() < 1_000_000)) {
            counter++;
            final AbstractLocusInfo<EdgingRecordAndOffset> next = iterator.next();
            if (previous != null) {
                Assert.assertEquals(next.getPosition(), previous.getPosition() + 1);
            }
            previous = next;
        }
        Assert.assertEquals(counter, 1_000_000);
    }

    /**
     * Test for intersecting interval for read with a deletion in the middle
     */
    @Test
    public void testIntersectingIntervalWithComplicatedCigar() {

        final SAMRecordSetBuilder builder = getRecordBuilder();
        builder.setHeader(createSamFileHeader("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n"));
        // add records up to coverage for the test in that position
        final int startPosition = 1;
        // Were it not for the gap, these two reads would not overlap
        builder.addFrag("record", 0, startPosition, true, false, "10M3D26M", null, 10);

        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t5\t20\t+\ttest");

        EdgeReadIterator iterator = new EdgeReadIterator(builder.getSamReader(), intervals);
        int locusPosition = 5;
        int[] expectedLength = new int[]{6, 7};
        int i = 0;
        while (iterator.hasNext()) {
            AbstractLocusInfo<EdgingRecordAndOffset> next = iterator.next();
            int position = next.getPosition();
            assertEquals(locusPosition++, position);
            if (position == 5 || position == 14) {
                assertEquals(1, next.getRecordAndOffsets().size());
                for (EdgingRecordAndOffset record : next.getRecordAndOffsets()) {
                    assertEquals(expectedLength[i], record.getLength());
                    assertEquals(EdgingRecordAndOffset.Type.BEGIN, record.getType());
                }
            } else if (position == 11) {
                assertEquals(1, next.getRecordAndOffsets().size());
                for (EdgingRecordAndOffset record : next.getRecordAndOffsets()) {
                    assertEquals(expectedLength[i], record.getLength());
                    assertEquals(EdgingRecordAndOffset.Type.END, record.getType());
                }
                i++;
            } else {
                assertEquals(0, next.getRecordAndOffsets().size());
            }
        }
        assertEquals(21, locusPosition);
    }

    /**
     * Test for handling multiple intervals
     */
    @Test
    public void testMultipleIntervals() {
        String samHeaderString = "@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chr1\tLN:100\n" +
                "@SQ\tSN:chr2\tLN:100\n" +
                "@SQ\tSN:chr3\tLN:100\n" +
                "@SQ\tSN:chr4\tLN:100\n";

        final SAMRecordSetBuilder builder = getRecordBuilder();
        builder.setHeader(createSamFileHeader(samHeaderString));

        builder.addFrag("fullyContainedInChr1", 0, 10, false, false, "10M10D10M", null, 10);
        builder.addFrag("startOutsideChr2", 1, 15, false, false, "10M10D10M", null, 10);
        builder.addFrag("endOutsideChr2", 1, 65, false, false, "10M10D10M", null, 10);
        builder.addFrag("spanningThreeIntervalsChr4", 3, 1, false, false, "100M", null, 10);

        IntervalList intervals = createIntervalList(samHeaderString +
                "chr1\t1\t100\t+\ttest\n" +
                "chr2\t20\t70\t+\ttest\n" +
                "chr4\t20\t30\t+\ttest\n" +
                "chr4\t40\t50\t+\ttest\n" +
                "chr4\t60\t70\t+\ttest\n");

        // These are the covered intervals that we expect to be covered
        final Interval[] intervalsCovered = {
                new Interval("chr1", 10, 19), // fullyContainedInChr1: first alignment block
                new Interval("chr1", 30, 39), // fullyContainedInChr1: second alignment block
                new Interval("chr2", 20, 24), // startOutsideChr2: first alignment block
                new Interval("chr2", 35, 44), // startOutsideChr2: second alignment block
                new Interval("chr2", 65, 70), // endOutsideChr2: first alignment block (second isn't covered at all)
                new Interval("chr4", 20, 30), // spanningThreeIntervalsChr4: first interval
                new Interval("chr4", 40, 50), // spanningThreeIntervalsChr4: second interval
                new Interval("chr4", 60, 70), // spanningThreeIntervalsChr4: third interval
        };

        EdgeReadIterator iterator = new EdgeReadIterator(builder.getSamReader(), intervals);
        AbstractLocusInfo<EdgingRecordAndOffset> currentLocusInfo = iterator.next();
        for (final Interval interval : intervalsCovered) {
            // Continue iterating over the LocusInfos if there is no RecordAndOffsets (size == 0) or it isn't a BEGIN record.
            while(currentLocusInfo.getRecordAndOffsets().size() < 1 || currentLocusInfo.getRecordAndOffsets().get(0).getType() != EdgingRecordAndOffset.Type.BEGIN) {
                currentLocusInfo = iterator.next();
            }
            EdgingRecordAndOffset currentEdgingRecordAndOffset = currentLocusInfo.getRecordAndOffsets().get(0);

            assertEquals(currentLocusInfo.getContig(), interval.getContig(), "Read: " + currentEdgingRecordAndOffset.getReadName());
            assertEquals(currentLocusInfo.getPosition(), interval.getStart(), "Read: " + currentEdgingRecordAndOffset.getReadName());
            assertEquals(currentLocusInfo.getPosition() + currentEdgingRecordAndOffset.getLength() - 1, interval.getEnd(), "Read: " + currentEdgingRecordAndOffset.getReadName());

            currentLocusInfo = iterator.next();
        }
    }

    /**
     * Test for handling multiple intervals
     */
    @Test
    public void testIntervalCompletelyContainsRead() {
        String samHeaderString = "@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:Z_AlphabeticallyOutOfOrderContig\tLN:100\n" +
                "@SQ\tSN:chr1\tLN:100\n" +
                "@SQ\tSN:chr2\tLN:100\n" +
                "@SQ\tSN:chr3\tLN:100\n" +
                "@SQ\tSN:chr4\tLN:100\n" +
                "@SQ\tSN:chr5\tLN:100\n" +
                "@SQ\tSN:chr6\tLN:100\n";

        final SAMRecordSetBuilder builder = getRecordBuilder();
        builder.setHeader(createSamFileHeader(samHeaderString));

        builder.addFrag("containedInChr1", 1, 1, false, false, "10M", null, 10);
        builder.addFrag("notContainedInChr2", 2, 42, false, false, "10M", null, 10);
        builder.addFrag("containedInChr4", 4, 41, false, false, "10M", null, 10);
        builder.addFrag("containedInChr5", 5, 2, false, false, "10M", null, 10);
        builder.addFrag("notContainedInChr6", 6, 1, false, false, "10M", null, 10);

        IntervalList intervals = createIntervalList(samHeaderString +
                "chr1\t1\t50\t+\ttest\n" +
                "chr2\t1\t50\t+\ttest\n" +
                "chr3\t1\t50\t+\ttest\n" +
                "chr4\t1\t50\t+\ttest\n" +
                "chr5\t1\t50\t+\ttest\n");

        final boolean[] expectedResults = {
                true,
                false,
                true,
                true,
                false
        };

        EdgeReadIterator iterator = new EdgeReadIterator(builder.getSamReader(), intervals);
        int i = 0;
        for (final SAMRecord record : builder.getRecords()) {
            assertEquals(iterator.advanceCurrentIntervalAndCheckIfIntervalContainsRead(record), expectedResults[i], "Read: " + record.getReadName());
            i += 1;
        }
        assertEquals(i, expectedResults.length); // Make sure we checked all reads
    }

    private void fillEmptyLocus(int[] expectedReferencePositions, int[] expectedDepths, int[][] expectedReadOffsets, int i) {
        expectedReferencePositions[i] = i + 1;
        expectedDepths[i] = 0;
        expectedReadOffsets[i] = new int[]{};
    }

    private SamReader createSamFileReader() {
        return createSamFileReader(null);
    }

    private SamReader createSamFileReader(final SAMFileHeader header) {
        final SAMRecordSetBuilder builder = getRecordBuilder();
        if (header != null) {
            builder.setHeader(header);
        }
        // add records up to coverage for the test in that position
        final int startPosition = 1;
        for (int i = 0; i < coverage; i++) {
            // add a negative-strand fragment mapped on chrM with base quality of 10
            builder.addFrag("record" + i, 0, startPosition, true, false, "36M", null, 10);
        }
        return builder.getSamReader();
    }

    private IntervalList createIntervalList(final String s) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())))) {
            return IntervalList.fromReader(br);
        } catch (IOException e) {
            throw new RuntimeException("Trouble closing reader: " + s, e);
        }
    }

    private SAMFileHeader createSamFileHeader(final String s) {
        return new SAMTextHeaderCodec().decode(BufferedLineReader.fromString(s), null);
    }
}
