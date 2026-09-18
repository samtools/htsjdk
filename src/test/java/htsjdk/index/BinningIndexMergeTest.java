package htsjdk.index;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link BinningIndex#merge}: combining the indexes of consecutive parts of one file. */
public class BinningIndexMergeTest extends HtsjdkTest {
    private static final int MIN_SHIFT = BinningIndex.BAI_MIN_SHIFT;
    private static final int DEPTH = BinningIndex.BAI_DEPTH;

    /**
     * Two parts that together cover three references; part boundaries fall in the middle of reference 1. Each part
     * is indexed as though it were a whole file, i.e. with offsets starting at zero.
     */
    private static final class TwoParts {
        final IndexedRecords first = new IndexedRecords();
        final IndexedRecords second = new IndexedRecords();
        final List<IndexedRecords.Rec> concatenated = new ArrayList<>();
        final BinningIndex merged;

        TwoParts() {
            final IndexedRecords whole = new IndexedRecords();
            for (int start = 1; start < 3_000_000; start += 1_700) addTo(first, whole, 0, start);
            for (int start = 1; start < 1_000_000; start += 1_700) addTo(first, whole, 1, start);
            // The second part starts on a fresh block, as a separately compressed part does.
            while (whole.records().size() % 6 != 0) addTo(first, whole, 1, 1_000_000);
            for (int start = 1_000_001; start < 4_000_000; start += 1_700) addTo(second, whole, 1, start);
            for (int start = 1; start < 2_000_000; start += 1_700) addTo(second, whole, 2, start);
            concatenated.addAll(whole.records());
            merged = BinningIndex.merge(
                    List.of(first.index(MIN_SHIFT, DEPTH, 3), second.index(MIN_SHIFT, DEPTH, 3)),
                    new long[] {0, first.compressedLength()});
        }

        private static void addTo(
                final IndexedRecords part, final IndexedRecords whole, final int ref, final int start) {
            final int end = start + (start % 11 == 0 ? 200_000 : 0);
            part.add(ref, start, end);
            whole.add(ref, start, end);
        }
    }

    @Test
    public void testMergedIndexFindsRecordsFromBothParts() {
        final TwoParts parts = new TwoParts();
        final Random random = new Random(3);
        int overlapsSeen = 0;
        for (int i = 0; i < 300; i++) {
            final int referenceIndex = random.nextInt(3);
            final int start = 1 + random.nextInt(4_000_000);
            final int end = start + random.nextInt(i % 4 == 0 ? 1_500_000 : 4_000);
            final BAMFileSpan span = parts.merged.getSpanOverlapping(referenceIndex, start, end);
            overlapsSeen +=
                    IndexedRecords.assertSpanCoversOverlaps(parts.concatenated, span, referenceIndex, start, end);
        }
        Assert.assertTrue(overlapsSeen > 1_000, "queries should have hit plenty of records");
    }

    @Test
    public void testQueryAcrossThePartBoundaryFindsRecordsFromBothParts() {
        final TwoParts parts = new TwoParts();
        final BAMFileSpan span = parts.merged.getSpanOverlapping(1, 990_000, 1_010_000);
        final int found = IndexedRecords.assertSpanCoversOverlaps(parts.concatenated, span, 1, 990_000, 1_010_000);
        final long fromSecondPart = parts.second.records().stream()
                .filter(rec -> rec.overlaps(1, 990_000, 1_010_000))
                .count();
        Assert.assertTrue(fromSecondPart > 0 && found > fromSecondPart, "both parts should contribute");
    }

    @Test
    public void testMergingASinglePartAtOffsetZeroChangesNothing() {
        final BinningIndex index = new TwoParts().first.index(MIN_SHIFT, DEPTH, 3);
        Assert.assertEquals(BinningIndex.merge(List.of(index), new long[] {0}), index);
    }

    @Test
    public void testPartsWithUnsetWindowsMergeToTheIndexOfTheWholeFile() {
        final IndexedRecords first = new IndexedRecords();
        final IndexedRecords second = new IndexedRecords();
        final IndexedRecords whole = new IndexedRecords();
        for (int start = 1; start < 200_000; start += 1_700) TwoParts.addTo(first, whole, 0, start);
        while (whole.records().size() % 6 != 0) TwoParts.addTo(first, whole, 0, 200_000);
        // Reference 0 resumes well past where the first part left it, and reference 1 starts away from its first
        // window: windows that neither part has a record in, which a whole-file index fills from the next window that
        // has one.
        for (int start = 500_001; start < 700_000; start += 1_700) TwoParts.addTo(second, whole, 0, start);
        for (int start = 100_001; start < 300_000; start += 1_700) TwoParts.addTo(second, whole, 1, start);

        final BinningIndex merged = BinningIndex.merge(
                List.of(first.partIndex(MIN_SHIFT, DEPTH, 2), second.partIndex(MIN_SHIFT, DEPTH, 2)),
                new long[] {0, first.compressedLength()});

        Assert.assertEquals(merged, whole.index(MIN_SHIFT, DEPTH, 2));
    }

