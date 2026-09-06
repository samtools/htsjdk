package htsjdk.samtools.cram;

import htsjdk.index.HtsFileSpan;
import htsjdk.index.HtsQueryIndex;
import htsjdk.samtools.BAMFileSpan;
import htsjdk.samtools.Chunk;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileSpan;
import htsjdk.samtools.cram.ref.ReferenceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeSet;

/**
 * Answers CRAM region queries from a CRAI index: which containers may hold records overlapping a
 * region, and where unplaced records start.
 *
 * <p>Entries are grouped by reference and sorted by start, with a running maximum end. A binary
 * search on the running maximum finds the first entry that can reach the query; a forward scan
 * stops at the first entry starting past it. This handles nested slices without htslib's nested
 * containment list.
 *
 * <p>Unlike htslib, an entry whose span misses the query is never returned. htslib does so to
 * tolerate missing entries; a CRAI written by htsjdk or samtools has one entry per slice, and a
 * slice's span covers every record in it.
 *
 * <p>There is no metadata method: a CRAI stores no record counts (issue #531).
 *
 * <p>Spans are {@link SAMFileSpan}s, usable with
 * {@link htsjdk.samtools.SamReader.Indexing#iterator(SAMFileSpan)}. Coordinates are container byte
 * offsets shifted left 16 bits.
 */
public class CRAIQueryIndex implements HtsQueryIndex {

    /** Low bits of a CRAM file pointer hold a slice landmark, which the container iterator ignores. */
    private static final int CONTAINER_OFFSET_SHIFT = 16;

    private static final long LANDMARK_MASK = (1L << CONTAINER_OFFSET_SHIFT) - 1;

    /** Entries for placed references, keyed by reference index. */
    private final Map<Integer, ReferenceEntries> entriesByReference;

    /** Offset of the first container holding an unplaced record, if there is one. */
    private final OptionalLong firstUnplacedContainerOffset;

    /**
     * @param entries the CRAI entries, in any order
     * @throws CRAMException if an entry has a reference id below -1
     */
    public CRAIQueryIndex(final Collection<CRAIEntry> entries) {
        final Map<Integer, List<CRAIEntry>> grouped = new HashMap<>();
        long firstUnplaced = Long.MAX_VALUE;
        for (final CRAIEntry entry : entries) {
            final int sequenceId = entry.getSequenceId();
            if (sequenceId < ReferenceContext.UNMAPPED_UNPLACED_ID) {
                // Multi-reference slices are indexed as one entry per reference, never as -2.
                throw new CRAMException("Malformed CRAI entry with reference id " + sequenceId + ": " + entry);
            }
            if (sequenceId == ReferenceContext.UNMAPPED_UNPLACED_ID) {
                // A multi-reference slice with unplaced records has an entry for them, so this finds
                // them even when they share a container with placed records.
                firstUnplaced = Math.min(firstUnplaced, entry.getContainerStartByteOffset());
                continue;
            }
            grouped.computeIfAbsent(sequenceId, id -> new ArrayList<>()).add(entry);
        }

        this.entriesByReference = new HashMap<>(grouped.size());
        grouped.forEach((referenceIndex, referenceEntries) ->
                entriesByReference.put(referenceIndex, new ReferenceEntries(referenceEntries)));
        this.firstUnplacedContainerOffset =
                firstUnplaced == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(firstUnplaced);
    }

