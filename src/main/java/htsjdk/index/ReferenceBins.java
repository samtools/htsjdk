package htsjdk.index;

import htsjdk.samtools.Chunk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The part of a {@link BinningIndex} that describes one reference sequence: its bins, its linear index if the
 * index has one, and its metadata pseudo-bin if the index carries one.
 *
 * <p>Only bins that hold chunks are stored, so the footprint follows the data rather than the index geometry.
 * Instances are immutable.
 */
public final class ReferenceBins {

    /**
     * The contents of the metadata pseudo-bin that samtools and tabix write for each reference.
     *
     * @param firstOffset virtual offset of the first record on the reference
     * @param lastOffset virtual offset just past the last record on the reference
     * @param mappedCount number of mapped records on the reference
     * @param unmappedCount number of unmapped records placed on the reference
     */
    public record Metadata(long firstOffset, long lastOffset, long mappedCount, long unmappedCount) {}

    static final ReferenceBins EMPTY = new ReferenceBins(new int[0], new long[0][], new long[0], new long[0], null, 1);

    // Bin numbers in ascending order, so a level's bins can be range-scanned after a binary search.
    private final int[] binNumbers;
    // binChunks[i] holds the chunks of binNumbers[i] as {start0, end0, start1, end1, ...} virtual offsets.
    private final long[][] binChunks;
    // loffsets[i] is the smallest virtual offset of any record overlapping the first window of binNumbers[i].
    // Derived from the linear index on first use when the file did not store them, since a TBI never needs them.
    private volatile long[] loffsets;
    private final long[] linearIndex;
    private final Metadata metadata;
    private final int depth;

    /**
     * @param binNumbers bin numbers, strictly ascending
     * @param binChunks for each bin, its chunks flattened to {start0, end0, start1, end1, ...} virtual offsets
     * @param loffsets for each bin, the smallest virtual offset of any record overlapping the bin's first
     *     window of the smallest bin size, i.e. a lower bound for every record in the bin; or null to derive
     *     them from the linear index when first needed (all zero, the trivial bound, if that is empty too)
     * @param linearIndex for each window of the smallest bin size, the smallest virtual offset of any record
     *     overlapping the window; empty if the index has no linear index
     * @param metadata the metadata pseudo-bin, or null if there is none
     * @param depth the index's number of bin levels above the smallest bins, needed to derive loffsets
     */
    ReferenceBins(
            final int[] binNumbers,
            final long[][] binChunks,
            final long[] loffsets,
            final long[] linearIndex,
            final Metadata metadata,
            final int depth) {
        if (binNumbers.length != binChunks.length || (loffsets != null && binNumbers.length != loffsets.length)) {
            throw new IllegalArgumentException("binNumbers, binChunks and loffsets differ in length");
        }
        for (int i = 0; i < binNumbers.length; i++) {
            if (i > 0 && binNumbers[i] <= binNumbers[i - 1]) {
                throw new IllegalArgumentException("Bin " + binNumbers[i] + " is duplicated or out of order");
            }
            if (binChunks[i].length == 0 || binChunks[i].length % 2 != 0) {
                throw new IllegalArgumentException("Bin " + binNumbers[i] + " has a malformed chunk list");
            }
        }
        this.binNumbers = binNumbers;
        this.binChunks = binChunks;
        this.loffsets = loffsets;
        this.linearIndex = linearIndex;
        this.metadata = metadata;
        this.depth = depth;
    }

    /**
     * @return true if the reference has no bins, no linear index and no metadata
     */
    public boolean isEmpty() {
        return binNumbers.length == 0 && linearIndex.length == 0 && metadata == null;
    }

    /**
     * @return the number of bins that hold chunks
     */
    public int getBinCount() {
        return binNumbers.length;
    }

    /**
     * @param ordinal position of the bin among this reference's bins, which are ordered by bin number
     * @return the bin's number in the index's binning scheme
     */
    public int getBinNumber(final int ordinal) {
        return binNumbers[ordinal];
    }

    /**
     * @param ordinal position of the bin among this reference's bins, which are ordered by bin number
     * @return the bin's chunks, in file order
     */
    public List<Chunk> getChunks(final int ordinal) {
        final long[] offsets = binChunks[ordinal];
        final List<Chunk> chunks = new ArrayList<>(offsets.length / 2);
        for (int i = 0; i < offsets.length; i += 2) {
            chunks.add(new Chunk(offsets[i], offsets[i + 1]));
        }
        return chunks;
    }

    /**
     * @param ordinal position of the bin among this reference's bins, which are ordered by bin number
     * @return the smallest virtual offset of any record overlapping the bin's first window; every record in the
     *     bin lies at or after it
     */
    public long getLoffset(final int ordinal) {
        return loffsets()[ordinal];
    }

    /**
     * @return a copy of the linear index; empty if there is none
     */
    public long[] getLinearIndex() {
        return linearIndex.clone();
    }

    /**
     * @return the metadata pseudo-bin, if the index carries one for this reference
     */
    public Optional<Metadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    // The raw arrays, for the index's query and serialisation code; callers must not modify them.

    int[] binNumbers() {
        return binNumbers;
    }

    long[][] binChunks() {
        return binChunks;
    }

    long[] loffsets() {
        long[] result = loffsets;
        if (result == null) {
            result = new long[binNumbers.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = BinningIndex.loffsetFromLinearIndex(binNumbers[i], linearIndex, depth);
            }
            loffsets = result; // idempotent, so a race here only repeats the work
        }
        return result;
    }

    long[] linearIndex() {
        return linearIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ReferenceBins)) return false;
        final ReferenceBins that = (ReferenceBins) o;
        return Arrays.equals(binNumbers, that.binNumbers)
                && Arrays.deepEquals(binChunks, that.binChunks)
                && Arrays.equals(loffsets(), that.loffsets())
                && Arrays.equals(linearIndex, that.linearIndex)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(binNumbers);
        result = 31 * result + Arrays.deepHashCode(binChunks);
        result = 31 * result + Arrays.hashCode(loffsets());
        result = 31 * result + Arrays.hashCode(linearIndex);
        result = 31 * result + Objects.hashCode(metadata);
        return result;
    }
}
