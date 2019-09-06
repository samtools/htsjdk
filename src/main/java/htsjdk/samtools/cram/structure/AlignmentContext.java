package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.ReferenceContext;

import java.util.Objects;

/**
 * An AlignmentContext represents mapping information related to a collection of reads, or a single
 * {@link CRAMRecord}, {@link Slice}, or {@link Container}.
 *
 * It contains a {@link ReferenceContext}, and if that context is of type SINGLE_REFERENCE_TYPE
 * then it also contains Alignment Start and Alignment Span values.
 */
public class AlignmentContext {
    public static final int NO_ALIGNMENT_START = -1;
    public static final int NO_ALIGNMENT_SPAN = 0;

    public static final int NO_ALIGNMENT_END = SAMRecord.NO_ALIGNMENT_START; // SAMRecord uses this for alignmentEnd...

    public static final AlignmentContext MULTIPLE_REFERENCE_CONTEXT =
            new AlignmentContext(
                ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                    NO_ALIGNMENT_START,
                    NO_ALIGNMENT_SPAN);

    public static final AlignmentContext UNMAPPED_UNPLACED_CONTEXT =
            new AlignmentContext(
                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                    NO_ALIGNMENT_START,
                    NO_ALIGNMENT_SPAN);

    public static final AlignmentContext EOF_CONTAINER_CONTEXT =
            new AlignmentContext(
                ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                    CramIO.EOF_ALIGNMENT_START, // defined by the spec...
                    CramIO.EOF_ALIGNMENT_SPAN); // defined by the spec...

    private final ReferenceContext referenceContext;

    // minimum alignment start of the reads represented here, using a 1-based coordinate system
    // or UNINITIALIZED if the ReferenceContext is not SINGLE_REFERENCE_TYPE
    private final int alignmentStart;
    private final int alignmentSpan;

    //TODO: are AlignmentContext created this way useful ?
    public AlignmentContext(final ReferenceContext referenceContext) {
        this(referenceContext, NO_ALIGNMENT_START, NO_ALIGNMENT_SPAN);
    }

    public AlignmentContext(final ReferenceContext referenceContext,
                            final int alignmentStart,
                            final int alignmentSpan) {

        // TODO: for now, accept anything until CRAMBAIndexer fake slices are eliminated
//        switch(referenceContext.getType()) {
//            case SINGLE_REFERENCE_TYPE:
//                if (alignmentStart == NO_ALIGNMENT_START || alignmentSpan == NO_ALIGNMENT_SPAN) {
//                    throw new IllegalArgumentException(
//                            String.format(
//                                    "Attempt to create a single reference alignment context with an invalid start/span (%d/%d)",
//                                    alignmentStart,
//                                    alignmentSpan));
//                }
//                break;
//
//            case UNMAPPED_UNPLACED_TYPE:
//                // make a special exception for EOF Containers, as required by the spec
//                if (!(alignmentStart == NO_ALIGNMENT_START && alignmentSpan == NO_ALIGNMENT_SPAN) &&
//                        !(alignmentStart == CramIO.EOF_ALIGNMENT_START && alignmentSpan == CramIO.EOF_ALIGNMENT_SPAN)) {
//                    throw new IllegalArgumentException(
//                            String.format(
//                            "Attempt to create an unmapped/unplaced alignment context with invalid start/span (%d/%d)",
//                            alignmentStart,
//                            alignmentSpan));
//                }
//                break;
//
//            case MULTIPLE_REFERENCE_TYPE:
//                if (alignmentStart != NO_ALIGNMENT_START || alignmentSpan != NO_ALIGNMENT_SPAN) {
//                    throw new IllegalArgumentException(
//                            String.format(
//                                    "Attempt to create a multi-reference alignment context with invalid start/span (%d/%d)",
//                                    alignmentStart,
//                                    alignmentSpan));
//                }
//                break;
//
//            default:
//                throw new IllegalArgumentException(
//                        String.format(
//                                "Attempt to create an alignment context with unknonwn reference context type: %s",
//                                referenceContext.getType()));
//        }

        this.referenceContext = referenceContext;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
    }

//        // alignment start and span values are only recorded if this is a single-reference context
//        if (!referenceContext.isMappedSingleRef() && (alignmentStart != NO_ALIGNMENT_START || alignmentSpan != NO_ALIGNMENT_SPAN)) {
//            // make a special exception for EOF Containers, as required by the spec
//            if (alignmentStart != CramIO.EOF_ALIGNMENT_START || alignmentSpan != CramIO.EOF_ALIGNMENT_SPAN) {
//                //throw new IllegalArgumentException(
//                System.out.println(        String.format(
//                                "Attempt to create an unmapped/unplaced alignment context with invalid start/span (%d/%d)",
//                                alignmentStart,
//                                alignmentSpan));
//            }
//        }
//        this.alignmentStart = alignmentStart;
//        this.alignmentSpan = alignmentSpan;
//    }

//        if (referenceContext.isMappedSingleRef()) {
//                this.alignmentStart = alignmentStart;
//                this.alignmentSpan = alignmentSpan;

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