    /**
     * {@inheritDoc}
     *
     * <p>One byte range per matching container.
     */
    @Override
    public SAMFileSpan getSpanOverlapping(final int referenceIndex, final int start, final int end) {
        return new BAMFileSpan(chunksForContainers(getContainerOffsets(referenceIndex, start, end)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>From the first container with an unplaced record to end of file.
     */
    @Override
    public Optional<HtsFileSpan> getSpanOfUnplaced() {
        if (firstUnplacedContainerOffset.isEmpty()) {
            return Optional.empty();
        }
        final long containerStart = firstUnplacedContainerOffset.getAsLong() << CONTAINER_OFFSET_SHIFT;
        return Optional.of(new BAMFileSpan(new Chunk(containerStart, Long.MAX_VALUE)));
    }

    /** No-op; the index is held in memory. */
    @Override
    public void close() {}

    /**
     * Byte offset of the first container holding an unplaced record; all unplaced records follow it.
     *
     * @return the offset, or empty if there are none
     */
    public OptionalLong getFirstUnplacedContainerOffset() {
        return firstUnplacedContainerOffset;
    }

    /**
     * Offsets of the containers that may hold records overlapping the region, ascending and distinct.
     *
     * @param referenceIndex index into the sequence dictionary
     * @param start 1-based inclusive start; below 1 means the start of the reference
     * @param end 1-based inclusive end; below 1 means the end of the reference
     * @return the offsets; empty if nothing matches
     */
    public long[] getContainerOffsets(final int referenceIndex, final int start, final int end) {
        final ReferenceEntries entries = entriesByReference.get(referenceIndex);
        if (entries == null) {
            return new long[0];
        }
        final TreeSet<Long> offsets = new TreeSet<>();
        entries.collectOverlapping(start, end, offsets);
        return toLongArray(offsets);
    }

    /**
     * Coordinate pairs for {@code CRAMIterator}: one ascending, disjoint {@code (start, end)} pair
     * per container that may hold records matching any interval.
     *
     * @param intervals the intervals to resolve
     * @return the pairs; empty if nothing matches
     */
    public long[] getCoordinatesForQueries(final QueryInterval[] intervals) {
        final TreeSet<Long> offsets = new TreeSet<>();
        for (final QueryInterval interval : intervals) {
            final ReferenceEntries entries = entriesByReference.get(interval.referenceIndex);
            if (entries != null) {
                entries.collectOverlapping(interval.start, interval.end, offsets);
            }
        }

        final long[] coordinates = new long[offsets.size() * 2];
        int i = 0;
        for (final long offset : offsets) {
            coordinates[i++] = containerStartCoordinate(offset);
            coordinates[i++] = containerEndCoordinate(offset);
        }
        return coordinates;
    }

    private static List<Chunk> chunksForContainers(final long[] containerOffsets) {
        final List<Chunk> chunks = new ArrayList<>(containerOffsets.length);
        for (final long offset : containerOffsets) {
            chunks.add(new Chunk(containerStartCoordinate(offset), containerEndCoordinate(offset)));
        }
        return chunks;
    }

    private static long containerStartCoordinate(final long containerOffset) {
        return containerOffset << CONTAINER_OFFSET_SHIFT;
    }

    /**
     * Setting all landmark bits makes {@code end >> 16} this container and keeps start below end, as
     * the container iterator requires.
     */
    private static long containerEndCoordinate(final long containerOffset) {
        return containerStartCoordinate(containerOffset) | LANDMARK_MASK;
    }

    private static long[] toLongArray(final Collection<Long> values) {
        final long[] array = new long[values.size()];
        int i = 0;
        for (final long value : values) {
            array[i++] = value;
        }
        return array;
    }

    /** One reference's entries, sorted by start, with a running maximum end. */
    private static final class ReferenceEntries {
        /** Sorted by alignment start; ties broken by container then slice offset. */
        private final CRAIEntry[] entries;

        /** Exclusive ends; {@code long} to avoid overflow. */
        private final long[] ends;

        /** {@code maxEndThrough[i]} is the largest end among entries {@code 0..i}; non-decreasing. */
        private final long[] maxEndThrough;

        ReferenceEntries(final List<CRAIEntry> unsorted) {
            this.entries = unsorted.toArray(new CRAIEntry[0]);
            Arrays.sort(entries);

            this.ends = new long[entries.length];
            this.maxEndThrough = new long[entries.length];
            long runningMax = Long.MIN_VALUE;
            for (int i = 0; i < entries.length; i++) {
                ends[i] = (long) entries[i].getAlignmentStart() + entries[i].getAlignmentSpan();
                runningMax = Math.max(runningMax, ends[i]);
                maxEndThrough[i] = runningMax;
            }
        }

        /**
         * Add the container offset of every entry overlapping the region to {@code offsets}.
         *
         * @param queryStart 1-based inclusive start; below 1 means the start of the reference
         * @param queryEnd 1-based inclusive end; below 1 means the end of the reference
         * @param offsets collector for the matching container offsets
         */
        void collectOverlapping(final int queryStart, final int queryEnd, final Collection<Long> offsets) {
            // As in GenomicIndexUtil.regionToBins, a non-positive bound means the reference's own bound.
            final long start = queryStart < 1 ? 1 : queryStart;
            final long end = queryEnd < 1 ? Long.MAX_VALUE : queryEnd;
            if (start > end) {
                return;
            }

            for (int i = firstEntryReaching(start); i < entries.length; i++) {
                if (entries[i].getAlignmentStart() > end) {
                    break;
                }
                // Ends are exclusive.
                if (ends[i] > start) {
                    offsets.add(entries[i].getContainerStartByteOffset());
                }
            }
        }

        /**
         * Index of the first entry whose end reaches {@code start}, or {@code entries.length}; a
         * binary search on {@link #maxEndThrough}.
         */
        private int firstEntryReaching(final long start) {
            int low = 0;
            int high = entries.length;
            while (low < high) {
                final int mid = (low + high) >>> 1;
                if (maxEndThrough[mid] > start) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            return low;
        }
    }
}
