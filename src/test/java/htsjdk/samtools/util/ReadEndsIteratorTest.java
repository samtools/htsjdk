package htsjdk.samtools.util;

import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class ReadEndsIteratorTest {

    @Test
    public void testBasicIterator() {
        final ReadEndsIterator sli = getLocusIterator();

        int pos = 1;
        for (final AbstractLocusInfo<TypedRecordAndOffset> li : sli) {
            if (pos == 1 || pos == 37) {
                assertEquals(pos++, li.getPosition());
                assertEquals(2, li.getRecordAndPositions().size());
            } else {
                assertEquals(pos++, li.getPosition());
                assertEquals(0, li.getRecordAndPositions().size());
            }
        }

    }

    @Test
    public void testUncoveredLoci() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100000\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++";
        final String s1 = "3851612\t16\tchrM\t165\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;

        final SamReader samReader = createSamFileReader(exampleSam);
        final ReadEndsIterator sli = new ReadEndsIterator(samReader);

        int pos = 1;
        final int coveredStart = 165;
        final int coveredEnd = CoordMath.getEnd(coveredStart, seq1.length()) + 1;
        for (final AbstractLocusInfo li : sli) {
            Assert.assertEquals(li.getPosition(), pos++);
            final int expectedReads;
            if (li.getPosition() == coveredStart || li.getPosition() == coveredEnd) {
                expectedReads = 2;
            } else {
                expectedReads = 0;
            }
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedReads);
        }
        Assert.assertEquals(pos, 100001);
    }

    /**
     * Try all CIGAR operands (except H and P) and confirm that loci produced by SamLocusIterator are as expected.
     */
    @Test
    public void testSimpleGappedAlignment() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++";
        final String s1 = "3851612\t16\tchrM\t1\t255\t3S3M3N3M3D3M3I1N18M3S\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s1;
        final SamReader samReader = createSamFileReader(exampleSam);
        final ReadEndsIterator sli = createReadEndsIterator(samReader);
        while (sli.hasNext()) {
            AbstractLocusInfo<TypedRecordAndOffset> info = sli.next();
            int pos = info.getPosition();
            if (pos == 1 || pos == 7 || pos == 13 || pos == 17) {
                assertEquals(TypedRecordAndOffset.Type.BEGIN, info.getRecordAndPositions().get(0).getType());
                assertEquals(TypedRecordAndOffset.Type.BEGIN, info.getRecordAndPositions().get(1).getType());
            } else if (pos == 4 || pos == 10 || pos == 16 || pos == 35) {
                assertEquals(TypedRecordAndOffset.Type.END, info.getRecordAndPositions().get(0).getType());
                assertEquals(TypedRecordAndOffset.Type.END, info.getRecordAndPositions().get(1).getType());
            }
        }
    }

    /**
     * Test two reads that overlap because one has a deletion in the middle of it.
     */
    @Test
    public void testOverlappingGappedAlignments() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:80\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++";
        // Were it not for the gap, these two reads would not overlap
        final String s1 = "3851612\t16\tchrM\t1\t255\t18M10D18M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String s2 = "3851613\t16\tchrM\t41\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        final String exampleSam = sqHeader + s1 + s2;

        final SamReader samReader = createSamFileReader(exampleSam);
        final ReadEndsIterator sli = createReadEndsIterator(samReader);
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
            fillEpmtyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }
        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 19;
        expectedReadOffsets[i++] = new int[]{0};

        for (; i < 28; ++i) {
            fillEpmtyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        // Gap of 10, then 13 bases from the first read
        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 29;
        expectedReadOffsets[i++] = new int[]{18};

        for (; i < 40; ++i) {
            fillEpmtyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 41;
        expectedReadOffsets[i++] = new int[]{0};

        for (; i < 46; ++i) {
            fillEpmtyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 47;
        expectedReadOffsets[i++] = new int[]{18};

        // Last 5 bases of first read overlap first 5 bases of second read
        for (; i < 76; ++i) {
            fillEpmtyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        expectedDepths[i] = 1;
        expectedReferencePositions[i] = 77;
        expectedReadOffsets[i++] = new int[]{0};

        // Last 31 bases of 2nd read

        for (; i <= 80; ++i) {
            fillEpmtyLocus(expectedReferencePositions, expectedDepths, expectedReadOffsets, i);
        }

        i = 0;
        for (final AbstractLocusInfo<TypedRecordAndOffset> li : sli) {
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedDepths[i]);
            Assert.assertEquals(li.getPosition(), expectedReferencePositions[i]);
            Assert.assertEquals(li.getRecordAndPositions().size(), expectedReadOffsets[i].length);
            for (int j = 0; j < expectedReadOffsets[i].length; ++j) {
                Assert.assertEquals(li.getRecordAndPositions().get(j).getOffset(), expectedReadOffsets[i][j]);
                if (start.contains(li.getPosition() - 1)) {
                    Assert.assertEquals(li.getRecordAndPositions().get(j).getType(), TypedRecordAndOffset.Type.BEGIN);
                }
                if (end.contains(li.getPosition() - 1)) {
                    Assert.assertEquals(li.getRecordAndPositions().get(j).getType(), TypedRecordAndOffset.Type.END);
                }
            }
            ++i;
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetQualityCutOff() {
        final ReadEndsIterator sli = getLocusIterator();

        sli.setQualityScoreCutoff(10);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetMaxReadsToAccumulatePerLocus() {
        final ReadEndsIterator sli = getLocusIterator();

        sli.setMaxReadsToAccumulatePerLocus(100);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetEmitUncoveredLoci() {
        final ReadEndsIterator sli = getLocusIterator();

        sli.setEmitUncoveredLoci(true);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testSetIncludeIndels() {
        final ReadEndsIterator sli = getLocusIterator();

        sli.setIncludeIndels(true);
    }

    /**
     * Tests that reads, that don't intersect given interval list, are excluded from iterator
     */
    @Test
    public void testNotIntersectingInterval() {
        SamReader samReader = createSamFileReader(getExampleSamString());

        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t50\t60\t+\ttest");
        ReadEndsIterator iterator = new ReadEndsIterator(samReader, intervals);
        int locusPosition = 50;
        while (iterator.hasNext()) {
            AbstractLocusInfo<TypedRecordAndOffset> next = iterator.next();
            assertEquals(locusPosition++, next.getPosition());
            assertEquals(0, next.getRecordAndPositions().size());
        }
        assertEquals(61, locusPosition);
    }

    /**
     * Tests that for reads, that intersect given interval list read start is shifted to the start of the interval and
     * length is adjusted to the end of the interval.
     */
    @Test
    public void testIntersectingInterval() {
        SamReader samReader = createSamFileReader(getExampleSamString());
        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t5\t15\t+\ttest");
        ReadEndsIterator iterator = new ReadEndsIterator(samReader, intervals);
        int locusPosition = 5;
        while (iterator.hasNext()) {
            AbstractLocusInfo<TypedRecordAndOffset> next = iterator.next();
            int position = next.getPosition();
            assertEquals(locusPosition++, position);
            if (position == 5) {
                assertEquals(2, next.getRecordAndPositions().size());
                for (TypedRecordAndOffset record : next.getRecordAndPositions()) {
                    assertEquals(11, record.getLength());
                }
            } else {
                assertEquals(0, next.getRecordAndPositions().size());
            }
        }
        assertEquals(16, locusPosition);
    }

    /**
     * Test for mixed reads: intersecting and not the interval
     */
    @Test
    public void testIntersectingAndNotInterval() {

        String exampleSamString = getExampleSamString();
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++";
        final String s1 = "3851612\t16\tchrM\t40\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        exampleSamString = exampleSamString + s1;
        SamReader samReader = createSamFileReader(exampleSamString);

        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t40\t80\t+\ttest");

        ReadEndsIterator iterator = new ReadEndsIterator(samReader, intervals);
        int locusPosition = 40;
        while (iterator.hasNext()) {
            AbstractLocusInfo<TypedRecordAndOffset> next = iterator.next();
            int position = next.getPosition();
            assertEquals(locusPosition++, position);
            if (position == 40) {
                assertEquals(1, next.getRecordAndPositions().size());
                for (TypedRecordAndOffset record : next.getRecordAndPositions()) {
                    assertEquals(36, record.getLength());
                    assertEquals(TypedRecordAndOffset.Type.BEGIN, record.getType());
                }
            } else if (position == 76) {
                assertEquals(1, next.getRecordAndPositions().size());
                for (TypedRecordAndOffset record : next.getRecordAndPositions()) {
                    assertEquals(36, record.getLength());
                    assertEquals(TypedRecordAndOffset.Type.END, record.getType());
                }
            } else {
                assertEquals(0, next.getRecordAndPositions().size());
            }
        }
        assertEquals(81, locusPosition);
    }


    /**
     * Test for intersecting interval for read with a deletion in the middle
     */
    @Test
    public void testIntersectingIntervalWithCimplicatedCigar() {

        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++";
        final String s1 = "3851612\t16\tchrM\t1\t255\t10M3D26M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        String exampleSamString = sqHeader + s1;
        SamReader samReader = createSamFileReader(exampleSamString);

        IntervalList intervals = createIntervalList("@HD\tSO:coordinate\tVN:1.0\n" +
                "@SQ\tSN:chrM\tLN:100\n" +
                "chrM\t5\t20\t+\ttest");

        ReadEndsIterator iterator = new ReadEndsIterator(samReader, intervals);
        int locusPosition = 5;
        int[] expectedLength = new int[]{6, 7};
        int i = 0;
        while (iterator.hasNext()) {
            AbstractLocusInfo<TypedRecordAndOffset> next = iterator.next();
            int position = next.getPosition();
            assertEquals(locusPosition++, position);
            if (position == 5 || position == 14) {
                assertEquals(1, next.getRecordAndPositions().size());
                for (TypedRecordAndOffset record : next.getRecordAndPositions()) {
                    assertEquals(expectedLength[i], record.getLength());
                    assertEquals(TypedRecordAndOffset.Type.BEGIN, record.getType());
                }
            } else if (position == 11) {
                assertEquals(1, next.getRecordAndPositions().size());
                for (TypedRecordAndOffset record : next.getRecordAndPositions()) {
                    assertEquals(expectedLength[i], record.getLength());
                    assertEquals(TypedRecordAndOffset.Type.END, record.getType());
                }
                i++;
            } else {
                assertEquals(0, next.getRecordAndPositions().size());
            }
        }
        assertEquals(21, locusPosition);
    }


    private ReadEndsIterator getLocusIterator() {
        final String exampleSam = getExampleSamString();
        final SamReader samReader = createSamFileReader(exampleSam);
        return createReadEndsIterator(samReader);
    }

    private String getExampleSamString() {
        final String sqHeader = "@HD\tSO:coordinate\tVN:1.0\n@SQ\tSN:chrM\tAS:HG18\tLN:100\n";
        final String seq1 = "ACCTACGTTCAATATTACAGGCGAACATACTTACTA";
        final String qual1 = "++++++++++++++++++++++++++++++++++++";
        final String s1 = "3851612\t16\tchrM\t1\t255\t36M\t*\t0\t0\t" + seq1 + "\t" + qual1 + "\n";
        return sqHeader + s1 + s1;
    }

    private void fillEpmtyLocus(int[] expectedReferencePositions, int[] expectedDepths, int[][] expectedReadOffsets, int i) {
        expectedReferencePositions[i] = i + 1;
        expectedDepths[i] = 0;
        expectedReadOffsets[i] = new int[]{};
    }

    private SamReader createSamFileReader(final String samExample) {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(samExample.getBytes());
        return SamReaderFactory.makeDefault().open(SamInputResource.of(inputStream));
    }

    private ReadEndsIterator createReadEndsIterator(final SamReader samReader) {
        final ReadEndsIterator ret = new ReadEndsIterator(samReader);
        return ret;
    }

    private IntervalList createIntervalList(String s) {
        return IntervalList.fromReader(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes()))));
    }
}
