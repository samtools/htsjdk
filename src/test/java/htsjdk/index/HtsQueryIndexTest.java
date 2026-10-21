package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.BAMIndex;
import htsjdk.samtools.BAMIndexMetaData;
import htsjdk.samtools.SAMFileSpan;
import java.util.Optional;
import java.util.OptionalLong;
import org.testng.Assert;
import org.testng.annotations.Test;

/** The format-neutral index contract and {@link BAMIndex}'s implementation of it. */
public class HtsQueryIndexTest extends HtsjdkTest {

    /** A BAMIndex stub; exercises the inherited defaults. */
    private static class StubBAMIndex implements BAMIndex {
        private final long startOfLastLinearBin;
        private final OptionalLong unplacedRecordCount;

        StubBAMIndex(final long startOfLastLinearBin) {
            this(startOfLastLinearBin, OptionalLong.empty());
        }

        StubBAMIndex(final long startOfLastLinearBin, final OptionalLong unplacedRecordCount) {
            this.startOfLastLinearBin = startOfLastLinearBin;
            this.unplacedRecordCount = unplacedRecordCount;
        }

        @Override
        public OptionalLong getUnplacedRecordCount() {
            return unplacedRecordCount;
        }

        @Override
        public BAMFileSpan getSpanOverlapping(final int referenceIndex, final int startPos, final int endPos) {
            return new BAMFileSpan();
        }

        @Override
        public long getStartOfLastLinearBin() {
            return startOfLastLinearBin;
        }

        @Override
        public BAMIndexMetaData getMetaData(final int reference) {
            return null;
        }

        @Override
        public void close() {}
    }

    /** An index with only the required methods. */
    private static class MinimalQueryIndex implements HtsQueryIndex {
        @Override
        public HtsFileSpan getSpanOverlapping(final int referenceIndex, final int start, final int end) {
            return new BAMFileSpan();
        }

        @Override
        public void close() {}
    }

    @Test
    public void testSamFileSpanIsAnHtsFileSpan() {
        Assert.assertTrue(HtsFileSpan.class.isAssignableFrom(SAMFileSpan.class));
    }

    @Test
    public void testBamFileSpanIsAnHtsFileSpan() {
        // Required for BAMIndex.getSpanOverlapping's covariant return.
        Assert.assertTrue(HtsFileSpan.class.isAssignableFrom(BAMFileSpan.class));
    }

    @Test
    public void testBamIndexIsAnHtsQueryIndex() {
        Assert.assertTrue(HtsQueryIndex.class.isAssignableFrom(BAMIndex.class));
    }

    @Test
    public void testAnIndexWithoutUnplacedRecordsReportsNoSpanForThem() {
        try (final MinimalQueryIndex index = new MinimalQueryIndex()) {
            Assert.assertEquals(index.getSpanOfUnplaced(), Optional.empty());
        }
    }

    @Test
    public void testBamIndexReportsAnUnplacedSpanStartingAtTheLastLinearBin() {
        try (final BAMIndex index = new StubBAMIndex(12345L)) {
            final Optional<HtsFileSpan> span = index.getSpanOfUnplaced();
            Assert.assertTrue(span.isPresent());
            Assert.assertFalse(span.get().isEmpty());
            Assert.assertEquals(((BAMFileSpan) span.get()).toCoordinateArray(), new long[] {12345L, Long.MAX_VALUE});
        }
    }

    @Test
    public void testAnIndexThatRecordsNoCountReportsNone() {
        try (final MinimalQueryIndex index = new MinimalQueryIndex()) {
            Assert.assertEquals(index.getUnplacedRecordCount(), OptionalLong.empty());
        }
    }

    @Test
    public void testBamIndexReportsNoUnplacedSpanWhenItCountsNoUnplacedRecords() {
        try (final BAMIndex index = new StubBAMIndex(12345L, OptionalLong.of(0))) {
            Assert.assertEquals(index.getSpanOfUnplaced(), Optional.empty());
        }
    }

    @Test
    public void testBamIndexReportsAnUnplacedSpanWhenItCountsUnplacedRecords() {
        try (final BAMIndex index = new StubBAMIndex(12345L, OptionalLong.of(3))) {
            Assert.assertEquals(
                    ((BAMFileSpan) index.getSpanOfUnplaced().orElseThrow()).toCoordinateArray(),
                    new long[] {12345L, Long.MAX_VALUE});
        }
    }

    @Test
    public void testBamIndexReportsNoUnplacedSpanWhenNothingIsMapped() {
        // -1: no mapped reads.
        try (final BAMIndex index = new StubBAMIndex(-1L)) {
            Assert.assertEquals(index.getSpanOfUnplaced(), Optional.empty());
        }
    }

    @Test
    public void testAnEmptySpanReportsItselfEmpty() {
        final HtsFileSpan span = new BAMFileSpan();
        Assert.assertTrue(span.isEmpty());
    }
}
