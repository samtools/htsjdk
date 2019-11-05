/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram;

import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.AlignmentSpan;

/**
 * Class used to construct a BAI index for a CRAM file. Each BAIEntry represents a Slice or a subset
 * of a Slice (since MULTI_REF slices can contain records for more than one reference context), as these
 * need to be separated for BAI index creation).
 */
public class BAIEntry implements Comparable<BAIEntry> {
    final ReferenceContext referenceContext;
    final AlignmentSpan alignmentSpan;
    final long containerStartByteOffset;
    final long sliceByteOffsetFromCompressionHeaderStart;
    final int landmarkIndex;

    public BAIEntry(
            final ReferenceContext referenceContext,
            final AlignmentSpan alignmentSpan,
            final long containerStartByteOffset,
            final long sliceHeaderBlockByteOffset,
            final int landmarkIndex
    ) {
        // Note: a BAIEntry should never be made from a MULTI_REF reference context, because for a BAI index
        // MUTLI_REF slices need to be resolved down to constituent BAIEntry for each reference context, including
        // unmapped
        if (referenceContext.equals(ReferenceContext.MULTIPLE_REFERENCE_CONTEXT)) {
            throw new CRAMException("Attempt to create BAI entry from a multi ref context");
        } else if ((referenceContext.equals(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT) &&
                //unfortunately, many tests fail if we don't allow alignmentStart == -1 or alignmentSpan == 1
                //because there are files out there with these (non-epc conforming) values
                ((alignmentSpan.getAlignmentStart() != 0 && alignmentSpan.getAlignmentStart() != -1) ||
                        (alignmentSpan.getAlignmentSpan() != 0 && alignmentSpan.getAlignmentSpan() != 1)))) {
            throw new CRAMException(
                    String.format("Attempt to unmapped with non zero alignment start (%d) or span (%d)",
                            alignmentSpan.getAlignmentStart(),
                            alignmentSpan.getAlignmentSpan()));
        }
        this.referenceContext = referenceContext;
        this.alignmentSpan = alignmentSpan;
        this.containerStartByteOffset = containerStartByteOffset;
        this.sliceByteOffsetFromCompressionHeaderStart = sliceHeaderBlockByteOffset;
        this.landmarkIndex = landmarkIndex;
    }

    /**
     * Create a BAIEntry from a CRAIEntry (used to read a .crai as a .bai). Note that
     * there are no mapped/unmapped/unplaced counts present in the crai, which makes
     * BAIEntries created this way less full featured (i.e., wrong), but that is inherent
     * in the idea of converting a CRAi to a BAI to satisfy an index query).
     *
     * HTSJDK needs a native implementation satisfying queries using a CRAI directly.
     * see https://github.com/samtools/htsjdk/issues/851
     *
     * @param craiEntry
     */
    public BAIEntry(final CRAIEntry craiEntry) {
        this(new ReferenceContext(craiEntry.getSequenceId()),
                new AlignmentSpan(
                    craiEntry.getAlignmentStart(),
                    craiEntry.getAlignmentSpan(),
                    0,
                    0,
                    0),
                craiEntry.getContainerStartByteOffset(),
                craiEntry.getSliceByteOffsetFromCompressionHeaderStart(),
                0);
    }

    /**
     * Sort by numerical order of reference sequence ID, except that unmapped-unplaced reads come last
     *
     * For valid reference sequence ID (placed reads):
     * - sort by alignment start
     * - if alignment start is equal, sort by container offset
     * - if alignment start and container offset are equal, sort by slice offset
     *
     * For unmapped-unplaced reads:
     * - ignore (invalid) alignment start value
     * - sort by container offset
     * - if container offset is equal, sort by slice offset
     *
     * @param other the CRAIEntry to compare against
     * @return int representing the comparison result, suitable for ordering
     */
    @Override
    public int compareTo(final BAIEntry other) {
        // we need to call getReferenceContextID here since we might be unmapped
        if (getReferenceContext().getReferenceContextID() != other.getReferenceContext().getReferenceContextID()) {
            if (getReferenceContext().getReferenceContextID() == ReferenceContext.UNMAPPED_UNPLACED_ID) {
                return 1;
            }
            if (other.getReferenceContext().getReferenceContextID() == ReferenceContext.UNMAPPED_UNPLACED_ID) {
                return -1;
            }
            return Integer.compare(getReferenceContext().getReferenceSequenceID(), other.getReferenceContext().getReferenceSequenceID());
        }

        // only sort by alignment start values for placed entries
        // we need to call getReferenceContextID here since we might be unmapped
        if (getReferenceContext().getReferenceContextID() != ReferenceContext.UNMAPPED_UNPLACED_ID &&
                getAlignmentStart() != other.getAlignmentStart()) {
            return Integer.compare(getAlignmentStart(), other.getAlignmentStart());
        }

        if (getContainerStartByteOffset() != other.getContainerStartByteOffset()) {
            return Long.compare(getContainerStartByteOffset(), other.getContainerStartByteOffset());
        }

        return Long.compare(getSliceByteOffsetFromCompressionHeaderStart(), other.getSliceByteOffsetFromCompressionHeaderStart());
    };

    // Note: this should never be a multiple ref context
    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    public int getAlignmentStart() {
        return alignmentSpan.getAlignmentStart();
    }

    public int getAlignmentSpan() {
        return alignmentSpan.getAlignmentSpan();
    }

    public int getMappedReadsCount() {
        return alignmentSpan.getMappedCount();
    }

    public int getUnmappedReadsCount() {
        return alignmentSpan.getUnmappedCount();
    }
    public int getUnmappedUnplacedReadsCount() {
        return alignmentSpan.getUnmappedUnplacedCount();
    }

    public long getContainerStartByteOffset() {
        return containerStartByteOffset;
    }

    public long getSliceByteOffsetFromCompressionHeaderStart() {
        return sliceByteOffsetFromCompressionHeaderStart;
    }

    public int getLandmarkIndex() {
        return landmarkIndex;
    }

}
