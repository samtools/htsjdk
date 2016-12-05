/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

package htsjdk.samtools.util;


import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.filter.AggregateFilter;
import htsjdk.samtools.filter.DuplicateReadFilter;
import htsjdk.samtools.filter.FilteringSamIterator;
import htsjdk.samtools.filter.SamRecordFilter;
import htsjdk.samtools.filter.SecondaryOrSupplementaryFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Iterator that traverses a SAM File, accumulating information on a per-locus basis.
 * Optionally takes a target interval list, in which case the loci returned are the ones covered by
 * the interval list.  If no target interval list, whatever loci are covered by the input reads are returned.
 * By default duplicate reads and non-primary alignments are filtered out.  Filtering may be changed
 * via setSamFilters().
 *
 * @author Darina_Nikolaeva@epam.com, EPAM Systems, Inc. <www.epam.com>
 * @author Mariia_Zueva@epam.com, EPAM Systems, Inc. <www.epam.com>
 */

public abstract class AbstractLocusIterator<T extends AbstractRecordAndOffset, K extends AbstractLocusInfo<T>> implements Iterable<K>, CloseableIterator<K> {

    static final Log LOG = Log.getInstance(AbstractLocusIterator.class);

    private final SamReader samReader;
    final private ReferenceSequenceMask referenceSequenceMask;
    private PeekableIterator<SAMRecord> samIterator;
    private List<SamRecordFilter> samFilters = Arrays.asList(new SecondaryOrSupplementaryFilter(),
            new DuplicateReadFilter());
    final List<Interval> intervals;

    /**
     * If true, do indexed lookup to improve performance.  Not relevant if intervalList == null.
     * It is no longer the case the useIndex==true can make performance worse.  It should always perform at least
     * as well as useIndex==false, and generally will be much faster.
     */
    private final boolean useIndex;

    /**
     * LocusInfos on this list are ready to be returned by iterator.  All reads that overlap
     * the locus have been accumulated before the AbstractLocusInfo is moved into this list.
     */
    private final ArrayList<K> complete = new ArrayList<>(100);

    /**
     * LocusInfos for which accumulation is in progress.  When {@link #accumulateSamRecord(SAMRecord)} is called
     * the state of this list is guaranteed to be either:
     * a) Empty, or
     * b) That the element at index 0 corresponds to the same genomic locus as the first aligned base
     * in the read being accumulated
     * <p>
     * Before each new read is accumulated the accumulator is examined and:
     * i) any LocusInfos at positions earlier than the read start are moved to {@link #complete}
     * ii) any uncovered positions between the last AbstractLocusInfo and the first aligned base of the new read
     * have LocusInfos created and added to {@link #complete} if we are emitting uncovered loci
     */
    final ArrayList<K> accumulator = new ArrayList<>(100);

    private int qualityScoreCutoff = Integer.MIN_VALUE;
    private int mappingQualityScoreCutoff = Integer.MIN_VALUE;
    private boolean includeNonPfReads = true;

    /**
     * If true, emit a AbstractLocusInfo for every locus in the target map, or if no target map,
     * emit a AbstractLocusInfo for every locus in the reference sequence.
     * If false, emit a AbstractLocusInfo only if a locus has coverage.
     */
    private boolean emitUncoveredLoci = true;

    /**
     * If set, this will cap the number of reads we accumulate for any given position.
     * Note that if we hit the maximum threshold at the first position in the accumulation queue,
     * then we throw further reads overlapping that position completely away (including for subsequent positions).
     * This is a useful feature if one wants to minimize the memory footprint in files with a few massively large pileups,
     * but it must be pointed out that it could cause major bias because of the non-random nature with which the cap is
     * applied (the first maxReadsToAccumulatePerLocus reads are kept and all subsequent ones are dropped).
     */
    private int maxReadsToAccumulatePerLocus = Integer.MAX_VALUE;

    /**
     * Set to true when we have enforced the accumulation limit for the first time
     */
    private boolean enforcedAccumulationLimit = false;

    /**
     * If true, include indels in the LocusInfo
     */
    protected boolean includeIndels = false;

    /**
     * When there is a target mask, these members remember the last locus for which a AbstractLocusInfo has been
     * returned, so that any uncovered locus in the target mask can be covered by a 0-coverage AbstractLocusInfo
     */
    private int lastReferenceSequence = 0;

