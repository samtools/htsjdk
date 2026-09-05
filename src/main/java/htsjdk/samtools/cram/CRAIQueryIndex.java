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
 * Answers CRAM region queries directly from a CRAI index: which containers can hold records
 * overlapping a region, and where the unplaced records start.
 *
 * <p>Entries are grouped by reference and sorted by alignment start, alongside a running maximum of
 * their ends. The running maximum is non-decreasing, so a binary search on it finds the first entry
 * that can reach the query, and a forward scan from there stops at the first entry starting past the
 * query. That resolves slices nested inside longer ones without htslib's nested containment list.
 *
 * <p>Unlike htslib, an entry whose span misses the query is never returned. htslib returns the
 * nearest slice in that case because it tolerates an index with missing entries; every CRAI htsjdk
 * reads or writes has one entry per slice, and a slice's span covers the alignment end of every
 * record in it, so a non-overlapping entry cannot hold a matching record.
 *
 * <p>A CRAI stores no record counts, so there is no metadata method (issue #531).
 *
 * <p>Spans are {@link SAMFileSpan}s so they can be handed back to
 * {@link htsjdk.samtools.SamReader.Indexing#iterator(SAMFileSpan)}. Each coordinate is a container
 * byte offset shifted left 16 bits, the form the CRAM container iterator expects.
 */
public class CRAIQueryIndex implements HtsQueryIndex {

    /** Low bits of a CRAM file pointer, reserved for a slice landmark; the iterator only uses the rest. */
    private static final int CONTAINER_OFFSET_SHIFT = 16;

    private static final long LANDMARK_MASK = (1L << CONTAINER_OFFSET_SHIFT) - 1;

    /** Entries for placed references, keyed by reference index. */
    private final Map<Integer, ReferenceEntries> entriesByReference;

    /** Offset of the first container holding an unplaced record, if there is one. */
    private final OptionalLong firstUnplacedContainerOffset;

    /**
     * Build a queryable index from CRAI entries, as read by
     * {@link htsjdk.samtools.CRAMCRAIIndexer#readIndex}.
     *
     * @param entries the CRAI entries; neither the collection nor its order is retained
     * @throws CRAMException if an entry has a reference id below -1, which no valid CRAI contains
     */
    public CRAIQueryIndex(final Collection<CRAIEntry> entries) {
        final Map<Integer, List<CRAIEntry>> grouped = new HashMap<>();
        long firstUnplaced = Long.MAX_VALUE;
        for (final CRAIEntry entry : entries) {
            final int sequenceId = entry.getSequenceId();
            if (sequenceId < ReferenceContext.UNMAPPED_UNPLACED_ID) {
                // A multi-reference slice is indexed as one entry per reference, never as -2.
                throw new CRAMException("Malformed CRAI entry with reference id " + sequenceId + ": " + entry);
            }
            if (sequenceId == ReferenceContext.UNMAPPED_UNPLACED_ID) {
                // A multi-reference slice holding unplaced records has an entry for them too, so
                // this finds them whether or not they share a container with placed records.
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
     * <p>From the first container holding an unplaced record to the end of the file; empty when
     * the index has no unplaced entries.
     */
    @Override
    public Optional<HtsFileSpan> getSpanOfUnplaced() {
        if (firstUnplacedContainerOffset.isEmpty()) {
            return Optional.empty();
        }
        final long containerStart = firstUnplacedContainerOffset.getAsLong() << CONTAINER_OFFSET_SHIFT;
        return Optional.of(new BAMFileSpan(new Chunk(containerStart, Long.MAX_VALUE)));
    }

    /** Nothing to release: the whole index is held in memory. */
    @Override
    public void close() {}

    /**
     * The byte offset of the first container that holds an unplaced record. Unplaced records sort to
     * the end of a coordinate-sorted CRAM, so reading forward from here reaches all of them.
     *
     * @return the offset, or empty if the index has no unplaced entries
     */
    public OptionalLong getFirstUnplacedContainerOffset() {
        return firstUnplacedContainerOffset;
    }

    /**
     * The offsets of every container that may hold a record overlapping the region, ascending and
     * without duplicates. A multi-reference slice contributes one entry per reference, all sharing
     * a container, which is why duplicates arise.
     *
     * @param referenceIndex reference to query, as an index into the sequence dictionary
     * @param start 1-based inclusive start; anything below 1 means the start of the reference
     * @param end 1-based inclusive end; anything below 1 means the end of the reference
     * @return ascending distinct container offsets, empty if nothing matches
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
     * Coordinate pairs addressing every container that may hold a record matching any of the
     * intervals, in the form {@code CRAMIterator} expects: one {@code (start, end)} pair per
     * container, ascending and disjoint.
     *
     * @param intervals the intervals to resolve; may be empty
     * @return coordinate pairs, or an empty array if no container matches
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
     * The container iterator reads while its position is at or below {@code end >> 16}, so setting
     * every landmark bit addresses exactly this container and keeps start strictly below end.
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

    /** The entries for one reference, sorted by alignment start, with a running maximum of their ends. */
    private static final class ReferenceEntries {
        /** Sorted by alignment start; ties broken by container then slice offset. */
        private final CRAIEntry[] entries;

        /** Exclusive ends, as {@code long} so a span reaching past {@link Integer#MAX_VALUE} is safe. */
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
            // Same bounds convention as the BAI path (GenomicIndexUtil.regionToBins): a non-positive
            // start or end means "as far as the reference goes" in that direction.
            final long start = queryStart < 1 ? 1 : queryStart;
            final long end = queryEnd < 1 ? Long.MAX_VALUE : queryEnd;
            if (start > end) {
                return;
            }

            for (int i = firstEntryReaching(start); i < entries.length; i++) {
                if (entries[i].getAlignmentStart() > end) {
                    break;
                }
                // ends[i] is exclusive, so it must be strictly past a 1-based inclusive start.
                if (ends[i] > start) {
                    offsets.add(entries[i].getContainerStartByteOffset());
                }
            }
        }

        /**
         * The index of the first entry whose end reaches {@code start}, or {@code entries.length} if
         * none does. Relies on {@link #maxEndThrough} being non-decreasing.
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