    @Test
    public void testAWindowNoPartHasARecordInTakesTheOffsetOfTheNextWindowWithOne() {
        final IndexedRecords first = new IndexedRecords().add(0, 1, 100);
        final IndexedRecords second = new IndexedRecords().add(0, 3 * 16_384 + 1, 3 * 16_384 + 100);

        final BinningIndex merged = BinningIndex.merge(
                List.of(first.partIndex(MIN_SHIFT, DEPTH, 1), second.partIndex(MIN_SHIFT, DEPTH, 1)),
                new long[] {0, first.compressedLength()});

        final long firstRecord = first.records().get(0).chunkStart();
        final long secondRecord =
                BlockCompressedFilePointerUtil.shift(second.records().get(0).chunkStart(), first.compressedLength());
        Assert.assertEquals(
                merged.getReference(0).getLinearIndex(),
                new long[] {firstRecord, secondRecord, secondRecord, secondRecord});
    }

    @Test
    public void testAReferenceThatStartsInALaterPartHasItsFirstRecordsOffsetBeforeIt() {
        final IndexedRecords first = new IndexedRecords().add(0, 1, 100);
        final IndexedRecords second = new IndexedRecords().add(1, 2 * 16_384 + 1, 2 * 16_384 + 100);

        final BinningIndex merged = BinningIndex.merge(
                List.of(first.partIndex(MIN_SHIFT, DEPTH, 2), second.partIndex(MIN_SHIFT, DEPTH, 2)),
                new long[] {0, first.compressedLength()});

        final long secondRecord =
                BlockCompressedFilePointerUtil.shift(second.records().get(0).chunkStart(), first.compressedLength());
        Assert.assertEquals(
                merged.getReference(1).getLinearIndex(), new long[] {secondRecord, secondRecord, secondRecord});
    }

    @Test
    public void testMetadataCountsAreSummedAndOffsetsSpanTheParts() {
        final ReferenceBins.Metadata firstPart = new ReferenceBins.Metadata(10 << 16, 50 << 16, 7, 1);
        final ReferenceBins.Metadata secondPart = new ReferenceBins.Metadata(0, 30 << 16, 5, 2);
        final BinningIndex merged =
                BinningIndex.merge(List.of(withMetadata(firstPart), withMetadata(secondPart)), new long[] {0, 1_000});
        Assert.assertEquals(
                merged.getReference(0).getMetadata().orElseThrow(),
                new ReferenceBins.Metadata(10 << 16, (1_000L + 30) << 16, 12, 3));
    }

    /** A one-reference, one-bin index carrying the given metadata pseudo-bin. */
    private static BinningIndex withMetadata(final ReferenceBins.Metadata metadata) {
        final ReferenceBins reference = new ReferenceBins(
                new int[] {4681}, new long[][] {{0, 1 << 16}}, new long[] {0}, new long[] {0}, metadata, DEPTH);
        return new BinningIndex(MIN_SHIFT, DEPTH, List.of(reference), -1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testPartsWithDifferentReferenceCountsAreRejected() {
        final BinningIndex.Builder builder = new BinningIndex.Builder(MIN_SHIFT, DEPTH);
        BinningIndex.merge(
                List.of(builder.build(2), new BinningIndex.Builder(MIN_SHIFT, DEPTH).build(3)), new long[] {0, 10});
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testPartsWithDifferentBinningSchemesAreRejected() {
        BinningIndex.merge(
                List.of(new BinningIndex.Builder(14, 5).build(1), new BinningIndex.Builder(14, 6).build(1)),
                new long[] {0, 10});
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNoPartsIsRejected() {
        BinningIndex.merge(List.of(), new long[0]);
    }

    @Test
    public void testBinSmallInEachPartButNotOverTheWholeFileIsKept() {
        final BinningIndex merged = BinningIndex.merge(partsSharingA16KbBin(), new long[] {0, 70_000});
        final ReferenceBins reference = merged.getReference(0);
        Assert.assertEquals(List.of(reference.getBinNumber(0), reference.getBinNumber(1)), List.of(585, 4682));
    }

    @Test
    public void testBinSmallOverTheWholeFileIsFoldedIntoItsParent() {
        final BinningIndex merged = BinningIndex.merge(partsSharingA16KbBin(), new long[] {0, 1_000});
        Assert.assertEquals(merged.getReference(0).getBinCount(), 1);
        Assert.assertEquals(merged.getReference(0).getBinNumber(0), 585);
    }

    /** Two parts with a record each in the 16 kb bin 4682, the first also with one in that bin's parent, 585. */
    private static List<BinningIndex> partsSharingA16KbBin() {
        final BinningIndex.Builder first = new BinningIndex.Builder(MIN_SHIFT, DEPTH).forMerging();
        first.add(0, 16_384, 16_385, 0, 100);
        first.add(0, 16_390, 16_390, 100, 200);
        final BinningIndex.Builder second = new BinningIndex.Builder(MIN_SHIFT, DEPTH).forMerging();
        second.add(0, 16_400, 16_400, 0, 100);
        return List.of(first.build(1), second.build(1));
    }
}
