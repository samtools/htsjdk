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

import htsjdk.samtools.*;

/**
 * Iterator that traverses a SAM File, accumulating information on a per-locus basis.
 * Optionally takes a target interval list, in which case the loci returned are the ones covered by
 * the interval list.  If no target interval list, whatever loci are covered by the input reads are returned.
 * By default duplicate reads and non-primary alignments are filtered out.  Filtering may be changed
 * via setSamFilters(). Difference from SamLocusIterator is that this implementation accumulates data
 * only about start and end of alignment blocks from reads, not about each aligned base.
 * 
 * @author Darina_Nikolaeva@epam.com, EPAM Systems, Inc. <www.epam.com>
 * @author Mariia_Zueva@epam.com, EPAM Systems, Inc. <www.epam.com>
 * 
 */
public class EdgeReadIterator extends AbstractLocusIterator<EdgingRecordAndOffset, AbstractLocusInfo<EdgingRecordAndOffset>> {
    // These variables are required to perform the detection of overlap between reads and intervals
    private Interval currentInterval = null;
    private final PeekableIterator<Interval> intervalListIterator;
    private final IntervalCoordinateComparator intervalCoordinateComparator;

    private final OverlapDetector<Interval> overlapDetector;

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments.  Do not use
     * BAM index even if available.
     *
     * @param samReader must be coordinate sorted
     */
    public EdgeReadIterator(final SamReader samReader) {
        this(samReader, null);
    }

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments.
     *
     * @param samReader    must be coordinate sorted
     * @param intervalList Either the list of desired intervals, or null.  Note that if an intervalList is
     *                     passed in that is not coordinate sorted, it will be coordinated sorted by this class.
     */
    public EdgeReadIterator(final SamReader samReader, final IntervalList intervalList) {
        this(samReader, intervalList, samReader.hasIndex());
    }

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments
     *
     * @param samReader    must be coordinate sorted
     * @param intervalList Either the list of desired intervals, or null.  Note that if an intervalList is
     *                     passed in that is not coordinate sorted, it will be coordinated sorted here.
     * @param useIndex     If true, do indexed lookup to improve performance.  Not relevant if intervalList == null.
     *                     It is no longer the case the useIndex==true can make performance worse.  It should always perform at least
     *                     as well as useIndex==false, and generally will be much faster.
     */
    public EdgeReadIterator(final SamReader samReader, final IntervalList intervalList, final boolean useIndex) {
        super(samReader, intervalList, useIndex);

        if (getIntervals() == null) {
            intervalListIterator = null;
            intervalCoordinateComparator = null;
            overlapDetector = null;
        } else {
            // For the easy case of a read being fully contained in an interval, we use an iterator to keep track
            // of the current interval.
            intervalListIterator = new PeekableIterator<>(getIntervals().iterator());
            if (intervalListIterator.hasNext()) {
                currentInterval = intervalListIterator.next();
            }
            // We also need this comparator to keep track of the different contigs. The constructor of
            // AbstractLocusInfo ensures that the sequence dictionaries of the SAM file and the interval list match.
            intervalCoordinateComparator = new IntervalCoordinateComparator(getHeader());

            // For more complicated cases, we need an OverlapDetector.
            overlapDetector = OverlapDetector.create(getIntervals());
        }
    }

    /**
     * This function updates currentInterval according to the position of the record that it is
     * presented and determines if the current read is fully contained in the currentInterval.
     * @param rec The record we want to consider
     * @return True, if rec is fully contained in the current interval, otherwise false
     */
     protected boolean advanceCurrentIntervalAndCheckIfIntervalContainsRead(final SAMRecord rec) {
         // currentInterval should never be null when calling this method, but we have to check it just to make sure,
         // so that we don't get a NullPointerException in the return statement.
         if (currentInterval == null) {
             return false;
         }
        // Here we need to update the currentInterval. We have to do this using an
        // IntervalCoordinateComparator to take factor in the order in the sequence dictionary.
        while (intervalListIterator.peek() != null && intervalCoordinateComparator.compare(new Interval(rec), intervalListIterator.peek()) > 0) {
            currentInterval = intervalListIterator.next();
        }
        return currentInterval.contains(rec);
    }

