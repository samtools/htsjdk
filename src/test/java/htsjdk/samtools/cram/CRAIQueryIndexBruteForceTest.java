package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Checks {@link CRAIQueryIndex}'s binary search against an obviously-correct linear scan over
 * randomly generated indexes. The binary search plus running-maximum-end is the one piece of this
 * class with a real chance of an off-by-one, and hand-written cases only cover the shapes someone
 * thought of.
 */
public class CRAIQueryIndexBruteForceTest extends HtsjdkTest {

    private static final int REFERENCE_COUNT = 4;
    private static final int MAX_POSITION = 5000;

    /** The definition of the query, written the slow obvious way. */
    private static long[] bruteForce(
            final List<CRAIEntry> entries, final int referenceIndex, final int start, final int end) {
        final long queryStart = start < 1 ? 1 : start;
        final long queryEnd = end < 1 ? Long.MAX_VALUE : end;
        final TreeSet<Long> offsets = new TreeSet<>();
        for (final CRAIEntry entry : entries) {
            if (entry.getSequenceId() != referenceIndex) {
                continue;
            }
            final long entryStart = entry.getAlignmentStart();
            final long entryEndExclusive = entryStart + entry.getAlignmentSpan();
            if (entryStart <= queryEnd && entryEndExclusive > queryStart) {
                offsets.add(entry.getContainerStartByteOffset());
            }
        }
        return offsets.stream().mapToLong(Long::longValue).toArray();
    }

    /**
     * Build an index whose slices look like real CRAM: mostly coordinate-ordered and abutting, but
     * with occasional long spans that swallow their neighbours, gaps, and containers shared by
     * several slices.
     */
    private static List<CRAIEntry> randomEntries(final Random random, final int entriesPerReference) {
        final List<CRAIEntry> entries = new ArrayList<>();
        long containerOffset = 0;
        for (int referenceIndex = 0; referenceIndex < REFERENCE_COUNT; referenceIndex++) {
            int position = 1;
            for (int i = 0; i < entriesPerReference; i++) {
                final int span = random.nextInt(10) == 0 ? 1 + random.nextInt(2000) : 1 + random.nextInt(60);
                entries.add(new CRAIEntry(referenceIndex, position, span, containerOffset, 0, 100));
                // Sometimes leave a gap, sometimes overlap, sometimes put the next slice in the same
                // container the way a multi-slice container does.
                position += random.nextInt(80);
                if (position < 1) {
                    position = 1;
                }
                if (random.nextInt(4) != 0) {
                    containerOffset += 1 + random.nextInt(1000);
                }
            }
            containerOffset += 1000;
        }
        return entries;
    }

    private static void assertMatchesBruteForce(final long seed, final int entriesPerReference, final int queries) {
        final Random random = new Random(seed);
        final List<CRAIEntry> entries = randomEntries(random, entriesPerReference);
        final CRAIQueryIndex index = new CRAIQueryIndex(entries);

        for (int i = 0; i < queries; i++) {
            final int referenceIndex = random.nextInt(REFERENCE_COUNT + 1); // +1 so unknown refs get hit too
            final int start = 1 + random.nextInt(MAX_POSITION);
            final int end = start + random.nextInt(500);
            Assert.assertEquals(
                    index.getContainerOffsets(referenceIndex, start, end),
                    bruteForce(entries, referenceIndex, start, end),
                    "seed " + seed + ", query " + referenceIndex + ":" + start + "-" + end);
        }
    }

    @Test
    public void testMatchesBruteForceOnSmallIndexes() {
        for (long seed = 0; seed < 25; seed++) {
            assertMatchesBruteForce(seed, 8, 200);
        }
    }

    @Test
    public void testMatchesBruteForceOnLargerIndexes() {
        for (long seed = 100; seed < 110; seed++) {
            assertMatchesBruteForce(seed, 250, 400);
        }
    }

    @Test
    public void testMatchesBruteForceForSingleBaseQueries() {
        final Random random = new Random(7);
        final List<CRAIEntry> entries = randomEntries(random, 60);
        final CRAIQueryIndex index = new CRAIQueryIndex(entries);

        for (int position = 1; position <= 2000; position++) {
            Assert.assertEquals(
                    index.getContainerOffsets(0, position, position),
                    bruteForce(entries, 0, position, position),
                    "position " + position);
        }
    }

    @Test
    public void testMatchesBruteForceForWholeReferenceQueries() {
        final Random random = new Random(11);
        final List<CRAIEntry> entries = randomEntries(random, 120);
        final CRAIQueryIndex index = new CRAIQueryIndex(entries);

        for (int referenceIndex = 0; referenceIndex < REFERENCE_COUNT; referenceIndex++) {
            Assert.assertEquals(
                    index.getContainerOffsets(referenceIndex, 0, 0),
                    bruteForce(entries, referenceIndex, 0, 0),
                    "reference " + referenceIndex);
            Assert.assertEquals(
                    index.getContainerOffsets(referenceIndex, 1, -1),
                    bruteForce(entries, referenceIndex, 1, -1),
                    "reference " + referenceIndex);
        }
    }
}
