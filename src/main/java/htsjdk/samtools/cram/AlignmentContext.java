package htsjdk.samtools.cram;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.EOFConstants;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.structure.Slice;

import java.util.Objects;

/**
 * An AlignmentContext represents mapping information related to a collection of reads, or a single
 * {@link CramCompressionRecord}, {@link Slice}, or {@link Container}.
 *
 * It contains a {@link ReferenceContext}, and if that context is of type SINGLE_REFERENCE_TYPE
 * then it also contains Alignment Start and Alignment Span values.
 */
public class AlignmentContext {
    public static final int UNINITIALIZED = -1;

    public static final AlignmentContext MULTIPLE_REFERENCE_CONTEXT = new AlignmentContext(
            ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, UNINITIALIZED, UNINITIALIZED);
    public static final AlignmentContext UNMAPPED_UNPLACED_CONTEXT = new AlignmentContext(
            ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, UNINITIALIZED, UNINITIALIZED);

    public static final AlignmentContext EOF_CONTAINER_CONTEXT = new AlignmentContext(
            ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, EOFConstants.EOF_ALIGNMENT_START, EOFConstants.EOF_ALIGNMENT_SPAN);

    private final ReferenceContext referenceContext;

    // minimum alignment start of the reads represented here, using a 1-based coordinate system
    // or UNINITIALIZED if the ReferenceContext is not SINGLE_REFERENCE_TYPE
    private final int alignmentStart;
    private final int alignmentSpan;

    public AlignmentContext(final ReferenceContext referenceContext,
                            final int alignmentStart,
                            final int alignmentSpan) {
        this.referenceContext = referenceContext;

        // alignment start and span values are only recorded if this is a single-reference context
        if (referenceContext.isMappedSingleRef()) {
            this.alignmentStart = alignmentStart;
            this.alignmentSpan = alignmentSpan;
        } else if (referenceContext.isUnmappedUnplaced() && alignmentStart == EOFConstants.EOF_ALIGNMENT_START) {
            // with a special exception for EOF Containers as required by the spec
            this.alignmentStart = EOFConstants.EOF_ALIGNMENT_START;
            this.alignmentSpan = EOFConstants.EOF_ALIGNMENT_SPAN;
        } else {
            this.alignmentStart = UNINITIALIZED;
            this.alignmentSpan = UNINITIALIZED;
        }
    }

    public ReferenceContext getReferenceContext() {
        return referenceContext;
    }

    public int getAlignmentStart() {
        return alignmentStart;
    }

    public int getAlignmentSpan() {
        return alignmentSpan;
    }

    @Override
    public String toString() {
        switch (referenceContext.getType()) {
            case MULTIPLE_REFERENCE_TYPE:
                return "AlignmentContext{MULTIPLE_REFERENCE_CONTEXT}";
            case UNMAPPED_UNPLACED_TYPE:
                return "AlignmentContext{UNMAPPED_UNPLACED_CONTEXT}";
            default:
                final String format = "AlignmentContext{referenceSequenceId=%s, alignmentStart=%d, alignmentSpan=%d}";
                return String.format(format, referenceContext.getSequenceId(), alignmentStart, alignmentSpan);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlignmentContext that = (AlignmentContext) o;
        return alignmentStart == that.alignmentStart &&
                alignmentSpan == that.alignmentSpan &&
                Objects.equals(referenceContext, that.referenceContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceContext, alignmentStart, alignmentSpan);
    }
}