    /**
     * Last processed locus position in the reference
     */
    private int lastPosition = 0;

    /**
     * Set to true when past all aligned reads in input SAM file
     */
    private boolean finishedAlignedReads = false;

    private final LocusComparator<Locus> locusComparator = new LocusComparator<>();

    /**
     * Last processed interval, relevant only if list of intervals is defined.
     */
    private int lastInterval = 0;

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments
     *
     * @param samReader    must be coordinate sorted
     * @param intervalList Either the list of desired intervals, or null.  Note that if an intervalList is
     *                     passed in that is not coordinate sorted, it will eventually be coordinated sorted by this class.
     * @param useIndex     If true, do indexed lookup to improve performance.  Not relevant if intervalList == null.
     *                     It is no longer the case the useIndex==true can make performance worse.  It should always perform at least
     *                     as well as useIndex==false, and generally will be much faster.
     */

    public AbstractLocusIterator(final SamReader samReader, final IntervalList intervalList, final boolean useIndex) {
        final String className = this.getClass().getSimpleName();
        if (samReader.getFileHeader().getSortOrder() == null || samReader.getFileHeader().getSortOrder() == SAMFileHeader.SortOrder.unsorted) {

            LOG.warn(className + " constructed with samReader that has SortOrder == unsorted.  ", "" +
                    "Assuming SAM is coordinate sorted, but exceptions may occur if it is not.");
        } else if (samReader.getFileHeader().getSortOrder() != SAMFileHeader.SortOrder.coordinate) {
            throw new SAMException(className + " cannot operate on a SAM file that is not coordinate sorted.");
        }
        this.samReader = samReader;
        this.useIndex = useIndex;
        if (intervalList != null) {
            intervals = intervalList.uniqued().getIntervals();
            this.referenceSequenceMask = new IntervalListReferenceSequenceMask(intervalList);
        } else {
            intervals = null;
            this.referenceSequenceMask = new WholeGenomeReferenceSequenceMask(samReader.getFileHeader());
        }
    }

    /**
     * @return iterator over all/all covered locus position in reference according to <code>emitUncoveredLoci</code>
     * value.
     */
    public Iterator<K> iterator() {
        if (samIterator != null) {
            throw new IllegalStateException("Cannot call iterator() more than once on " + this.getClass().getSimpleName());
        }
        CloseableIterator<SAMRecord> tempIterator;
        if (intervals != null) {
            tempIterator = new SamRecordIntervalIteratorFactory().makeSamRecordIntervalIterator(samReader, intervals, useIndex);
        } else {
            tempIterator = samReader.iterator();
        }
        if (samFilters != null) {
            tempIterator = new FilteringSamIterator(tempIterator, new AggregateFilter(samFilters));
        }
        samIterator = new PeekableIterator<>(tempIterator);
        return this;
    }

    /**
     * Closes inner <code>SamIterator</>.
     */
    public void close() {
        this.samIterator.close();
    }

    private boolean samHasMore() {
        return !finishedAlignedReads && (samIterator.peek() != null);
    }

    /**
     * Returns true if there are more AbstractLocusInfo<T> objects that can be returned, due to any of the following reasons:
     * 1) there are more aligned reads in the SAM file
     * 2) there are AbstractLocusInfo<T>s in some stage of accumulation
     * 3) there are loci in the target mask that have yet to be accumulated (even if there are no reads covering them)
     */
    public boolean hasNext() {
        if (this.samIterator == null) {
            iterator();
        }

        while (complete.isEmpty() && ((!accumulator.isEmpty()) || samHasMore() || hasRemainingMaskBases())) {
            final K locusInfo = next();
            if (locusInfo != null) {
                complete.add(0, locusInfo);
            }
        }
        return !complete.isEmpty();
    }

