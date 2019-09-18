package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.util.Log;

import java.util.Objects;

/**
 * An AlignmentContext represents mapping information related to a collection of reads, or a single
 * {@link CRAMRecord}, {@link Slice}, or {@link Container}.
 *
 * It contains a {@link ReferenceContext}, and if that context is of type SINGLE_REFERENCE_TYPE
 * then it also contains Alignment Start and Alignment Span values.
 */
public class AlignmentContext {
    private static final Log log = Log.getInstance(AlignmentContext.class);

    public static final int NO_ALIGNMENT_START = -1;
    public static final int NO_ALIGNMENT_SPAN = 0;

    public static final int NO_ALIGNMENT_END = SAMRecord.NO_ALIGNMENT_START; // SAMRecord uses this for alignmentEnd...

    public static final AlignmentContext MULTIPLE_REFERENCE_CONTEXT =
            new AlignmentContext(
                ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
                    //TODO: replace all these 0s with a constant
                    0,
                    NO_ALIGNMENT_SPAN);

    public static final AlignmentContext UNMAPPED_UNPLACED_CONTEXT =
            new AlignmentContext(
                    ReferenceContext.UNMAPPED_UNPLACED_CONTEXT,
                    0,
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

    public AlignmentContext(final ReferenceContext referenceContext,
                            final int alignmentStart,
                            final int alignmentSpan) {
        // TODO: We can't validate here because there are too many files floating around with containers
        // with non-spec conforming values (ie., unmapped with 0, 1, multi with -1/0
        // so instead we validate only on writer
        validateAlignmentContext(false, referenceContext, alignmentStart, alignmentSpan);
        this.referenceContext = referenceContext;
        this.alignmentStart = alignmentStart;
        this.alignmentSpan = alignmentSpan;
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

    // Determine if these values result in a good alignment context
    public static void validateAlignmentContext(
            final boolean isStrict,
            final ReferenceContext referenceContext,
            final int alignmentStart,
            final int alignmentSpan) {
        switch (referenceContext.getType()) {
            case SINGLE_REFERENCE_TYPE:
                if (alignmentStart == NO_ALIGNMENT_START) {
                    log.warn(
                            String.format(
                                    "Attempt to create a single-reference alignment context with an invalid start (index %d/start %d/span %d)",
                                    referenceContext.getReferenceSequenceID(),
                                    alignmentStart,
                                    alignmentSpan));
                } else if (alignmentSpan == NO_ALIGNMENT_SPAN) {
                    // it is possible to have an alignment span == 0, i.e., a slice with a single record where
                    // the sequence is SAMRecord.NULL_SEQUENCE
                    log.warn(String.format(
                            "Attempt to create a single-reference alignment context with an invalid span (index %d/start %d/span %d)",
                            referenceContext.getReferenceSequenceID(),
                            alignmentStart,
                            alignmentSpan));
                }
                break;

            case UNMAPPED_UNPLACED_TYPE:
                // the spec requires start==0 and span==0 for unmapped, but also make a special exception
                // for EOF Containers, as required by the spec
                if (!(alignmentStart == 0 && alignmentSpan == NO_ALIGNMENT_SPAN) &&
                        !(alignmentStart == CramIO.EOF_ALIGNMENT_START && alignmentSpan == CramIO.EOF_ALIGNMENT_SPAN)) {
                    final String errorString = String.format(
                            "Attempt to create an unmapped/unplaced alignment context with invalid start/span (%d/%d)",
                            alignmentStart,
                            alignmentSpan);
                    if (isStrict) {
                        throw new IllegalArgumentException(errorString);
                    } else {
                        log.warn(errorString);
                    }
                }
                break;

            case MULTIPLE_REFERENCE_TYPE:
                if (alignmentStart != 0 || alignmentSpan != NO_ALIGNMENT_SPAN) {
                    // THE spec does not prescribe what the alignment span for a SAMFIleHeader container
                    // should be, so there are many files out their with random values
                    log.warn(String.format(
                                    "Attempt to create a multi-reference alignment context with invalid start/span (%d/%d)",
                                    alignmentStart,
                                    alignmentSpan));
                }
                break;

            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Attempt to create an alignment context with unknown reference context type: %s",
                                referenceContext.getType()));
        }
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
                return String.format(format, referenceContext.getReferenceSequenceID(), alignmentStart, alignmentSpan);
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
