package htsjdk.index;

import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hierarchical binning index over a BGZF-compressed, coordinate-sorted file that BAI, TBI and CSI all
 * serialise: per reference, a set of bins each listing the file chunks that hold the records assigned to it.
 *
 * <p>The binning scheme is described by two numbers. The smallest bins span {@code 2^minShift} bases and there
 * are {@code depth} further levels above them, each bin spanning eight bins of the level below, up to the single
 * bin 0 that spans {@code 2^(minShift + 3 * depth)} bases. BAI and TBI fix these at 14 and 5; CSI stores them. A
 * record goes in the smallest bin that contains it entirely.
 *
 * <p>The index is format-neutral: it knows nothing of sequence names, tabix columns or file headers, and
 * addresses references by ordinal. Instances are immutable.
 */
public final class BinningIndex implements HtsQueryIndex {
    /** The {@code minShift} of BAI and TBI. */
    public static final int BAI_MIN_SHIFT = 14;
    /** The {@code depth} of BAI and TBI. */
    public static final int BAI_DEPTH = 5;

    // Deeper than this and bin numbers no longer fit in an int. htslib applies the same limit.
    private static final int MAX_DEPTH = 9;

    private static final Comparator<long[]> BY_UNSIGNED_START = (a, b) -> Long.compareUnsigned(a[0], b[0]);

    private final int minShift;
    private final int depth;
    private final ReferenceBins[] references;

    /**
     * @param minShift log2 of the span of the smallest bins
     * @param depth number of bin levels above the smallest bins
     * @param references one entry per reference, in reference order
     */
    BinningIndex(final int minShift, final int depth, final List<ReferenceBins> references) {
        validateGeometry(minShift, depth);
        this.minShift = minShift;
        this.depth = depth;
        this.references = references.toArray(new ReferenceBins[0]);
    }

    private static void validateGeometry(final int minShift, final int depth) {
        if (minShift < 1 || depth < 1 || depth > MAX_DEPTH || minShift + 3 * depth > 62) {
            throw new IllegalArgumentException(
                    String.format("Unsupported binning scheme: minShift=%d, depth=%d", minShift, depth));
        }
    }

    public int getMinShift() {
        return minShift;
    }

    public int getDepth() {
        return depth;
    }

    public int getReferenceCount() {
        return references.length;
    }

    /**
     * @param referenceIndex ordinal of the reference
     * @return the reference's bins; never null, but {@link ReferenceBins#isEmpty() empty} if nothing is indexed
     *     on the reference
     */
    public ReferenceBins getReference(final int referenceIndex) {
        return references[referenceIndex];
    }

    /**
     * @return the number of bases the binning scheme can address, i.e. the span of bin 0
     */
    public long getMaxPosition() {
        return maxPosition(minShift, depth);
    }

    private static long maxPosition(final int minShift, final int depth) {
        return 1L << (minShift + 3 * depth);
    }

    /** The number of the first bin at a level, where level 0 is bin 0 and level {@code depth} the smallest bins. */
    private static int firstBinOfLevel(final int level) {
        return ((1 << (3 * level)) - 1) / 7;
    }

    /** The pseudo-bin, one past the last real bin, in which samtools and tabix store per-reference metadata. */
    private static int metadataBin(final int depth) {
        return firstBinOfLevel(depth + 1) + 1;
    }