    /**
     * @return true if there are more bases at which the locus iterator must emit AbstractLocusInfo<T>s because
     * there are loci beyond the last emitted loci which are in the set of loci to be emitted and
     * the iterator is setup to emit uncovered loci - so we can guarantee we'll emit those loci.
     */
    private boolean hasRemainingMaskBases() {
        // if there are more sequences in the mask, by definition some of them must have
        // marked bases otherwise if we're in the last sequence, but we're not at the last marked position,
        // there is also more in the mask
        if (!emitUncoveredLoci) {
            // If not emitting uncovered loci, this check is irrelevant
            return false;
        }
        return (lastReferenceSequence < referenceSequenceMask.getMaxSequenceIndex() ||
                (lastReferenceSequence == referenceSequenceMask.getMaxSequenceIndex() &&
                        lastPosition < referenceSequenceMask.nextPosition(lastReferenceSequence, lastPosition)));
    }

    /**
     * hasNext() has been fixed so that if it returns true, next() is now guaranteed not to return null.
     *
     * @return information about next locus position in reference sequence
     */
    public K next() {
        // if we don't have any completed entries to return, try and make some!
        while (complete.isEmpty() && samHasMore()) {
            final SAMRecord rec = samIterator.peek();

            // There might be unmapped reads mixed in with the mapped ones, but when a read
            // is encountered with no reference index it means that all the mapped reads have been seen.
            if (rec.getReferenceIndex() == -1) {
                this.finishedAlignedReads = true;
                continue;

            }
            // Skip over an unaligned read that has been forced to be sorted with the aligned reads
            if (rec.getReadUnmappedFlag()
                    || rec.getMappingQuality() < this.mappingQualityScoreCutoff
                    || (!this.includeNonPfReads && rec.getReadFailsVendorQualityCheckFlag())) {
                samIterator.next();
                continue;
            }

            int start = rec.getAlignmentStart();
            // only if we are including indels and the record does not start in the first base of the reference
            // the stop locus to populate the queue is not the same if the record starts with an insertion
            if (includeIndels && start != 1 && startWithInsertion(rec.getCigar())) {
                // the start to populate is one less
                start--;
            }
            final Locus alignmentStart = new LocusImpl(rec.getReferenceIndex(), start);
            // emit everything that is before the start of the current read, because we know no more
            // coverage will be accumulated for those loci.
            while (!accumulator.isEmpty() && locusComparator.compare(accumulator.get(0), alignmentStart) < 0) {
                final K first = accumulator.get(0);
                populateCompleteQueue(alignmentStart);
                if (!complete.isEmpty()) {
                    return complete.remove(0);
                }
                if (!accumulator.isEmpty() && first == accumulator.get(0)) {
                    throw new SAMException("Stuck in infinite loop");
                }
            }

            // at this point, either the accumulator list is empty or the head should
            // be the same position as the first base of the read (or insertion if first)
            if (!accumulator.isEmpty()) {
                if (accumulator.get(0).getSequenceIndex() != rec.getReferenceIndex() ||
                        accumulator.get(0).getPosition() != start) {
                    throw new IllegalStateException("accumulator should be empty or aligned with current SAMRecord");
                }
            }

            // Store the loci for the read in the accumulator
            if (!surpassedAccumulationThreshold()) {
                accumulateSamRecord(rec);
                // Store the indels if requested
                if (includeIndels) {
                    accumulateIndels(rec);
                }
            }
            samIterator.next();
        }

        final Locus endLocus = new LocusImpl(Integer.MAX_VALUE, Integer.MAX_VALUE);
        // if we have nothing to return to the user, and we're at the end of the SAM iterator,
        // push everything into the complete queue
        if (complete.isEmpty() && !samHasMore()) {
            while (!accumulator.isEmpty()) {
                populateCompleteQueue(endLocus);
                if (!complete.isEmpty()) {
                    return complete.remove(0);
                }
            }
        }

        // if there are completed entries, return those
        if (!complete.isEmpty()) {
            return complete.remove(0);
        } else if (emitUncoveredLoci) {
            final Locus afterLastMaskPositionLocus = new LocusImpl(referenceSequenceMask.getMaxSequenceIndex(),
                    referenceSequenceMask.getMaxPosition() + 1);
            // In this case... we're past the last read from SAM so see if we can
            // fill out any more (zero coverage) entries from the mask
            return createNextUncoveredLocusInfo(afterLastMaskPositionLocus);
        } else {
            return null;
        }
    }


    /**
     * @return true if we have surpassed the maximum accumulation threshold for the first locus in the accumulator, false otherwise
     */

