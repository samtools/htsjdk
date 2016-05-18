/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.samtools.util.CloseableIterator;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Provides an iterator interface for merging multiple underlying iterators into a single
 * iterable stream. The underlying iterators/files must all have the same sort order unless
 * the requested output format is unsorted, in which case any combination is valid.
 */
public class MergingSamRecordIterator implements CloseableIterator<SAMRecord> {
    private final PriorityQueue<ComparableSamRecordIterator> pq;
    private final SamFileHeaderMerger samHeaderMerger;
    private final Collection<SamReader> readers;
    private final SAMFileHeader.SortOrder sortOrder;
    private final SAMRecordComparator comparator;

    private boolean initialized = false;

    /**
     * Constructs a new merging iterator with the same set of readers and sort order as
     * provided by the header merger parameter.
     *
     * @param headerMerger   The merged header and contents of readers.
     * @param forcePresorted True to ensure that the iterator checks the headers of the readers for appropriate sort order.
     * @deprecated replaced by (SamFileHeaderMerger, Collection<SAMFileReader>, boolean)
     */
    public MergingSamRecordIterator(final SamFileHeaderMerger headerMerger, final boolean forcePresorted) {
        this(headerMerger, headerMerger.getReaders(), forcePresorted);
    }

    /**
     * Constructs a new merging iterator with the same set of readers and sort order as
     * provided by the header merger parameter.
     *
     * @param headerMerger The merged header and contents of readers.
     * @param assumeSorted false ensures that the iterator checks the headers of the readers for appropriate sort order.
     */
    public MergingSamRecordIterator(final SamFileHeaderMerger headerMerger, Collection<SamReader> readers, final boolean assumeSorted) {
        this.samHeaderMerger = headerMerger;
        this.sortOrder = headerMerger.getMergedHeader().getSortOrder();
        this.comparator = getComparator();
        this.readers = readers;

        this.pq = new PriorityQueue<ComparableSamRecordIterator>(readers.size());

        for (final SamReader reader : readers) {
            if (!samHeaderMerger.getHeaders().contains(reader.getFileHeader()))
                throw new SAMException("All iterators to be merged must be accounted for in the SAM header merger");
            if (!assumeSorted && this.sortOrder != SAMFileHeader.SortOrder.unsorted &&
                    reader.getFileHeader().getSortOrder() != this.sortOrder) {
                throw new SAMException("Files are not compatible with sort order");
            }
        }
    }

    /**
     * Add a set of SAM file iterators to the merging iterator.  Use this to restrict the merged iteration to a given genomic interval,
     * rather than iterating over every read in the backing file or stream.
     *
     * @param headerMerger The merged header and contents of readers.
     * @param iterators    Iterator traversing over reader contents.
     */
    public MergingSamRecordIterator(final SamFileHeaderMerger headerMerger, final Map<SamReader, CloseableIterator<SAMRecord>> iterators, final boolean assumeSorted) {
        this(headerMerger, iterators.keySet(), assumeSorted);
        for (final Map.Entry<SamReader, CloseableIterator<SAMRecord>> mapping : iterators.entrySet())
            addIfNotEmpty(new ComparableSamRecordIterator(mapping.getKey(), mapping.getValue(), comparator));
        initialized = true;
    }

    private void startIterationIfRequired() {
        if (initialized)
            return;
        for (final SamReader reader : readers)
            addIfNotEmpty(new ComparableSamRecordIterator(reader, reader.iterator(), comparator));
        initialized = true;
    }

    /**
     * Close down all open iterators.
     */
    public void close() {
        // Iterators not in the priority queue have already been closed; only close down the iterators that are still in the priority queue.
        for (CloseableIterator<SAMRecord> iterator : pq)
            iterator.close();
    }

    /** Returns true if any of the underlying iterators has more records, otherwise false. */
    public boolean hasNext() {
        startIterationIfRequired();
        return !this.pq.isEmpty();
    }

