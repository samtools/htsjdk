package htsjdk.index;

import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;

/**
 * Test fixture: coordinate-sorted records laid out in an imaginary BGZF file, so that index queries can be checked
 * against a brute-force scan of the records.
 */
final class IndexedRecords {
    private static final int RECORD_BYTES = 100;
    private static final int RECORDS_PER_BLOCK = 6;
    private static final int COMPRESSED_BLOCK_BYTES = 200;

    record Rec(int referenceIndex, int start, int end, long chunkStart, long chunkEnd) {
        /** Whether the record overlaps a 1-based inclusive query interval. */
        boolean overlaps(final int queryReference, final int queryStart, final int queryEnd) {
            return referenceIndex == queryReference && start <= queryEnd && Math.max(end, start) >= queryStart;
        }
    }

    private final List<Rec> records = new ArrayList<>();
    private final long firstBlockAddress;

    IndexedRecords() {
        this(0);
    }

    /**
     * @param firstBlockAddress compressed offset of the first block, for laying out a later part of a file
     */
    IndexedRecords(final long firstBlockAddress) {
        this.firstBlockAddress = firstBlockAddress;
    }

    /** Appends a record; callers add them in coordinate order. */
    IndexedRecords add(final int referenceIndex, final int start, final int end) {
        final int ordinal = records.size();
        records.add(new Rec(referenceIndex, start, end, offsetOf(ordinal), offsetOf(ordinal + 1)));
        return this;
    }

    /** The virtual offset at which the record with this ordinal starts; the ordinal one past the last record gives the end. */
    private long offsetOf(final int ordinal) {
        return BlockCompressedFilePointerUtil.makeFilePointer(
                firstBlockAddress + (long) (ordinal / RECORDS_PER_BLOCK) * COMPRESSED_BLOCK_BYTES,
                (ordinal % RECORDS_PER_BLOCK) * RECORD_BYTES);
    }

    /** Compressed size of the blocks used so far, i.e. where a following part would start. */
    long compressedLength() {
        return (long) ((records.size() + RECORDS_PER_BLOCK - 1) / RECORDS_PER_BLOCK) * COMPRESSED_BLOCK_BYTES;
    }

    List<Rec> records() {
        return records;
    }

    /** Indexes the records under the given binning scheme. */
    BinningIndex index(final int minShift, final int depth, final int referenceCount) {
        final BinningIndex.Builder builder = new BinningIndex.Builder(minShift, depth);
        for (final Rec rec : records) {
            builder.add(rec.referenceIndex(), rec.start(), rec.end(), rec.chunkStart(), rec.chunkEnd());
        }
        return builder.build(referenceCount);
    }

    /** Asserts that the span holds every record overlapping the query, and returns how many there were. */
    static int assertSpanCoversOverlaps(
            final List<Rec> records, final BAMFileSpan span, final int referenceIndex, final int start, final int end) {
        int overlapping = 0;
        for (final Rec rec : records) {
            if (!rec.overlaps(referenceIndex, start, end)) continue;
            overlapping++;
            boolean covered = false;
            for (final Chunk chunk : span.getChunks()) {
                covered |= Long.compareUnsigned(chunk.getChunkStart(), rec.chunkStart()) <= 0
                        && Long.compareUnsigned(rec.chunkEnd(), chunk.getChunkEnd()) <= 0;
            }
            Assert.assertTrue(
                    covered,
                    String.format("%s overlaps %d:%d-%d but is outside %s", rec, referenceIndex, start, end, span));
        }
        return overlapping;
    }
}
