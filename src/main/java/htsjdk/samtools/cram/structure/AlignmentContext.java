package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;
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

    public static final int NO_ALIGNMENT_START = 0;
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

    public AlignmentContext(final ReferenceContext referenceContext,
                            final int alignmentStart,
                            final int alignmentSpan) {
        // We can't enforce valid alignment contexts, or even warn about them here, because there are too many
        // files floating around that were created with implementations based on versions of the spec that didn't
        // prescribe the valid start/span values for multi ref or unmapped slices, and SAMFileHeader containers.
        // So skip this ssince it results in lots of warnings.
        //validateAlignmentContext(false, referenceContext, alignmentStart, alignmentSpan);
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

    // Determine if these values result would in a valid alignment context
    //if isString = true, throws if not
    //Note: The spec does not prescribe what the alignment start/span for a SAMFileHeader container
    // should be, and only recently prescribed what they should be for multi-ref slices, so there are
    // many files out their with random values for those. There we can't really throw since there are
    // many files out their with random values in these places.
    public static void validateAlignmentContext(
            final boolean isStrict,
            final ReferenceContext referenceContext,
            final int alignmentStart,
            final int alignmentSpan) {
        switch (referenceContext.getType()) {
            case SINGLE_REFERENCE_TYPE:
                // note that it is technically possible to have an alignment span == 0, i.e., for a slice
                // with a single record where the sequence is SAMRecord.NULL_SEQUENCE, so we only check
                // alignment start
                if (alignmentStart < 0) {
                    final String errorString = String.format(
                            "Single-reference alignment context with an invalid start detected (index %d/start %d/span %d)",
                            referenceContext.getReferenceSequenceID(),
                            alignmentStart,
                            alignmentSpan);
                    if (isStrict) {
                        throw new CRAMException(errorString);
                    } else {
                        log.warn(errorString);
                    }
                }
                break;

            case UNMAPPED_UNPLACED_TYPE:
                // the spec requires start==0 and span==0 for unmapped, but also make a special exception
                // for EOF Containers, as required by the spec
                if (!(alignmentStart == NO_ALIGNMENT_START && alignmentSpan == NO_ALIGNMENT_SPAN) &&
                        !(alignmentStart == CramIO.EOF_ALIGNMENT_START && alignmentSpan == CramIO.EOF_ALIGNMENT_SPAN)) {
                    final String errorString = String.format(
                            "Unmapped/unplaced alignment context with invalid start/span detected (%d/%d)",
                            alignmentStart,
                            alignmentSpan);
                    if (isStrict) {
                        throw new CRAMException(errorString);
                    } else {
                        log.warn(errorString);
                    }
                }
                break;

            case MULTIPLE_REFERENCE_TYPE:
                if (alignmentStart != NO_ALIGNMENT_START || alignmentSpan != NO_ALIGNMENT_SPAN) {
                    final String errorString = String.format(
                            "Multi-reference alignment context with invalid start/span detected (%d/%d)",
                            alignmentStart,
                            alignmentSpan);
                    if (isStrict) {
                        throw new CRAMException(errorString);
                    } else {
                        log.warn(errorString);
                    }
                }
                break;

            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Alignment context with unknown reference context type: %s",
                                referenceContext.getType()));
        }
    }

    @Override
    public String toString() {
            return String.format(
                    "sequenceId=%s, start=%d, span=%d",
                    referenceContext,
                    alignmentStart,
                    alignmentSpan);
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