    /**
     * Capture the loci covered by the given SAMRecord in the AbstractLocusInfos in the accumulator,
     * creating new AbstractLocusInfos as needed. EdgingRecordAndOffset object are created only for start
     * and end of each alignment block of SAMRecord.
     * If list of intervals is defined, start or/and length of alignment block are shifted to match the interval, to
     * prevent exceeding the interval.
     * @param rec SAMRecord to process and add to <code>AbstractLocusInfo</code>
     */
    @Override
    void accumulateSamRecord(SAMRecord rec) {
        // In the case that no intervals are passed, or that the current interval completely contains
        // the current read (which is the most common case for WGS), set needToConsiderIntervals to false, so we don't
        // have to find intersections and can later emit the read right away.
        final boolean needToConsiderIntervals = intervals != null && !advanceCurrentIntervalAndCheckIfIntervalContainsRead(rec);

        // interpret the CIGAR string and add the base info
        for (final AlignmentBlock alignmentBlock : rec.getAlignmentBlocks()) {
            // General concept
            //
            // Example: Read (or more accurately, AlignmentBlock) from position 101 to 108
            //
            // accumulator (ArrayList<LocusInfo>)     30  31  32  33  34  35  36  37  38 (has one LocusInfo at each position, corresponding to the genomic position)
            // LocusInfo objects (with genomic pos.) 100 101 102 103 104 105 106 107 108 (the LocusInfo objects can contain EdgingRecordAndOffset objects, if a record starts or ends there)
            //                                            ^                           ^
            //                                            |                           |
            // EdgingRecordAndOffset objects              B                           E

            // Steps:
            // 1. Fill accumulator with enough LocusInfos to the end of the read
            // 2. Determine which parts of the read are covered by the specified intervals
            //    (this step will be skipped if the read is completely contained within the
            //    interval, i.e. needToConsiderIntervals is false, otherwise each overlap
            //    will be considered individually)
            // 2. Create a EdgingRecordAndOffset object with the type BEGIN and add it
            //    to the LocusInfo at the position in the accumulator corresponding to
            //    the start position of the read (or overlap)
            // 3. Create a EdgingRecordAndOffset object with the type END and add it
            //    to the LocusInfo at the position in the accumulator corresponding to
            //    the end position of the read (or overlap)

            // 0-based offset into the read of the current base
            final int offsetStartOfAlignmentBlockInRead = alignmentBlock.getReadStart() - 1;
            // 1-based reference position that the current base aligns to
            final int referencePositionStartOfAlignmentBlock = alignmentBlock.getReferenceStart();

            // Here we add the first entry to the accumulator, which is the start of this AlignmentBlock.
            if (accumulator.isEmpty()) {
                accumulator.add(createLocusInfo(getReferenceSequence(rec.getReferenceIndex()), rec.getAlignmentStart()));
            }

            // The accumulator should always have LocusInfos that correspond to one consecutive segment of loci from
            // one reference sequence. So
            // accumulator.get(0).getPosition() + accumulator.size() == accumulator.get(accumulator.size()-1).getPosition()+1
            final int accumulatorNextPosition = accumulator.get(0).getPosition() + accumulator.size();

            if (accumulatorNextPosition != accumulator.get(accumulator.size() - 1).getPosition() + 1) {
                throw new IllegalStateException("The accumulator has gotten into a funk. Cannot continue");
            }

            // Ensure there are consecutive AbstractLocusInfos up to and including the end of the AlignmentBlock
            for (int locusPos = accumulatorNextPosition; locusPos <= referencePositionStartOfAlignmentBlock + alignmentBlock.getLength(); ++locusPos) {
                accumulator.add(createLocusInfo(getReferenceSequence(rec.getReferenceIndex()), locusPos));
            }

            // Let's assume an alignment block starts in some locus.
            // We put two records to the accumulator. The first one has the "begin" type which corresponds to the locus
            // where the block starts. The second one has the "end" type which corresponds to the other locus where the block ends.

            // 0-based offset from the aligned position of the first base in the read to the aligned position
            // of the current base.
            final int offsetStartOfAlignmentBlockOnReference = referencePositionStartOfAlignmentBlock - rec.getAlignmentStart();
            // Similar for the end of the alignment block. We can simply add the length of the block, since by
            // definition all bases in an AlignmentBlock match the reference alignment
            final int offsetEndOfAlignmentBlockOnReference = offsetStartOfAlignmentBlockOnReference + alignmentBlock.getLength();

            if (needToConsiderIntervals) {
                // If the read isn't fully contained within the currentInterval, we need to manually handle each of the overlaps.

                for (final Interval interval : overlapDetector.getOverlaps(new Interval(rec.getContig(), referencePositionStartOfAlignmentBlock, referencePositionStartOfAlignmentBlock + alignmentBlock.getLength()))) {
                    // In case the start position is smaller than the start of the interval, we need to determine the offset (we need this later)...
                    final int offsetStartOfIntervalInAlignmentBlock = referencePositionStartOfAlignmentBlock < interval.getStart() ? interval.getStart() - referencePositionStartOfAlignmentBlock : 0;
                    // ... and add it to the start position to get the actual position from where we want to count.
                    final int offsetStartOfActualSequenceOnReference = offsetStartOfAlignmentBlockOnReference + offsetStartOfIntervalInAlignmentBlock;

                    // Similarly, we need to determine the actual end of the sequence we want to consider.
                    final int referencePositionEndOfAlignmentBlock = referencePositionStartOfAlignmentBlock + alignmentBlock.getLength();
                    // For that, we find the difference between the end position of the AlignmentBlock and the end of the interval, and subtract it from the offset of end of the AlignmentBlock
                    final int offsetEndOfActualSequenceOnReference = offsetEndOfAlignmentBlockOnReference - (referencePositionEndOfAlignmentBlock > interval.getEnd() ? referencePositionEndOfAlignmentBlock - interval.getEnd() - 1 : 0);

                    final int length = offsetEndOfActualSequenceOnReference - offsetStartOfActualSequenceOnReference;

                    // accumulate start of the overlap block
                    final EdgingRecordAndOffset recordAndOffset = createRecordAndOffset(rec, offsetStartOfAlignmentBlockInRead + offsetStartOfIntervalInAlignmentBlock, length, referencePositionStartOfAlignmentBlock + offsetStartOfIntervalInAlignmentBlock);
                    accumulator.get(offsetStartOfActualSequenceOnReference).add(recordAndOffset);

                    // accumulate end of the overlap block
                    final EdgingRecordAndOffset recordAndOffsetEnd = createRecordAndOffset(recordAndOffset);
                    accumulator.get(offsetEndOfActualSequenceOnReference).add(recordAndOffsetEnd);
                }
            } else {
                // If the read is fully contained within the interval, then we don't need to determine the overlaps,
                // which will speed this up significantly.

                final int length = offsetEndOfAlignmentBlockOnReference - offsetStartOfAlignmentBlockOnReference;

                // accumulate start of the alignment block
                final EdgingRecordAndOffset recordAndOffset = createRecordAndOffset(rec, offsetStartOfAlignmentBlockInRead, length, referencePositionStartOfAlignmentBlock);
                accumulator.get(offsetStartOfAlignmentBlockOnReference).add(recordAndOffset);

                // accumulate end of the alignment block
                final EdgingRecordAndOffset recordAndOffsetEnd = createRecordAndOffset(recordAndOffset);
                accumulator.get(offsetEndOfAlignmentBlockOnReference).add(recordAndOffsetEnd);
            }
        }
    }

