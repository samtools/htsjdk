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

import htsjdk.samtools.AlignmentBlock;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;

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
     *                     passed in that is not coordinate sorted, it will eventually be coordinated sorted by this class.
     */
    public EdgeReadIterator(final SamReader samReader, final IntervalList intervalList) {
        this(samReader, intervalList, samReader.hasIndex());
    }

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
    public EdgeReadIterator(final SamReader samReader, final IntervalList intervalList, final boolean useIndex) {
        super(samReader, intervalList, useIndex);
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
        // interpret the CIGAR string and add the base info
        for (final AlignmentBlock alignmentBlock : rec.getAlignmentBlocks()) {
            // 0-based offset into the read of the current base
            final int readOffset = alignmentBlock.getReadStart() - 1;
            // 1-based reference position that the current base aligns to
            final int refPos = alignmentBlock.getReferenceStart();

            // 0-based offset from the aligned position of the first base in the read to the aligned position
            // of the current base.
            final int refOffset = refPos - rec.getAlignmentStart();
            final int refOffsetEnd = refPos - rec.getAlignmentStart() + alignmentBlock.getLength();


            // Ensure there are AbstractLocusInfos up to and including this position
            for (int j = accumulator.size(); j <= refOffsetEnd; ++j) {
                accumulator.add(createLocusInfo(getReferenceSequence(rec.getReferenceIndex()),
                        rec.getAlignmentStart() + j));
            }

            /* Let's assume an alignment block starts in some locus. 
             * We put two records to the accumulator. The first one has the "begin" type which corresponds to the locus 
             * where the block starts. The second one has the "end" type which corresponds to the other locus where the block ends. 
            */
            int refOffsetInterval = refOffset; // corresponds to the beginning of the alignment block 
            int refOffsetEndInterval = refOffsetEnd;
            int startShift = 0;

            // intersect intervals and alignment block
            if (getIntervals() != null) {
                // get the current interval we're processing 
                Interval interval = getCurrentInterval();
                if (interval != null) {
                    final int intervalEnd = interval.getEnd();
                    final int intervalStart = interval.getStart();
                    // check if an interval and the alignment block overlap
                    if (!CoordMath.overlaps(refPos, refPos + alignmentBlock.getLength(), intervalStart, intervalEnd)) {
                        continue;
                    }
                    // if the alignment block starts out of an interval, shift the starting position
                    if (refPos < intervalStart) {
                        startShift = intervalStart - refPos;
                        refOffsetInterval = refOffsetInterval + startShift;
                    }
                    // if the alignment block ends out of an interval, shift the ending position
                    final int readEnd = refPos + alignmentBlock.getLength();
                    if (refPos + alignmentBlock.getLength() > intervalEnd) {
                        refOffsetEndInterval = refOffsetEndInterval - (readEnd - intervalEnd) + 1;
                    }
                }
            }
            final int length = refOffsetEndInterval - refOffsetInterval;
            // add the alignment block to the accumulator when it starts and when it ends 
            final EdgingRecordAndOffset recordAndOffset = createRecordAndOffset(rec, readOffset + startShift, length, refPos + startShift);
            // accumulate start of the alignment block
            accumulator.get(refOffsetInterval).add(recordAndOffset);
            final EdgingRecordAndOffset recordAndOffsetEnd = createRecordAndOffset(recordAndOffset);
            // accumulate end of the alignment block
            accumulator.get(refOffsetEndInterval).add(recordAndOffsetEnd);
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
