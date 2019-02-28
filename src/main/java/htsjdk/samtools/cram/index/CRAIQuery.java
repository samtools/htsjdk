package htsjdk.samtools.cram.index;

import htsjdk.samtools.cram.ref.ReferenceContext;

/**
 * Parameters for querying a CRAI Index: reference sequence, alignment start, and alignment span
 *
 * Analogous to {@link htsjdk.samtools.QueryInterval} but incompatible because CRAM is 0-based
 */
public class CRAIQuery {
    protected final int sequenceId;
    protected final int alignmentStart;
    protected final int alignmentSpan;

    CRAIQuery(final int sequenceId, final int alignmentStart, final int alignmentSpan) {
        this.sequenceId = sequenceId;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
    }

    public boolean intersect(final CRAIQuery that) {
        if (this.sequenceId != that.sequenceId) {
            return false;
        }

        // TODO: enforce this on construction?
        if (this.sequenceId == ReferenceContext.UNMAPPED_UNPLACED_ID || this.sequenceId == ReferenceContext.MULTIPLE_REFERENCE_ID) {
            return false;
        }

        final int a0 = this.alignmentStart;
        final int a1 = that.alignmentStart;

        final int b0 = a0 + this.alignmentSpan;
        final int b1 = a1 + that.alignmentSpan;

        return Math.abs(a0 + b0 - a1 - b1) < (this.alignmentSpan + that.alignmentSpan);
    }
}