    /**
     * The smallest bin that wholly contains a region.
     *
     * @param begin 0-based inclusive start
     * @param endExclusive 0-based exclusive end
     */
    private static int binFor(final int begin, final int endExclusive, final int minShift, final int depth) {
        final int last = endExclusive - 1;
        for (int level = depth, shift = minShift; level > 0; level--, shift += 3) {
            if (begin >> shift == last >> shift) {
                return firstBinOfLevel(level) + (begin >> shift);
            }
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A start of zero or less means the start of the reference, and an end of zero or less its end. The
     * chunks returned are in file order and do not overlap; chunks that start within the BGZF block another
     * ends in are joined, since one read serves both.
     */
    @Override
    public BAMFileSpan getSpanOverlapping(final int referenceIndex, final int start, final int end) {
        if (referenceIndex < 0 || referenceIndex >= references.length) {
            return new BAMFileSpan();
        }
        final ReferenceBins reference = references[referenceIndex];
        final long maxPosition = getMaxPosition();
        final long begin = Math.max(start, 1) - 1;
        final long endExclusive = end <= 0 ? maxPosition : Math.min(end, maxPosition);
        if (begin >= endExclusive) {
            return new BAMFileSpan();
        }

        // No record overlapping the query can start before the first record overlapping the query's first
        // window, so chunks that end at or before that record's offset cannot contribute.
        final long[] linearIndex = reference.linearIndex();
        final long minimumOffset =
                linearIndex.length == 0 ? 0 : linearIndex[(int) Math.min(begin >> minShift, linearIndex.length - 1)];

        final int[] binNumbers = reference.binNumbers();
        final long[][] binChunks = reference.binChunks();
        final List<long[]> candidates = new ArrayList<>();
        for (int level = 0, shift = minShift + 3 * depth; level <= depth; level++, shift -= 3) {
            final int firstBin = firstBinOfLevel(level) + (int) (begin >> shift);
            final int lastBin = firstBinOfLevel(level) + (int) ((endExclusive - 1) >> shift);
            int i = Arrays.binarySearch(binNumbers, firstBin);
            if (i < 0) i = -i - 1;
            for (; i < binNumbers.length && binNumbers[i] <= lastBin; i++) {
                final long[] offsets = binChunks[i];
                for (int j = 0; j < offsets.length; j += 2) {
                    if (Long.compareUnsigned(offsets[j + 1], minimumOffset) > 0) {
                        candidates.add(new long[] {offsets[j], offsets[j + 1]});
                    }
                }
            }
        }
        return new BAMFileSpan(coalesce(candidates));
    }

    /**
     * Sorts chunks into file order, drops those contained in another, and joins those that overlap or that
     * start in the BGZF block the previous one ends in.
     */
    private static List<Chunk> coalesce(final List<long[]> candidates) {
        candidates.sort(BY_UNSIGNED_START);
        final List<Chunk> chunks = new ArrayList<>(candidates.size());
        long[] current = null;
        for (final long[] next : candidates) {
            if (current == null) {
                current = next;
                continue;
            }
            final boolean contained = Long.compareUnsigned(next[1], current[1]) <= 0;
            final boolean startsWithinCurrentsLastBlock = BlockCompressedFilePointerUtil.getBlockAddress(next[0])
                    <= BlockCompressedFilePointerUtil.getBlockAddress(current[1]);
            if (contained) {
                continue;
            } else if (startsWithinCurrentsLastBlock) {
                current[1] = next[1];
            } else {
                chunks.add(new Chunk(current[0], current[1]));
                current = next;
            }
        }
        if (current != null) {
            chunks.add(new Chunk(current[0], current[1]));
        }
        return chunks;
    }

    /** A binning index holds no resources; this does nothing. */
    @Override
    public void close() {}

    /**
     * Reads the per-reference layout that BAI and TBI share: for each reference its bins, then its linear
     * index. The caller has already consumed the format's own header, from which the arguments come.
     *
     * @param codec positioned at the first reference
     * @param referenceCount number of references to read
     * @param minShift log2 of the span of the smallest bins
     * @param depth number of bin levels above the smallest bins
     */
    public static BinningIndex readBaiLayout(
            final BinaryCodec codec, final int referenceCount, final int minShift, final int depth) {
        validateGeometry(minShift, depth);
        final int metadataBin = metadataBin(depth);
        final List<ReferenceBins> references = new ArrayList<>(referenceCount);
        for (int referenceIndex = 0; referenceIndex < referenceCount; referenceIndex++) {
            final int binCount = codec.readInt();
            final List<long[]> bins = new ArrayList<>(binCount);
            final int[] binNumbers = new int[binCount];
            ReferenceBins.Metadata metadata = null;
            int realBins = 0;
            for (int i = 0; i < binCount; i++) {
                final int binNumber = codec.readInt();
                final long[] offsets = new long[2 * codec.readInt()];
                for (int j = 0; j < offsets.length; j++) {
                    offsets[j] = codec.readLong();
                }
                if (binNumber == metadataBin) {
                    if (offsets.length != 4) {
                        throw new IllegalArgumentException(String.format(
                                "Metadata bin of reference %d has %d chunks; expected 2",
                                referenceIndex, offsets.length / 2));
                    }
                    metadata = new ReferenceBins.Metadata(offsets[0], offsets[1], offsets[2], offsets[3]);
                } else if (offsets.length > 0) {
                    binNumbers[realBins++] = binNumber;
                    bins.add(offsets);
                }
            }
            final long[] linearIndex = new long[codec.readInt()];
            for (int i = 0; i < linearIndex.length; i++) {
                linearIndex[i] = codec.readLong();
            }
            references.add(sortedByBinNumber(
                    Arrays.copyOf(binNumbers, realBins), bins.toArray(new long[0][]), linearIndex, metadata));
        }
        return new BinningIndex(minShift, depth, references);
    }

    /** Files need not list bins in order (htslib writes them in hash order); the in-memory form must. */
    private static ReferenceBins sortedByBinNumber(
            final int[] binNumbers,
            final long[][] binChunks,
            final long[] linearIndex,
            final ReferenceBins.Metadata metadata) {
        final Integer[] order = new Integer[binNumbers.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> binNumbers[i]));
        final int[] sortedNumbers = new int[binNumbers.length];
        final long[][] sortedChunks = new long[binNumbers.length][];
        for (int i = 0; i < order.length; i++) {
            sortedNumbers[i] = binNumbers[order[i]];
            sortedChunks[i] = binChunks[order[i]];
        }
        return new ReferenceBins(sortedNumbers, sortedChunks, linearIndex, metadata);
    }

    /**
     * Writes the per-reference layout that BAI and TBI share; the counterpart of {@link #readBaiLayout}. The
     * caller writes the format's own header first.
     */
    public void writeBaiLayout(final BinaryCodec codec) {
        final int metadataBin = metadataBin(depth);
        for (final ReferenceBins reference : references) {
            final int[] binNumbers = reference.binNumbers();
            final long[][] binChunks = reference.binChunks();
            final ReferenceBins.Metadata metadata = reference.getMetadata().orElse(null);
            codec.writeInt(binNumbers.length + (metadata == null ? 0 : 1));
            for (int i = 0; i < binNumbers.length; i++) {
                codec.writeInt(binNumbers[i]);
                codec.writeInt(binChunks[i].length / 2);
                for (final long offset : binChunks[i]) {
                    codec.writeLong(offset);
                }
            }
            if (metadata != null) {
                codec.writeInt(metadataBin);
                codec.writeInt(2);
                codec.writeLong(metadata.firstOffset());
                codec.writeLong(metadata.lastOffset());
                codec.writeLong(metadata.mappedCount());
                codec.writeLong(metadata.unmappedCount());
            }
            final long[] linearIndex = reference.linearIndex();
            codec.writeInt(linearIndex.length);
            for (final long offset : linearIndex) {
                codec.writeLong(offset);
            }
        }
    }

    /**
     * Merges the indexes of consecutive, headerless parts of one file into the index of their concatenation.
     *
     * @param parts the part indexes, in file order; all must share a binning scheme and reference count
     * @param partOffsets for each part, the byte offset in the concatenated file at which the part starts
     */
    public static BinningIndex merge(final List<BinningIndex> parts, final long[] partOffsets) {
        if (parts.isEmpty() || parts.size() != partOffsets.length) {
            throw new IllegalArgumentException("Need at least one part, and one offset per part");
        }
        final BinningIndex first = parts.get(0);
        for (final BinningIndex part : parts) {
            if (part.minShift != first.minShift
                    || part.depth != first.depth
                    || part.references.length != first.references.length) {
                throw new IllegalArgumentException(
                        "Cannot merge indexes with different binning schemes or reference counts");
            }
        }
        final List<ReferenceBins> merged = new ArrayList<>(first.references.length);
        for (int referenceIndex = 0; referenceIndex < first.references.length; referenceIndex++) {
            final Builder.ReferenceAccumulator accumulator = new Builder.ReferenceAccumulator();
            long[] linearIndex = new long[0];
            ReferenceBins.Metadata metadata = null;
            for (int p = 0; p < parts.size(); p++) {
                final ReferenceBins reference = parts.get(p).references[referenceIndex];
                final long partOffset = partOffsets[p];
                final int[] binNumbers = reference.binNumbers();
                for (int i = 0; i < binNumbers.length; i++) {
                    final long[] offsets = reference.binChunks()[i];
                    for (int j = 0; j < offsets.length; j += 2) {
                        // Parts are in file order, so within a bin chunks still arrive in file order.
                        accumulator.addChunk(
                                binNumbers[i],
                                BlockCompressedFilePointerUtil.shift(offsets[j], partOffset),
                                BlockCompressedFilePointerUtil.shift(offsets[j + 1], partOffset));
                    }
                }
                // For a window covered by several parts the earliest part has the smallest offset.
                final long[] partLinearIndex = reference.linearIndex();
                if (partLinearIndex.length > linearIndex.length) {
                    final int alreadyCovered = linearIndex.length;
                    linearIndex = Arrays.copyOf(linearIndex, partLinearIndex.length);
                    for (int w = alreadyCovered; w < partLinearIndex.length; w++) {
                        linearIndex[w] = BlockCompressedFilePointerUtil.shift(partLinearIndex[w], partOffset);
                    }
                }
                metadata = mergeMetadata(metadata, reference.getMetadata().orElse(null), partOffset);
            }
            merged.add(accumulator.toReferenceBins(linearIndex, metadata));
        }
        return new BinningIndex(first.minShift, first.depth, merged);
    }

    private static ReferenceBins.Metadata mergeMetadata(
            final ReferenceBins.Metadata soFar, final ReferenceBins.Metadata next, final long nextOffset) {
        if (next == null) return soFar;
        final long firstOffset = BlockCompressedFilePointerUtil.shift(next.firstOffset(), nextOffset);
        final long lastOffset = BlockCompressedFilePointerUtil.shift(next.lastOffset(), nextOffset);
        if (soFar == null) {
            return new ReferenceBins.Metadata(firstOffset, lastOffset, next.mappedCount(), next.unmappedCount());
        }
        return new ReferenceBins.Metadata(
                soFar.firstOffset(),
                lastOffset,
                soFar.mappedCount() + next.mappedCount(),
                soFar.unmappedCount() + next.unmappedCount());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof BinningIndex)) return false;
        final BinningIndex that = (BinningIndex) o;
        return minShift == that.minShift && depth == that.depth && Arrays.equals(references, that.references);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * minShift + depth) + Arrays.hashCode(references);
    }

    /**
     * Builds a {@link BinningIndex} from records presented in file order.
     */
    public static final class Builder {
        private static final long UNSET = -1;

        private final int minShift;
        private final int depth;
        private final long maxPosition;
        private final List<ReferenceBins> finished = new ArrayList<>();

        private int currentReference = -1;
        private ReferenceAccumulator accumulator;
        private long[] linearIndex;
        private int windowCount;

        /**
         * @param minShift log2 of the span of the smallest bins
         * @param depth number of bin levels above the smallest bins
         */
        public Builder(final int minShift, final int depth) {
            validateGeometry(minShift, depth);
            this.minShift = minShift;
            this.depth = depth;
            this.maxPosition = maxPosition(minShift, depth);
        }

        /**
         * Adds a record. Records must arrive in file order, so reference ordinals never decrease.
         *
         * @param referenceIndex ordinal of the record's reference
         * @param start 1-based inclusive start
         * @param end 1-based inclusive end; a record whose end precedes its start is indexed as the single base
         *     at its start
         * @param chunkStart virtual offset of the record
         * @param chunkEnd virtual offset just past the record
         */
        public void add(
                final int referenceIndex, final int start, final int end, final long chunkStart, final long chunkEnd) {
            if (referenceIndex != currentReference) {
                advanceTo(referenceIndex);
            }
            if (start < 1) {
                throw new IllegalArgumentException("Record start must be at least 1, but was " + start);
            }
            final int begin = start - 1;
            final int endExclusive = Math.max(end, start);
            if (endExclusive > maxPosition) {
                throw new IllegalArgumentException(String.format(
                        "Record at %d-%d on reference %d lies beyond %d, the last position an index with "
                                + "minShift=%d and depth=%d can address",
                        start, end, referenceIndex, maxPosition, minShift, depth));
            }
            accumulator.addChunk(binFor(begin, endExclusive, minShift, depth), chunkStart, chunkEnd);

            final int firstWindow = begin >> minShift;
            final int lastWindow = (endExclusive - 1) >> minShift;
            if (lastWindow >= linearIndex.length) {
                final int oldLength = linearIndex.length;
                linearIndex = Arrays.copyOf(linearIndex, Math.max(lastWindow + 1, 2 * oldLength));
                Arrays.fill(linearIndex, oldLength, linearIndex.length, UNSET);
            }
            for (int window = firstWindow; window <= lastWindow; window++) {
                if (linearIndex[window] == UNSET || chunkStart < linearIndex[window]) {
                    linearIndex[window] = chunkStart;
                }
            }
            windowCount = Math.max(windowCount, lastWindow + 1);
        }

        private void advanceTo(final int referenceIndex) {
            if (referenceIndex < currentReference) {
                throw new IllegalArgumentException(String.format(
                        "Reference %d added after reference %d; records must be in file order",
                        referenceIndex, currentReference));
            }
            finishCurrentReference();
            while (finished.size() < referenceIndex) {
                finished.add(ReferenceBins.EMPTY);
            }
            currentReference = referenceIndex;
            accumulator = new ReferenceAccumulator();
            linearIndex = new long[16];
            Arrays.fill(linearIndex, UNSET);
            windowCount = 0;
        }

        private void finishCurrentReference() {
            if (accumulator == null) return;
            // Windows that no record overlaps take the offset of the nearest preceding window that one does,
            // as samtools does, so that every window gives a usable lower bound.
            long previous = 0;
            for (int window = 0; window < windowCount; window++) {
                if (linearIndex[window] == UNSET) {
                    linearIndex[window] = previous;
                } else {
                    previous = linearIndex[window];
                }
            }
            finished.add(accumulator.toReferenceBins(Arrays.copyOf(linearIndex, windowCount), null));
            accumulator = null;
        }

        /**
         * @param referenceCount number of references the index is to cover; references after the last one added
         *     to are empty
         */
        public BinningIndex build(final int referenceCount) {
            finishCurrentReference();
            if (referenceCount < finished.size()) {
                throw new IllegalArgumentException(String.format(
                        "Records were added for %d references but the index is to cover only %d",
                        finished.size(), referenceCount));
            }
            while (finished.size() < referenceCount) {
                finished.add(ReferenceBins.EMPTY);
            }
            return new BinningIndex(minShift, depth, finished);
        }

        /** Collects the chunks of one reference's bins. Within a bin, chunks must be added in file order. */
        private static final class ReferenceAccumulator {
            private final Map<Integer, ChunkList> bins = new HashMap<>();
            // Consecutive records nearly always share a bin, so remembering the last one skips most lookups.
            private int lastBinNumber = -1;
            private ChunkList lastBin;

            void addChunk(final int binNumber, final long chunkStart, final long chunkEnd) {
                if (binNumber != lastBinNumber) {
                    lastBin = bins.computeIfAbsent(binNumber, unused -> new ChunkList());
                    lastBinNumber = binNumber;
                }
                lastBin.add(chunkStart, chunkEnd);
            }

            ReferenceBins toReferenceBins(final long[] linearIndex, final ReferenceBins.Metadata metadata) {
                final int[] binNumbers = bins.keySet().stream()
                        .mapToInt(Integer::intValue)
                        .sorted()
                        .toArray();
                final long[][] binChunks = new long[binNumbers.length][];
                for (int i = 0; i < binNumbers.length; i++) {
                    binChunks[i] = bins.get(binNumbers[i]).toArray();
                }
                return new ReferenceBins(binNumbers, binChunks, linearIndex, metadata);
            }
        }

        /** A growable list of chunks, flattened to {start0, end0, start1, end1, ...}. */
        private static final class ChunkList {
            private long[] offsets = new long[4];
            private int size;

            void add(final long chunkStart, final long chunkEnd) {
                // A chunk starting in or next to the BGZF block the previous one ends in costs no extra seek
                // to read as one, so they are stored as one.
                if (size > 0
                        && BlockCompressedFilePointerUtil.areInSameOrAdjacentBlocks(offsets[size - 1], chunkStart)) {
                    offsets[size - 1] = chunkEnd;
                    return;
                }
                if (size == offsets.length) {
                    offsets = Arrays.copyOf(offsets, 2 * size);
                }
                offsets[size++] = chunkStart;
                offsets[size++] = chunkEnd;
            }

            long[] toArray() {
                return Arrays.copyOf(offsets, size);
            }
        }
    }
}
