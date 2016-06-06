/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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
 */
public class ReadEndsIterator extends AbstractLocusIterator<TypedRecordAndOffset, AbstractLocusInfo<TypedRecordAndOffset>> {

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments.  Do not use
     * BAM index even if available.
     *
     * @param samReader must be coordinate sorted
     */
    public ReadEndsIterator(final SamReader samReader) {
        this(samReader, null);
    }

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments.  Do not use
     * BAM index even if available.
     *
     * @param samReader    must be coordinate sorted
     * @param intervalList Either the list of desired intervals, or null.  Note that if an intervalList is
     *                     passed in that is not coordinate sorted, it will eventually be coordinated sorted by this class.
     */
    public ReadEndsIterator(final SamReader samReader, final IntervalList intervalList) {
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
    public ReadEndsIterator(final SamReader samReader, final IntervalList intervalList, final boolean useIndex) {
        super(samReader, intervalList, useIndex);
    }

    /**
     * Capture the loci covered by the given SAMRecord in the LocusInfos in the accumulator,
     * creating new LocusInfos as needed. TypedRecordAndOffset object are created only for start
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
            int readOffset = alignmentBlock.getReadStart() - 1;
            // 1-based reference position that the current base aligns to
            int refPos = alignmentBlock.getReferenceStart();

            // 0-based offset from the aligned position of the first base in the read to the aligned position
            // of the current base.
            int refOffset = refPos - rec.getAlignmentStart();
            int refOffsetEnd = refPos - rec.getAlignmentStart() + alignmentBlock.getLength();


            // Ensure there are LocusInfos up to and including this position
            for (int j = accumulator.size(); j <= refOffsetEnd; ++j) {
                accumulator.add(createLocusInfo(getReferenceSequence(rec.getReferenceIndex()),
                        rec.getAlignmentStart() + j));
            }

            int refOffsetInterval = refOffset;
            int refOffsetEndInterval = refOffsetEnd;
            int startShift = 0;

            if (getIntervals() != null) {
                Interval interval = getCurrentInterval();
                if (interval != null) {
                    final int intervalEnd = interval.getEnd();
                    final int intervalStart = interval.getStart();
                    if ((refPos < intervalStart && refPos + alignmentBlock.getLength() < intervalStart)
                            || (refPos > intervalEnd && refPos + alignmentBlock.getLength() > intervalEnd)) {
                        continue;
                    }
                    if (refPos < intervalStart) {
                        startShift = intervalStart - refPos;
                        refOffsetInterval = refOffsetInterval + startShift;
                    }
                    if (refPos + alignmentBlock.getLength() > intervalEnd) {
                        refOffsetEndInterval = refOffsetEndInterval - (refPos + alignmentBlock.getLength() - intervalEnd) + 1;
                    }
                }

            }
            int length = refOffsetEndInterval - refOffsetInterval;
            TypedRecordAndOffset recordAndOffset = createRecordAndOffset(rec, readOffset + startShift, length, refPos + startShift, TypedRecordAndOffset.Type.BEGIN);
            accumulator.get(refOffsetInterval).add(recordAndOffset);
            TypedRecordAndOffset recordAndOffsetEnd = createRecordAndOffset(rec, readOffset + startShift, length, refPos + startShift, TypedRecordAndOffset.Type.END);
            recordAndOffsetEnd.setStart(recordAndOffset);
            accumulator.get(refOffsetEndInterval).add(recordAndOffsetEnd);
        }
    }

    @Override
    void accumulateIndels(SAMRecord rec) {
        throw new UnsupportedOperationException("Indels accumulation is not supported for " + getClass().getSimpleName() + ".");
    }

    /**
     * Creates a new <code>TypedRecordAndOffset</code> for given input values
     *
     * @param rec        aligned SamRecord
     * @param readOffset offset from start of read
     * @param length     length of alignment block
     * @param refPos     position in the reference sequence
     * @param type       BEGIN or END type of RecordAndOffset
     * @return created <code>TypedRecordAndOffset</code>
     */
    @Override
    TypedRecordAndOffset createRecordAndOffset(SAMRecord rec, int readOffset, int length, int refPos, TypedRecordAndOffset.Type type) {
        return new TypedRecordAndOffset(rec, readOffset, length, refPos, type);
    }

    /**
     * @param referenceSequence processed reference sequence
     * @param lastPosition      last processed reference locus position
     * @return <code>AbstractLocusInfo<T></code> for the lastPosition
     */
    @Override
    AbstractLocusInfo<TypedRecordAndOffset> createLocusInfo(SAMSequenceRecord referenceSequence, int lastPosition) {
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
        throw new UnsupportedOperationException("Locus cap is not supported for " + getClass().getSimpleName() + ".");
    }

    /**
     * This method isn't supported in current implementation.
     *
     * @param qualityScoreCutoff the minimum bae quality to include in <code>AbstractLocusInfo</code>.
     */
    @Override
    public void setQualityScoreCutoff(int qualityScoreCutoff) {
        throw new UnsupportedOperationException("Quality filtering is not supported for " + getClass().getSimpleName() + ".");
    }

    /**
     * For correct work of <code>ReadEndsIterator</code> value <code>emitUncoveredLoci</code> must be true.
     *
     * @param emitUncoveredLoci if false, iterator will skip uncovered loci in reference sequence, otherwise
     *                          empty <code>AbstractLocusInfo</code> will be created for each loci.
     */
    @Override
    public void setEmitUncoveredLoci(boolean emitUncoveredLoci) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " doesn't support work with skipping " +
                "uncovered bases.");
    }


    @Override
    public void setIncludeIndels(boolean includeIndels) {
        throw new UnsupportedOperationException("Indels accumulation is not supported for " + getClass().getSimpleName() + ".");
    }
}