    private boolean surpassedAccumulationThreshold() {
        final boolean surpassesThreshold = !accumulator.isEmpty() && accumulator.get(0).getRecordAndOffsets().size() >= maxReadsToAccumulatePerLocus;
        if (surpassesThreshold && !enforcedAccumulationLimit) {
            LOG.warn("We have encountered greater than " + maxReadsToAccumulatePerLocus + " reads at position " + accumulator.get(0).toString() + " and will ignore the remaining reads at this position.  Note that further warnings will be suppressed.");
            enforcedAccumulationLimit = true;
        }
        return surpassesThreshold;
    }

    /**
     * Capture the loci covered by the given SAMRecord in the LocusInfos in the accumulator,
     * creating new LocusInfos as needed.
     *
     * @param rec record to add to accumulator
     */
    abstract void accumulateSamRecord(final SAMRecord rec);


    /**
     * Requires that the accumulator for the record is previously fill with
     * {@link #accumulateSamRecord(htsjdk.samtools.SAMRecord)}.
     * Include in the LocusInfo the indels; the quality threshold does not affect insertions/deletions
     */
    abstract void accumulateIndels(final SAMRecord rec);

    /**
     * @param rec         aligned SamRecord
     * @param readOffset  offset from start of read
     * @param length      length of aligned block
     * @param refPosition position in the reference sequence
     * @return RecordAndOffset
     */
    abstract T createRecordAndOffset(SAMRecord rec, int readOffset, int length, int refPosition);

    /**
     * Create the next relevant zero-coverage AbstractLocusInfo<T>
     *
     * @param stopBeforeLocus don't go up to this sequence and position
     * @return a zero-coverage AbstractLocusInfo<T>, or null if there is none before the stopBefore locus
     */
    private K createNextUncoveredLocusInfo(final Locus stopBeforeLocus) {
        while (lastReferenceSequence <= stopBeforeLocus.getSequenceIndex() &&
                lastReferenceSequence <= referenceSequenceMask.getMaxSequenceIndex()) {

            if (lastReferenceSequence == stopBeforeLocus.getSequenceIndex() &&
                    lastPosition + 1 >= stopBeforeLocus.getPosition()) {
                return null;
            }

            final int nextbit = referenceSequenceMask.nextPosition(lastReferenceSequence, lastPosition);

            // try the next reference sequence
            if (nextbit == -1) {
                // No more in this reference sequence
                if (lastReferenceSequence == stopBeforeLocus.getSequenceIndex()) {
                    lastPosition = stopBeforeLocus.getPosition();
                    return null;
                }
                lastReferenceSequence++;
                lastPosition = 0;
            } else if (lastReferenceSequence < stopBeforeLocus.getSequenceIndex() || nextbit < stopBeforeLocus.getPosition()) {
                lastPosition = nextbit;
                return createLocusInfo(getReferenceSequence(lastReferenceSequence), lastPosition);
            } else if (nextbit >= stopBeforeLocus.getPosition()) {
                return null;
            }
        }
        return null;
    }

    /**
     * @param referenceSequence processed reference sequence
     * @param lastPosition      last processed reference locus position
     * @return <code>AbstractLocusInfo<T></code> for the lastPosition
     */
    abstract K createLocusInfo(SAMSequenceRecord referenceSequence, int lastPosition);