    /** Returns the next record from the top most iterator during merging. */
    public SAMRecord next() {
        startIterationIfRequired();

        final ComparableSamRecordIterator iterator = this.pq.poll();
        final SAMRecord record = iterator.next();
        addIfNotEmpty(iterator);
        // this will resolve the reference indices against the new, merged header
        record.setHeader(this.samHeaderMerger.getMergedHeader());

        // Fix the read group if needs be
        if (this.samHeaderMerger.hasReadGroupCollisions()) {
            final String oldGroupId = (String) record.getAttribute(ReservedTagConstants.READ_GROUP_ID);
            if (oldGroupId != null) {
                final String newGroupId = this.samHeaderMerger.getReadGroupId(iterator.getReader().getFileHeader(), oldGroupId);
                record.setAttribute(ReservedTagConstants.READ_GROUP_ID, newGroupId);
            }
        }

        // Fix the program group if needs be
        if (this.samHeaderMerger.hasProgramGroupCollisions()) {
            final String oldGroupId = (String) record.getAttribute(ReservedTagConstants.PROGRAM_GROUP_ID);
            if (oldGroupId != null) {
                final String newGroupId = this.samHeaderMerger.getProgramGroupId(iterator.getReader().getFileHeader(), oldGroupId);
                record.setAttribute(ReservedTagConstants.PROGRAM_GROUP_ID, newGroupId);
            }
        }

        return record;
    }

    /**
     * Adds iterator to priority queue. If the iterator has more records it is added
     * otherwise it is closed and not added.
     */
    private void addIfNotEmpty(final ComparableSamRecordIterator iterator) {
        if (iterator.hasNext()) {
            pq.offer(iterator);
        } else {
            iterator.close();
        }
    }

    /** Unsupported operation. */
    public void remove() {
        throw new UnsupportedOperationException("MergingSAMRecorderIterator.remove()");
    }

    /**
     * Get the right comparator for a given sort order (coordinate, alphabetic). In the
     * case of "unsorted" it will return a comparator that gives an arbitrary but reflexive
     * ordering.
     */
    private SAMRecordComparator getComparator() {
        // For unsorted build a fake comparator that compares based on object ID
        if (this.sortOrder == SAMFileHeader.SortOrder.unsorted) {
            return new SAMRecordComparator() {
                public int fileOrderCompare(final SAMRecord lhs, final SAMRecord rhs) {
                    return System.identityHashCode(lhs) - System.identityHashCode(rhs);
                }

                public int compare(final SAMRecord lhs, final SAMRecord rhs) {
                    return fileOrderCompare(lhs, rhs);
                }
            };
        }
        if (samHeaderMerger.hasMergedSequenceDictionary() && sortOrder.equals(SAMFileHeader.SortOrder.coordinate)) {
            return new MergedSequenceDictionaryCoordinateOrderComparator();
        }

        // Otherwise try and figure out what kind of comparator to return and build it
        return this.sortOrder.getComparatorInstance();
    }

    /** Returns the merged header that the merging iterator is working from. */
    public SAMFileHeader getMergedHeader() {
        return this.samHeaderMerger.getMergedHeader();
    }

    /**
     * Ugh.  Basically does a regular coordinate compare, but looks up the sequence indices in the merged
     * sequence dictionary.  I hate the fact that this extends SAMRecordCoordinateComparator, but it avoids
     * more copy & paste.
     */
    private class MergedSequenceDictionaryCoordinateOrderComparator extends SAMRecordCoordinateComparator implements Serializable {
        private static final long serialVersionUID = 1L;

        public int fileOrderCompare(final SAMRecord samRecord1, final SAMRecord samRecord2) {
            final int referenceIndex1 = getReferenceIndex(samRecord1);
            final int referenceIndex2 = getReferenceIndex(samRecord2);
            if (referenceIndex1 != referenceIndex2) {
                if (referenceIndex1 == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    return 1;
                } else if (referenceIndex2 == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    return -1;
                } else {
                    return referenceIndex1 - referenceIndex2;
                }
            }
            if (referenceIndex1 == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                // Both are unmapped.
                return 0;
            }
            return samRecord1.getAlignmentStart() - samRecord2.getAlignmentStart();
        }

        private int getReferenceIndex(final SAMRecord samRecord) {
            if (samRecord.getReferenceIndex() != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                return samHeaderMerger.getMergedSequenceIndex(samRecord.getHeader(), samRecord.getReferenceIndex());
            }
            if (samRecord.getMateReferenceIndex() != SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                return samHeaderMerger.getMergedSequenceIndex(samRecord.getHeader(), samRecord.getMateReferenceIndex());
            }
            return SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        }
    }
}
