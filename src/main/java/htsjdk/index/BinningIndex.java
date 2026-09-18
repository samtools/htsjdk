package htsjdk.index;

import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.IntToLongFunction;

/**
 * The hierarchical binning index over a BGZF-compressed, coordinate-sorted file that BAI, TBI and CSI all
 * serialise: per reference, a set of bins each listing the file chunks that hold the records assigned to it.
 *
 * <p>The binning scheme is described by two numbers. The smallest bins span {@code 2^minShift} bases and there
 * are {@code depth} further levels above them, each bin spanning eight bins of the level below, up to the single
 * bin 0 that spans {@code 2^(minShift + 3 * depth)} bases. BAI and TBI fix these at 14 and 5; CSI stores them. A
 * record goes in the smallest bin that contains it entirely.
 *
 * <p>Two devices let a query skip chunks that lie before the records it wants: the linear index, which BAI and
 * TBI store per reference, and each bin's {@code loffset}, which CSI stores instead. An index built here has both;
 * one loaded from a file has whichever the file had, with {@code loffset}s derived from a linear index.
 *
 * <p>The index is format-neutral: it knows nothing of sequence names, tabix columns or file headers, and
 * addresses references by ordinal. Instances are immutable.
 */
public final class BinningIndex implements HtsQueryIndex {
    /** The {@code minShift} of BAI and TBI. */
    public static final int BAI_MIN_SHIFT = 14;
    /** The {@code depth} of BAI and TBI. */
    public static final int BAI_DEPTH = 5;

    /** A binning scheme. */
    public record Geometry(int minShift, int depth) {}

    /** What a CSI file holds besides the index: the format-specific block whose meaning CSI leaves to the format. */
    public record CsiContents(byte[] aux, BinningIndex index) {}

    /** The first four bytes of a CSI file. */
    public static final byte[] CSI_MAGIC = {'C', 'S', 'I', 1};

    // Deeper than this and bin numbers no longer fit in an int. htslib applies the same limit.
    private static final int MAX_DEPTH = 9;
    // htslib's TBX_MAX_SHIFT: tabix starts from a scheme that spans 2^31 and deepens it as the data demands.
    private static final int TABIX_MAX_SHIFT = 31;
    // Slack htslib adds to the longest sequence before choosing a scheme for it.
    private static final int SEQUENCE_LENGTH_SLACK = 256;

    private static final long NO_COORDINATE_COUNT_ABSENT = -1;

    private static final Comparator<long[]> BY_UNSIGNED_START = (a, b) -> Long.compareUnsigned(a[0], b[0]);

    private final int minShift;
    private final int depth;
    private final ReferenceBins[] references;
    private final long noCoordinateCount;

    /**
     * @param minShift log2 of the span of the smallest bins
     * @param depth number of bin levels above the smallest bins
     * @param references one entry per reference, in reference order
     * @param noCoordinateCount number of records with no position, or {@link #NO_COORDINATE_COUNT_ABSENT}
     */
    BinningIndex(
            final int minShift, final int depth, final List<ReferenceBins> references, final long noCoordinateCount) {
        validateGeometry(minShift, depth);
        this.minShift = minShift;
        this.depth = depth;
        this.references = references.toArray(new ReferenceBins[0]);
        this.noCoordinateCount = noCoordinateCount;
    }

    /** Rejects a binning scheme whose bin numbers or positions would overflow, or that has no levels at all. */
    private static void validateGeometry(final int minShift, final int depth) {
        if (minShift < 1 || depth < 1 || depth > MAX_DEPTH || minShift + 3 * depth > 62) {
            throw new IllegalArgumentException(
                    String.format("Unsupported binning scheme: minShift=%d, depth=%d", minShift, depth));
        }
    }