    /**
     * Pop the first entry from the AbstractLocusInfo<T> accumulator into the complete queue.  In addition,
     * check the ReferenceSequenceMask and if there are intervening mask positions between the last popped base and the one
     * about to be popped, put those on the complete queue as well.
     * Note that a single call to this method may not empty the accumulator completely, or even
     * empty it at all, because it may just put a zero-coverage AbstractLocusInfo<T> into the complete queue.
     *
     * @param stopBeforeLocus don't go up to this sequence and position
     */
    private void populateCompleteQueue(final Locus stopBeforeLocus) {
        // Because of gapped alignments, it is possible to create LocusInfo's with no reads associated with them.
        // Skip over these if not including indels
        while (!accumulator.isEmpty() && accumulator.get(0).isEmpty() &&
                locusComparator.compare(accumulator.get(0), stopBeforeLocus) < 0) {
            accumulator.remove(0);
        }
        if (accumulator.isEmpty()) {
            return;
        }
        final K locusInfo = accumulator.get(0);
        if (locusComparator.compare(stopBeforeLocus, locusInfo) <= 0) {
            return;
        }

        // If necessary, emit a zero-coverage LocusInfo
        if (emitUncoveredLoci) {
            final K zeroCoverage = createNextUncoveredLocusInfo(locusInfo);
            if (zeroCoverage != null) {
                complete.add(zeroCoverage);
                return;
            }
        }

        // At this point we know we're going to process the LocusInfo, so remove it from the accumulator.
        accumulator.remove(0);

        // fill in any gaps based on our genome mask
        final int sequenceIndex = locusInfo.getSequenceIndex();


        // only add to the complete queue if it's in the mask (or we have no mask!)
        if (referenceSequenceMask.get(locusInfo.getSequenceIndex(), locusInfo.getPosition())) {
            complete.add(locusInfo);
        }

        lastReferenceSequence = sequenceIndex;
        lastPosition = locusInfo.getPosition();
    }

    protected SAMSequenceRecord getReferenceSequence(final int referenceSequenceIndex) {
        return samReader.getFileHeader().getSequence(referenceSequenceIndex);
    }

    public void remove() {
        throw new UnsupportedOperationException("Can not remove records from a SAM file via an iterator!");
    }

    /**
     * Check if cigar start with an insertion, ignoring other operators that do not consume references bases
     *
     * @param cigar the cigar
     * @return <code>true</code> if the first operator to consume reference bases or be an insertion, is an insertion; <code>false</code> otherwise
     */
    protected static boolean startWithInsertion(final Cigar cigar) {
        for (final CigarElement element : cigar.getCigarElements()) {
            if (element.getOperator() == CigarOperator.I) return true;
            if (!element.getOperator().consumesReferenceBases()) continue;
            break;
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------
    // Helper methods below this point...
    // --------------------------------------------------------------------------------------------

    /**
     * Controls which, if any, SAMRecords are filtered.  By default duplicate reads and non-primary alignments
     * are filtered out.  The list of filters passed here replaces any existing filters.
     *
     * @param samFilters list of filters, or null if no filtering is desired.
     */
    public void setSamFilters(final List<SamRecordFilter> samFilters) {
        this.samFilters = samFilters;
    }

    public int getQualityScoreCutoff() {
        return qualityScoreCutoff;
    }

    public void setQualityScoreCutoff(final int qualityScoreCutoff) {
        this.qualityScoreCutoff = qualityScoreCutoff;
    }

    public int getMappingQualityScoreCutoff() {
        return mappingQualityScoreCutoff;
    }

    public void setMappingQualityScoreCutoff(final int mappingQualityScoreCutoff) {
        this.mappingQualityScoreCutoff = mappingQualityScoreCutoff;
    }

    public boolean isIncludeNonPfReads() {
        return includeNonPfReads;
    }

    public void setIncludeNonPfReads(final boolean includeNonPfReads) {
        this.includeNonPfReads = includeNonPfReads;
    }

    public boolean isEmitUncoveredLoci() {
        return emitUncoveredLoci;
    }

    public void setEmitUncoveredLoci(final boolean emitUncoveredLoci) {
        this.emitUncoveredLoci = emitUncoveredLoci;
    }

    public int getMaxReadsToAccumulatePerLocus() {
        return maxReadsToAccumulatePerLocus;
    }

    /**
     * If set, this will cap the number of reads we accumulate for any given position.
     * As is pointed out above, setting this could cause major bias because of the non-random nature with which the
     * cap is applied (the first maxReadsToAccumulatePerLocus reads are kept and all subsequent ones are dropped).
     */
    public void setMaxReadsToAccumulatePerLocus(final int maxReadsToAccumulatePerLocus) {
        this.maxReadsToAccumulatePerLocus = maxReadsToAccumulatePerLocus;
    }

    protected List<Interval> getIntervals() {
        return intervals;
    }

    protected Interval getCurrentInterval() {
        if (intervals == null) return null;
        return intervals.get(lastInterval);
    }

    public boolean isIncludeIndels() {
        return includeIndels;
    }

    public void setIncludeIndels(final boolean includeIndels) {
        this.includeIndels = includeIndels;
    }
}