    @Override
    void accumulateIndels(SAMRecord rec) {
        throw new UnsupportedOperationException("Indels accumulation is not supported for " + getClass().getSimpleName() + ".");
    }

    /**
     * Creates a new <code>EdgingRecordAndOffset</code> for given input values
     *
     * @param rec        aligned SamRecord
     * @param readOffset offset from start of read
     * @param length     length of alignment block
     * @param refPos     position in the reference sequence
     * @return created <code>EdgingRecordAndOffset</code>
     */
    @Override
    EdgingRecordAndOffset createRecordAndOffset(SAMRecord rec, int readOffset, int length, int refPos) {
        return EdgingRecordAndOffset.createBeginRecord(rec, readOffset, length, refPos);
    }

    EdgingRecordAndOffset createRecordAndOffset(EdgingRecordAndOffset startRecord) {
        return EdgingRecordAndOffset.createEndRecord(startRecord);
    }

    /**
     * @param referenceSequence processed reference sequence
     * @param lastPosition      last processed reference locus position
     * @return <code>AbstractLocusInfo<T></code> for the lastPosition
     */
    @Override
    AbstractLocusInfo<EdgingRecordAndOffset> createLocusInfo(SAMSequenceRecord referenceSequence, int lastPosition) {
        return new AbstractLocusInfo<>(referenceSequence, lastPosition);
    }

    /**
     * This method isn't supported in current implementation.
     *
     * @param maxReadsToAccumulatePerLocus maximum number of <code>RecordAndOffset</code> objects to store for
     *                                     one loci in reference sequence.
     */
    @Override
    public void setMaxReadsToAccumulatePerLocus(int maxReadsToAccumulatePerLocus) {
        if (getMaxReadsToAccumulatePerLocus() != 0) {
            throw new UnsupportedOperationException("Locus cap is not supported for " + getClass().getSimpleName() + ".");
        }
    }

    /**
     * This method isn't supported in current implementation.
     *
     * @param qualityScoreCutoff the minimum base quality to include in <code>AbstractLocusInfo</code>.
     */
    @Override
    public void setQualityScoreCutoff(int qualityScoreCutoff) {
        throw new UnsupportedOperationException("Quality filtering is not supported for " + getClass().getSimpleName() + ".");
    }

    /**
     * For correct work of <code>EdgeReadIterator</code> value <code>emitUncoveredLoci</code> must be true.
     *
     * @param emitUncoveredLoci if false, iterator will skip uncovered loci in reference sequence, otherwise
     *                          empty <code>AbstractLocusInfo</code> will be created for each loci.
     */
    @Override
    public void setEmitUncoveredLoci(boolean emitUncoveredLoci) {
        if (isEmitUncoveredLoci() != emitUncoveredLoci) {
            throw new UnsupportedOperationException(getClass().getSimpleName() + " doesn't support work with skipping " +
                    "uncovered bases.");
        }
    }

    @Override
    public void setIncludeIndels(boolean includeIndels) {
        if (isIncludeIndels() != includeIndels) {
            throw new UnsupportedOperationException("Indels accumulation is not supported for " + getClass().getSimpleName() + ".");
        }
    }
}
