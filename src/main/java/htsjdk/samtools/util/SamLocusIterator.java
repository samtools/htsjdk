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
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Iterator that traverses a SAM File, accumulating information on a per-locus basis.
 * Optionally takes a target interval list, in which case the loci returned are the ones covered by
 * the interval list.  If no target interval list, whatever loci are covered by the input reads are returned.
 * By default duplicate reads and non-primary alignments are filtered out.  Filtering may be changed
 * via setSamFilters().
 *
 * @author alecw@broadinstitute.org
 */

public class SamLocusIterator extends AbstractLocusIterator<SamLocusIterator.RecordAndOffset, SamLocusIterator.LocusInfo> {

    /**
     * Prepare to iterate through the given SAM records, skipping non-primary alignments.  Do not use
     * BAM index even if available.
     *
     * @param samReader must be coordinate sorted
     */
    public SamLocusIterator(final SamReader samReader) {
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
    public SamLocusIterator(final SamReader samReader, final IntervalList intervalList) {
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
    public SamLocusIterator(final SamReader samReader, final IntervalList intervalList, final boolean useIndex) {
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
        // get the accumulator offset
        int accOffset = getAccumulatorOffset(rec);

        final int minQuality = getQualityScoreCutoff();
        final boolean dontCheckQualities = minQuality == 0;
        final byte[] baseQualities = dontCheckQualities ? null : rec.getBaseQualities();

        // interpret the CIGAR string and add the base info
        for (final AlignmentBlock alignmentBlock : rec.getAlignmentBlocks()) {
            final int readStart = alignmentBlock.getReadStart();
            final int refStart = alignmentBlock.getReferenceStart();
            final int blockLength = alignmentBlock.getLength();

            for (int i = 0; i < blockLength; ++i) {
                // 0-based offset into the read of the current base
                final int readOffset = readStart + i - 1;

                // if the quality score cutoff is met, accumulate the base info
                if (dontCheckQualities || baseQualities.length == 0 || baseQualities[readOffset] >= minQuality) {
                    // 0-based offset from the aligned position of the first base in the read to the aligned position of the current base.
                    final int refOffset = refStart + i - accOffset;
                    accumulator.get(refOffset).add(new RecordAndOffset(rec, readOffset));
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
        // 0-based offset for the reference of the current base
        // the accumulator could have the previous position because an indel is accumulating
        int refBase = rec.getAlignmentStart() - getAccumulatorOffset(rec);
        // iterate over the cigar element
        for (int elementIndex = 0; elementIndex < cigar.size(); elementIndex++) {
            final CigarElement e = cigar.get(elementIndex);
            final CigarOperator operator = e.getOperator();
            if (operator.equals(CigarOperator.I)) {
                System.err.println("");
                // insertions are included in the previous base
                accumulator.get(refBase - 1).addInserted(rec, readBase);
                readBase += e.getLength();
            } else if (operator.equals(CigarOperator.D)) {
                // accumulate for each position that spans the deletion
                for (int i = 0; i < e.getLength(); i++) {
                    // the offset is the one for the previous base
                    accumulator.get(refBase + i).addDeleted(rec, readBase - 1);
                }
                refBase += e.getLength();
            } else {
                if (operator.consumesReadBases()) readBase += e.getLength();
                if (operator.consumesReferenceBases()) refBase += e.getLength();
            }
        }
    }

    /**
     * Ensure that the queue is populated and get the accumulator offset for the current record
     */
    private int getAccumulatorOffset(SAMRecord rec) {
        final SAMSequenceRecord ref = getReferenceSequence(rec.getReferenceIndex());
        final int alignmentStart = rec.getAlignmentStart();
        final int alignmentEnd = rec.getAlignmentEnd();
        final int alignmentLength = alignmentEnd - alignmentStart;
        // get the offset for an insertion if we are tracking them
        final int insOffset = (includeIndels && startWithInsertion(rec.getCigar())) ? 1 : 0;
        // if there is an insertion in the first base and it is not tracked in the accumulator, add it
        if (insOffset == 1 && accumulator.isEmpty()) {
            accumulator.add(new LocusInfo(ref, alignmentStart - 1));
        }
        // Ensure there are LocusInfos up to and including this position
        for (int i = accumulator.size(); i <= alignmentLength + insOffset; ++i) {
            accumulator.add(new LocusInfo(ref, alignmentStart + i - insOffset));
        }
        return alignmentStart - insOffset;
    }


    /**
     * @param rec        aligned SamRecord
     * @param readOffset offset from start of read
     * @param length     1, as object represents only one aligned base
     * @param refPos     -1,  as this filed isn't used for this implementation
     * @param type       null for this implementation
     * @return created RecordAndOffset
     */
    @Override
    RecordAndOffset createRecordAndOffset(SAMRecord rec, int readOffset, int length, int refPos) {
        return new RecordAndOffset(rec, readOffset);
    }

    /**
     * @param referenceSequence processed reference sequence
     * @param lastPosition      last processed reference locus position
     * @return <code>LocusInfo<T></code> for the lastPosition
     */
    @Override
    LocusInfo createLocusInfo(SAMSequenceRecord referenceSequence, int lastPosition) {
        return new LocusInfo(referenceSequence, lastPosition);
    }

    // --------------------------------------------------------------------------------------------
    // Helper methods below this point...
    // --------------------------------------------------------------------------------------------

    /**
     * Implementation of <code>AbstractRecordAndOffset</code> class for <code>SamLocusIterator</code>.
     * One object represents one aligned base of inner <code>SAMRecord</code>.
     */
    public static class RecordAndOffset extends AbstractRecordAndOffset {

        /**
         * @param record inner <code>SAMRecord</code>
         * @param offset 0-based offset from the start of <code>SAMRecord</code>
         */
        public RecordAndOffset(final SAMRecord record, final int offset) {
            super(record, offset);
        }
    }

    /**
     * The unit of iteration.  Holds information about the locus (the SAMSequenceRecord and 1-based position
     * on the reference), plus List of ReadAndOffset objects, one for each read that overlaps the locus;
     * two more List_s_ of ReadAndOffset objects include reads that overlap the locus with insertions and deletions
     * respectively
     */
    public static final class LocusInfo extends AbstractLocusInfo<RecordAndOffset> {

        private List<RecordAndOffset> deletedInRecord = null;
        private List<RecordAndOffset> insertedInRecord = null;

        /**
         * @param referenceSequence reference sequence at which the reads are aligned
         * @param position          position in the sequence at which the reads are aligned
         */
        public LocusInfo(SAMSequenceRecord referenceSequence, int position) {
            super(referenceSequence, position);
        }

        /**
         * Accumulate info for one read with a deletion
         */
        public void addDeleted(final SAMRecord read, int previousPosition) {
            if (deletedInRecord == null) {
                deletedInRecord = new ArrayList<>();
            }
            deletedInRecord.add(new RecordAndOffset(read, previousPosition));
        }

        /**
         * Accumulate info for one read with an insertion.
         * For this locus, the reads in the insertion are included also in recordAndOffsets
         */

        public void addInserted(final SAMRecord read, int firstPosition) {

            if (insertedInRecord == null) {
                insertedInRecord = new ArrayList<>();
            }
            insertedInRecord.add(new RecordAndOffset(read, firstPosition));
        }

        public List<RecordAndOffset> getDeletedInRecord() {
            return (deletedInRecord == null) ? Collections.emptyList() : Collections.unmodifiableList(deletedInRecord);
        }

        public List<RecordAndOffset> getInsertedInRecord() {
            return (insertedInRecord == null) ? Collections.emptyList() : Collections.unmodifiableList(insertedInRecord);
        }

        /**
         * @return the number of records overlapping the position, with deletions included if they are being tracked.
         */
        @Override
        public int size() {
            return super.size() + ((deletedInRecord == null) ? 0 : deletedInRecord.size());
        }


        /**
         * @return <code>true</code> if all the RecordAndOffset lists are empty;
         * <code>false</code> if at least one have records
         */
        @Override
        public boolean isEmpty() {
            return getRecordAndOffsets().isEmpty() &&
                    (deletedInRecord == null || deletedInRecord.isEmpty()) &&
                    (insertedInRecord == null || insertedInRecord.isEmpty());
        }
    }
}
