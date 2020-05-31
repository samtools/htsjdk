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

import htsjdk.samtools.*;

import java.util.List;

/**
 * Iterator that traverses a BAM/CRAM/SAM file, accumulating information on a per-locus basis.
 * Optionally takes a target interval list, in which case the loci returned are the ones covered by
 * the interval list.  If no target interval list, whatever loci are covered by the input reads are returned.
 * By default duplicate reads and non-primary alignments are filtered out.  Filtering may be changed
 * via setSamFilters().
 *
 * @author alecw@broadinstitute.org
 * @author Jacek Kaczynski
 */

public class FileReaderLocusIterator extends AbstractFileReaderLocusIterator<SamLocusIterator.RecordAndOffset, SamLocusIterator.LocusInfo> {

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments.  Do not use
     * BAM index even if available.
     *
     * @param samReader must be coordinate sorted
     */
    public FileReaderLocusIterator(final SamReader.ReaderImplementation samReader) {
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
    public FileReaderLocusIterator(final SamReader.ReaderImplementation samReader, final IntervalList intervalList) {
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
    public FileReaderLocusIterator(final SamReader.ReaderImplementation samReader, final IntervalList intervalList, final boolean useIndex) {
        super(samReader, intervalList, useIndex);
    }

    /**
     * Capture the loci covered by the given SAMRecord in the LocusInfos in the accumulator,
     * creating new LocusInfos as needed. RecordAndOffset object are created for each aligned base of
     * <code>SAMRecord</code>.
     *
     * @param rec SAMRecord to process and add to <code>LocusInfo</code>
     */
    @Override
    void accumulateSamRecord(final SAMRecord rec) {
        prepareAccumulatorForRecord(rec);

        final int minQuality = getQualityScoreCutoff();
        final boolean dontCheckQualities = minQuality == 0;
        final byte[] baseQualities = dontCheckQualities ? null : rec.getBaseQualities();

        // interpret the CIGAR string and add the base info
        for (final AlignmentBlock alignmentBlock : rec.getAlignmentBlocks()) {
            final int readStart = alignmentBlock.getReadStart();
            final int blockLength = alignmentBlock.getLength();
            final int blockStartAccIndex = alignmentBlock.getReferenceStart() - accumulator.get(0).getPosition();

            for (int i = 0; i < blockLength; ++i) {
                // 0-based offset into the read of the current base
                final int readOffset = readStart + i - 1;

                // if the quality score cutoff is met, accumulate the base info
                if (dontCheckQualities || baseQualities.length == 0 || baseQualities[readOffset] >= minQuality) {
                    // 0-based offset from the aligned position of the first base in the read to the aligned position of the current base.
                    final int accumulateIndex = blockStartAccIndex + i;
                    accumulator.get(accumulateIndex).add(new SamLocusIterator.RecordAndOffset(rec, readOffset));
                }
            }
        }
    }

    /**
     * Requires that the accumulator for the record is previously fill with
     * {@link #accumulateSamRecord(htsjdk.samtools.SAMRecord)}.
     * Include in the LocusInfo the indels; the quality threshold does not affect insertions/deletions
     */
    @Override
    void accumulateIndels(SAMRecord rec) {
        // get the cigar elements
        final List<CigarElement> cigar = rec.getCigar().getCigarElements();
        // 0-based offset into the read of the current base
        int readBase = 0;
        int baseAccIndex = rec.getAlignmentStart() - accumulator.get(0).getPosition();

        // iterate over the cigar element
        for (int elementIndex = 0; elementIndex < cigar.size(); elementIndex++) {
            final CigarElement e = cigar.get(elementIndex);
            final CigarOperator operator = e.getOperator();
            if (operator.equals(CigarOperator.I)) {
                // insertions are included in the previous base
                accumulator.get(baseAccIndex - 1).addInserted(rec, readBase);
                readBase += e.getLength();
            } else if (operator.equals(CigarOperator.D)) {
                // accumulate for each position that spans the deletion
                for (int i = 0; i < e.getLength(); i++) {
                    // the offset is the one for the previous base
                    accumulator.get(baseAccIndex + i).addDeleted(rec, readBase - 1);
                }
                baseAccIndex += e.getLength();
            } else {
                if (operator.consumesReadBases()) readBase += e.getLength();
                if (operator.consumesReferenceBases()) baseAccIndex += e.getLength();
            }
        }
    }

    private void prepareAccumulatorForRecord(SAMRecord rec) {
        final SAMSequenceRecord ref = getReferenceSequence(rec.getReferenceIndex());
        final int alignmentStart = rec.getAlignmentStart();
        final int alignmentEnd = rec.getAlignmentEnd();
        final int alignmentLength = alignmentEnd - alignmentStart;

        // if there is an insertion in the first base and it is not tracked in the accumulator, add it
        if (includeIndels && startWithInsertion(rec.getCigar()) &&
                (accumulator.isEmpty() || accumulator.get(0).getPosition() == alignmentStart)) {
            accumulator.add(0, new SamLocusIterator.LocusInfo(ref, alignmentStart - 1));
        }
        // Ensure there are LocusInfos up to and including this position
        final int accIndexWhereReadStarts = accumulator.isEmpty() ? 0 : alignmentStart - accumulator.get(0).getPosition();
        final int newLocusesCount = accIndexWhereReadStarts + alignmentLength - accumulator.size();
        for (int i = 0; i <= newLocusesCount; i++) {
            accumulator.add(new SamLocusIterator.LocusInfo(ref, alignmentEnd - newLocusesCount + i));
        }
    }

    /**
     * @param rec        aligned SamRecord
     * @param readOffset offset from start of read
     * @param length     1, as object represents only one aligned base
     * @param refPos     -1,  as this filed isn't used for this implementation
     * @return created RecordAndOffset
     */
    @Override
    SamLocusIterator.RecordAndOffset createRecordAndOffset(SAMRecord rec, int readOffset, int length, int refPos) {
        return new SamLocusIterator.RecordAndOffset(rec, readOffset);
    }

    /**
     * @param referenceSequence processed reference sequence
     * @param lastPosition      last processed reference locus position
     * @return <code>LocusInfo<T></code> for the lastPosition
     */
    @Override
    SamLocusIterator.LocusInfo createLocusInfo(SAMSequenceRecord referenceSequence, int lastPosition) {
        return new SamLocusIterator.LocusInfo(referenceSequence, lastPosition);
    }
}