    /**
     * The binning scheme htslib's tabix chooses for a CSI index: starting from the shallowest scheme that spans
     * 2^31, it adds levels until the longest sequence fits (with a little slack), and if nine levels are not
     * enough, widens the smallest bins instead. Without a known longest sequence it uses a fixed deep scheme.
     *
     * @param minShift the requested log2 of the span of the smallest bins
     * @param longestSequence the longest sequence to be indexed, or 0 if not known
     */
    public static Geometry csiGeometry(final int minShift, final long longestSequence) {
        if (minShift < 1) {
            throw new IllegalArgumentException("minShift must be at least 1, but was " + minShift);
        }
        if (longestSequence <= 0) {
            final int depth = minShift < 10 ? MAX_DEPTH : minShift < 25 ? MAX_DEPTH - (minShift - 10) / 3 : 4;
            return new Geometry(minShift, depth);
        }
        final long needed = longestSequence + SEQUENCE_LENGTH_SLACK;
        int shift = minShift;
        int depth = Math.min(MAX_DEPTH, Math.max(1, (TABIX_MAX_SHIFT - shift + 2) / 3));
        if (needed <= maxPosition(shift, MAX_DEPTH)) {
            while (needed > maxPosition(shift, depth)) depth++;
        } else {
            depth = MAX_DEPTH;
            while (needed > maxPosition(shift, depth)) shift++;
        }
        return new Geometry(shift, depth);
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
     * @return the number of records that have no position, if the index records it
     */
    public OptionalLong getNoCoordinateCount() {
        return noCoordinateCount == NO_COORDINATE_COUNT_ABSENT
                ? OptionalLong.empty()
                : OptionalLong.of(noCoordinateCount);
    }

    /**
     * @return the number of bases the binning scheme can address, i.e. the span of bin 0
     */
    public long getMaxPosition() {
        return maxPosition(minShift, depth);
    }

    /** The span of bin 0, i.e. one past the largest 0-based position the scheme can address. */
    private static long maxPosition(final int minShift, final int depth) {
        return 1L << (minShift + 3 * depth);
    }

    /** The number of the first bin at a level, where level 0 is bin 0 and level {@code depth} the smallest bins. */
    private static int firstBinOfLevel(final int level) {
        return ((1 << (3 * level)) - 1) / 7;
    }

    /**
     * The level of a bin, 0 for bin 0 and {@code depth} for the smallest bins. Level {@code l} starts at bin
     * {@code (8^l - 1) / 7}, so {@code 7 * bin + 1} first reaches {@code 8^l} there and the level is the
     * highest set bit of that, in units of three.
     */
    private static int levelOf(final int binNumber) {
        return (31 - Integer.numberOfLeadingZeros(7 * binNumber + 1)) / 3;
    }

    /** The bin one level up that contains a bin. */
    private static int parentOf(final int binNumber) {
        return (binNumber - 1) >> 3;
    }

    /** The window of the smallest bin size at which a bin starts. */
    private static int firstWindowOf(final int binNumber, final int depth) {
        final int level = levelOf(binNumber);
        return (binNumber - firstBinOfLevel(level)) << (3 * (depth - level));
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
        final long minimumOffset = minimumOffset(reference, (int) (begin >> minShift));

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
     * A lower bound on the offset of any record overlapping a window: from the linear index when there is one,
     * otherwise the {@code loffset} of the nearest bin at or before the window, searching leftwards and then
     * upwards as htslib does.
     */
    private long minimumOffset(final ReferenceBins reference, final int window) {
        final long[] linearIndex = reference.linearIndex();
        if (linearIndex.length > 0) {
            return linearIndex[Math.min(window, linearIndex.length - 1)];
        }
        final int[] binNumbers = reference.binNumbers();
        int bin = firstBinOfLevel(depth) + window;
        while (bin > 0) {
            final int i = Arrays.binarySearch(binNumbers, bin);
            if (i >= 0) {
                return reference.loffsets()[i];
            }
            final int firstSibling = (parentOf(bin) << 3) + 1;
            bin = bin > firstSibling ? bin - 1 : parentOf(bin);
        }
        final int i = Arrays.binarySearch(binNumbers, 0);
        return i >= 0 ? reference.loffsets()[i] : 0;
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
     * index; then the count of records without a position, if the file has it. The caller has already consumed
     * the format's own header, from which the arguments come.
     *
     * @param codec positioned at the first reference
     * @param referenceCount number of references to read
     * @param minShift log2 of the span of the smallest bins
     * @param depth number of bin levels above the smallest bins
     */
    public static BinningIndex readBaiLayout(
            final BinaryCodec codec, final int referenceCount, final int minShift, final int depth) {
        validateGeometry(minShift, depth);
        return readReferences(codec, referenceCount, minShift, depth, false);
    }

    /**
     * Reads a whole CSI file, from its magic number to its trailing count of records without a position.
     *
     * @param codec positioned at the magic number
     * @throws IllegalArgumentException if the magic number is not CSI's
     */
    public static CsiContents readCsi(final BinaryCodec codec) {
        final byte[] magic = new byte[CSI_MAGIC.length];
        codec.readBytes(magic);
        if (!Arrays.equals(magic, CSI_MAGIC)) {
            throw new IllegalArgumentException("Not a CSI index: magic number is " + Arrays.toString(magic));
        }
        return readCsiAfterMagic(codec);
    }

    /**
     * Reads a CSI file whose magic number the caller has already consumed, typically to tell formats apart.
     *
     * @param codec positioned just after the magic number
     */
    public static CsiContents readCsiAfterMagic(final BinaryCodec codec) {
        final int minShift = codec.readInt();
        final int depth = codec.readInt();
        validateGeometry(minShift, depth);
        final byte[] aux = new byte[codec.readInt()];
        codec.readBytes(aux);
        final int referenceCount = codec.readInt();
        return new CsiContents(aux, readReferences(codec, referenceCount, minShift, depth, true));
    }

    /**
     * The body common to every format: per reference its bins, with an {@code loffset} per bin in CSI and a
     * linear index after the bins otherwise, then the optional trailing count of records without a position.
     */
    private static BinningIndex readReferences(
            final BinaryCodec codec,
            final int referenceCount,
            final int minShift,
            final int depth,
            final boolean csiLayout) {
        final int metadataBin = metadataBin(depth);
        final List<ReferenceBins> references = new ArrayList<>(referenceCount);
        for (int referenceIndex = 0; referenceIndex < referenceCount; referenceIndex++) {
            final int binCount = codec.readInt();
            final int[] binNumbers = new int[binCount];
            final long[][] binChunks = new long[binCount][];
            final long[] loffsets = csiLayout ? new long[binCount] : null;
            ReferenceBins.Metadata metadata = null;
            int realBins = 0;
            for (int i = 0; i < binCount; i++) {
                final int binNumber = codec.readInt();
                final long loffset = csiLayout ? codec.readLong() : 0;
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
                    binNumbers[realBins] = binNumber;
                    binChunks[realBins] = offsets;
                    if (csiLayout) loffsets[realBins] = loffset;
                    realBins++;
                }
            }
            long[] linearIndex = new long[0];
            if (!csiLayout) {
                linearIndex = new long[codec.readInt()];
                for (int i = 0; i < linearIndex.length; i++) {
                    linearIndex[i] = codec.readLong();
                }
            }
            references.add(sortedByBinNumber(
                    Arrays.copyOf(binNumbers, realBins),
                    Arrays.copyOf(binChunks, realBins),
                    csiLayout ? Arrays.copyOf(loffsets, realBins) : null,
                    linearIndex,
                    metadata,
                    depth));
        }
        return new BinningIndex(minShift, depth, references, readNoCoordinateCount(codec));
    }

    /** The trailing count of records without a position, which every format allows a file to leave out. */
    private static long readNoCoordinateCount(final BinaryCodec codec) {
        final byte[] trailer = new byte[8];
        int read = 0;
        while (read < trailer.length) {
            final int n = codec.readBytesOrFewer(trailer, read, trailer.length - read);
            if (n <= 0) return NO_COORDINATE_COUNT_ABSENT;
            read += n;
        }
        return ByteBuffer.wrap(trailer).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    /**
     * Files need not list bins in order (htslib writes them in hash order); the in-memory form must.
     *
     * @param loffsets the bins' {@code loffset}s, or null for a layout without them, in which case they are derived
     *     from the linear index if ever needed
     */
    private static ReferenceBins sortedByBinNumber(
            final int[] binNumbers,
            final long[][] binChunks,
            final long[] loffsets,
            final long[] linearIndex,
            final ReferenceBins.Metadata metadata,
            final int depth) {
        final Integer[] order = new Integer[binNumbers.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> binNumbers[i]));
        final int[] sortedNumbers = new int[binNumbers.length];
        final long[][] sortedChunks = new long[binNumbers.length][];
        final long[] sortedLoffsets = loffsets == null ? null : new long[binNumbers.length];
        for (int i = 0; i < order.length; i++) {
            sortedNumbers[i] = binNumbers[order[i]];
            sortedChunks[i] = binChunks[order[i]];
            if (loffsets != null) sortedLoffsets[i] = loffsets[order[i]];
        }
        return new ReferenceBins(sortedNumbers, sortedChunks, sortedLoffsets, linearIndex, metadata, depth);
    }

    /** A bin's {@code loffset} is the linear-index entry for its first window, or 0 if the index ends before it. */
    static long loffsetFromLinearIndex(final int binNumber, final long[] linearIndex, final int depth) {
        final int window = firstWindowOf(binNumber, depth);
        return window < linearIndex.length ? linearIndex[window] : 0;
    }

    /**
     * Writes the per-reference layout that BAI and TBI share; the counterpart of {@link #readBaiLayout}. The
     * caller writes the format's own header first. The trailing count of records without a position is written
     * only if the index has one.
     */
    public void writeBaiLayout(final BinaryCodec codec) {
        writeReferences(codec, false);
        if (noCoordinateCount != NO_COORDINATE_COUNT_ABSENT) {
            codec.writeLong(noCoordinateCount);
        }
    }

    /**
     * Writes a whole CSI file; the counterpart of {@link #readCsi}.
     *
     * @param aux the format-specific block to store; empty for formats that need none
     */
    public void writeCsi(final BinaryCodec codec, final byte[] aux) {
        codec.writeBytes(CSI_MAGIC);
        codec.writeInt(minShift);
        codec.writeInt(depth);
        codec.writeInt(aux.length);
        codec.writeBytes(aux);
        codec.writeInt(references.length);
        writeReferences(codec, true);
        codec.writeLong(noCoordinateCount == NO_COORDINATE_COUNT_ABSENT ? 0 : noCoordinateCount);
    }

    /** The counterpart of {@link #readReferences}, without the trailer. */
    private void writeReferences(final BinaryCodec codec, final boolean csiLayout) {
        final int metadataBin = metadataBin(depth);
        for (final ReferenceBins reference : references) {
            final int[] binNumbers = reference.binNumbers();
            final long[][] binChunks = reference.binChunks();
            final ReferenceBins.Metadata metadata = reference.getMetadata().orElse(null);
            codec.writeInt(binNumbers.length + (metadata == null ? 0 : 1));
            for (int i = 0; i < binNumbers.length; i++) {
                codec.writeInt(binNumbers[i]);
                if (csiLayout) codec.writeLong(reference.loffsets()[i]);
                codec.writeInt(binChunks[i].length / 2);
                for (final long offset : binChunks[i]) {
                    codec.writeLong(offset);
                }
            }
            if (metadata != null) {
                codec.writeInt(metadataBin);
                if (csiLayout) codec.writeLong(0);
                codec.writeInt(2);
                codec.writeLong(metadata.firstOffset());
                codec.writeLong(metadata.lastOffset());
                codec.writeLong(metadata.mappedCount());
                codec.writeLong(metadata.unmappedCount());
            }
            if (!csiLayout) {
                final long[] linearIndex = reference.linearIndex();
                codec.writeInt(linearIndex.length);
                for (final long offset : linearIndex) {
                    codec.writeLong(offset);
                }
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
        long noCoordinateCount = NO_COORDINATE_COUNT_ABSENT;
        final List<ReferenceBins> merged = new ArrayList<>(first.references.length);
        for (int referenceIndex = 0; referenceIndex < first.references.length; referenceIndex++) {
            final Builder.ReferenceAccumulator accumulator = new Builder.ReferenceAccumulator();
            final Map<Integer, Long> loffsets = new HashMap<>();
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
                    // The earliest part to hold a bin has the smallest offset for it. Only needed when the parts
                    // have no linear index to derive loffsets from.
                    if (reference.linearIndex().length == 0) {
                        loffsets.putIfAbsent(
                                binNumbers[i],
                                BlockCompressedFilePointerUtil.shift(reference.loffsets()[i], partOffset));
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
            merged.add(accumulator.toReferenceBins(
                    linearIndex.length > 0 ? null : loffsets::get, linearIndex, metadata, first.depth));
        }
        // Absent unless some part has it; then the sum over the parts that do.
        for (final BinningIndex part : parts) {
            if (part.noCoordinateCount != NO_COORDINATE_COUNT_ABSENT) {
                noCoordinateCount = (noCoordinateCount == NO_COORDINATE_COUNT_ABSENT ? 0 : noCoordinateCount)
                        + part.noCoordinateCount;
            }
        }
        return new BinningIndex(first.minShift, first.depth, merged, noCoordinateCount);
    }

    /** Combines two parts' metadata: the earliest start, the latest end, and the counts summed. */
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
        return minShift == that.minShift
                && depth == that.depth
                && noCoordinateCount == that.noCoordinateCount
                && Arrays.equals(references, that.references);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * minShift + depth) + Long.hashCode(noCoordinateCount)) + Arrays.hashCode(references);
    }

    /**
     * Builds a {@link BinningIndex} from records presented in file order.
     */
    public static final class Builder {
        private static final long UNSET = -1;

        private final int minShift;
        private final int depth;
        private final long maxPosition;
        private final boolean forCsi;
        private final List<ReferenceBins> finished = new ArrayList<>();

        private int currentReference = -1;
        private ReferenceAccumulator accumulator;
        private long[] linearIndex;
        private int windowCount;
        private long recordCount;

        /**
         * @param minShift log2 of the span of the smallest bins
         * @param depth number of bin levels above the smallest bins
         */
        public Builder(final int minShift, final int depth) {
            this(minShift, depth, false);
        }

        /**
         * @param minShift log2 of the span of the smallest bins
         * @param depth number of bin levels above the smallest bins
         * @param forCsi whether the index is bound for a CSI file, which stores no linear index: then each bin's
         *     {@code loffset} is worked out the way htslib does, and each reference gets a metadata pseudo-bin
         *     holding its record count and the offsets of its first and last records, as tabix writes. Otherwise
         *     neither is computed unless asked for.
         */
        public Builder(final int minShift, final int depth, final boolean forCsi) {
            validateGeometry(minShift, depth);
            this.minShift = minShift;
            this.depth = depth;
            this.maxPosition = maxPosition(minShift, depth);
            this.forCsi = forCsi;
        }

        /**
         * @return the number of bases the binning scheme can address, i.e. one past the largest position a record
         *     may end at
         */
        public long getMaxPosition() {
            return maxPosition;
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
            if (referenceIndex < 0) {
                throw new IllegalArgumentException("Reference index must not be negative, but was " + referenceIndex);
            }
            if (referenceIndex != currentReference) {
                advanceTo(referenceIndex);
            }
            if (start < 1) {
                throw new IllegalArgumentException("Record start must be at least 1, but was " + start);
            }
            final int begin = start - 1;
            final int endExclusive = Math.max(end, start);
            if (endExclusive > maxPosition) {
                throw beyondReach(referenceIndex, start, end);
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
            if (forCsi) recordCount++;
        }

        // Kept out of add() so that it stays under the JIT's size limit for inlining hot methods; over it, every
        // record pays for a call.
        private IllegalArgumentException beyondReach(final int referenceIndex, final int start, final int end) {
            final boolean baiScheme = minShift == BAI_MIN_SHIFT && depth == BAI_DEPTH;
            return new IllegalArgumentException(String.format(
                    "Record at %d-%d on reference %d lies beyond %d, the last position an index with "
                            + "minShift=%d and depth=%d can address%s",
                    start,
                    end,
                    referenceIndex,
                    maxPosition,
                    minShift,
                    depth,
                    baiScheme ? "; that is the fixed BAI/TBI scheme, so use a CSI index for this data" : ""));
        }

        /**
         * Finishes the reference being built, records empty references for any skipped ordinals, and starts
         * accumulating for {@code referenceIndex}.
         */
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
            recordCount = 0;
        }

        /** Freezes the reference being built, if any, into its {@link ReferenceBins}; a no-op otherwise. */
        private void finishCurrentReference() {
            if (accumulator == null) return;
            // A bin's loffset comes from its first window; a window no record overlaps takes the next window's
            // offset, as htslib does, since records in later windows are what a lookup there goes on to read.
            final long[] loffsetSource = forCsi ? Arrays.copyOf(linearIndex, windowCount) : null;
            for (int window = windowCount - 2; forCsi && window >= 0; window--) {
                if (loffsetSource[window] == UNSET) loffsetSource[window] = loffsetSource[window + 1];
            }
            // The stored linear index instead gives such a window the nearest preceding offset, as samtools does.
            long previous = 0;
            for (int window = 0; window < windowCount; window++) {
                if (linearIndex[window] == UNSET) {
                    linearIndex[window] = previous;
                } else {
                    previous = linearIndex[window];
                }
            }
            final ReferenceBins.Metadata metadata = forCsi ? accumulator.metadata(recordCount) : null;
            finished.add(accumulator.toReferenceBins(
                    forCsi ? bin -> loffsetFromLinearIndex(bin, loffsetSource, depth) : null,
                    Arrays.copyOf(linearIndex, windowCount),
                    metadata,
                    depth));
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
            return new BinningIndex(minShift, depth, finished, NO_COORDINATE_COUNT_ABSENT);
        }

        /** Collects the chunks of one reference's bins. Within a bin, chunks must be added in file order. */
        private static final class ReferenceAccumulator {
            private final Map<Integer, ChunkList> bins = new HashMap<>();
            // Consecutive records nearly always share a bin, so remembering the last one skips most lookups.
            private int lastBinNumber = -1;
            private ChunkList lastBin;

            /** Appends a chunk to a bin, creating the bin on first sight. */
            void addChunk(final int binNumber, final long chunkStart, final long chunkEnd) {
                if (binNumber != lastBinNumber) {
                    lastBin = bins.computeIfAbsent(binNumber, unused -> new ChunkList());
                    lastBinNumber = binNumber;
                }
                lastBin.add(chunkStart, chunkEnd);
            }

            /** The metadata pseudo-bin's view of the reference: the span of the file its records occupy. */
            ReferenceBins.Metadata metadata(final long recordCount) {
                long firstOffset = -1; // unsigned: the largest value
                long lastOffset = 0;
                for (final ChunkList chunks : bins.values()) {
                    if (Long.compareUnsigned(chunks.offsets[0], firstOffset) < 0) firstOffset = chunks.offsets[0];
                    final long end = chunks.offsets[chunks.size - 1];
                    if (Long.compareUnsigned(end, lastOffset) > 0) lastOffset = end;
                }
                return new ReferenceBins.Metadata(firstOffset, lastOffset, recordCount, 0);
            }

            /**
             * Freezes the accumulated bins, in bin-number order, together with the given linear index and
             * metadata.
             *
             * @param loffsetOf gives each bin number its {@code loffset}; null to derive them from the linear
             *     index when first needed
             */
            ReferenceBins toReferenceBins(
                    final IntToLongFunction loffsetOf,
                    final long[] linearIndex,
                    final ReferenceBins.Metadata metadata,
                    final int depth) {
                final int[] binNumbers = bins.keySet().stream()
                        .mapToInt(Integer::intValue)
                        .sorted()
                        .toArray();
                final long[][] binChunks = new long[binNumbers.length][];
                final long[] loffsets = loffsetOf == null ? null : new long[binNumbers.length];
                for (int i = 0; i < binNumbers.length; i++) {
                    binChunks[i] = bins.get(binNumbers[i]).toArray();
                    if (loffsets != null) loffsets[i] = loffsetOf.applyAsLong(binNumbers[i]);
                }
                return new ReferenceBins(binNumbers, binChunks, loffsets, linearIndex, metadata, depth);
            }
        }

        /** A growable list of chunks, flattened to {start0, end0, start1, end1, ...}. */
        private static final class ChunkList {
            private long[] offsets = new long[4];
            private int size;

            /** Appends a chunk, or extends the last one when the two would be read together anyway. */
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

            /** The chunks as a right-sized array. */
            long[] toArray() {
                return Arrays.copyOf(offsets, size);
            }
        }
    }
}
