package htsjdk.samtools.cram.ref;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;

/**
 * Represents the current state of reference sequence processing.
 *
 * Are we handling MULTIPLE_REFERENCE_TYPE records (-2, from the CRAM spec)?
 *
 * Are we handling UNMAPPED_UNPLACED_TYPE records (-1, from the CRAM spec)?
 *
 * Or are we handing a known SINGLE_REFERENCE_TYPE sequence (0 or higher, from the CRAM spec))?
 *
 */
public class ReferenceContext implements Comparable<ReferenceContext> {
    private static final int MULTIPLE_REFERENCE_ID = -2;
    private static final int UNMAPPED_UNPLACED_ID = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX; // -1

    public static final ReferenceContext MULTIPLE_REFERENCE_CONTEXT = new ReferenceContext(MULTIPLE_REFERENCE_ID);
    public static final ReferenceContext UNMAPPED_UNPLACED_CONTEXT = new ReferenceContext(UNMAPPED_UNPLACED_ID);

    private final ReferenceContextType type;
    private final int serializableSequenceId;

    /**
     * Create a ReferenceContext from a serializable sequence ID
     * 0 or greater for single reference
     * -1 for unmapped-unplaced
     * -2 for multiple reference
     *
     * @param serializableSequenceId the sequence ID or sentinel value for constructing this ReferenceContext
     */
    public ReferenceContext(final int serializableSequenceId) {
        this.serializableSequenceId = serializableSequenceId;

        switch (serializableSequenceId) {
            case MULTIPLE_REFERENCE_ID:
                this.type = ReferenceContextType.MULTIPLE_REFERENCE_TYPE;
                break;
            case UNMAPPED_UNPLACED_ID:
                this.type = ReferenceContextType.UNMAPPED_UNPLACED_TYPE;
                break;
            default:
                if (serializableSequenceId >= 0) {
                    this.type = ReferenceContextType.SINGLE_REFERENCE_TYPE;
                } else {
                    throw new CRAMException("Invalid Reference Sequence ID: " + serializableSequenceId);
                }
        }
    }

    /**
     * Get the ReferenceContext type: SINGLE_REFERENCE_TYPE, UNMAPPED_UNPLACED_TYPE, or MULTIPLE_REFERENCE_TYPE
     * @return the {@link ReferenceContextType} enum
     */
    public ReferenceContextType getType() {
        return type;
    }

    /**
     * Get the ReferenceContext sequence ID or sentinel value, suitable for serialization:
     * 0 or greater for single reference
     * -1 for unmapped
     * -2 for multiple reference
     * @return the sequence ID
     */
    public int getSerializableId() {
        return serializableSequenceId;
    }

    /**
     * Get the valid sequence ID, if single-reference
     * @throws CRAMException if this is not single-ref
     * @return the sequence ID
     */
    public int getSequenceId() {
        if (type != ReferenceContextType.SINGLE_REFERENCE_TYPE) {
            final String msg = "This ReferenceContext does not have a valid reference sequence ID because its type is " +
                    type.toString();
            throw new CRAMException(msg);
        }

        return serializableSequenceId;
    }

    /**
     * Does this ReferenceContext refer to only unmapped-unplaced reads?
     *
     * Note: not a guarantee that the unmapped flag is set for all records
     * @see htsjdk.samtools.cram.structure.CramCompressionRecord#isPlaced()
     *
     * @return true if the ReferenceContext refers only to unmapped-unplaced reads
     */
    public boolean isUnmappedUnplaced() {
        return type == ReferenceContextType.UNMAPPED_UNPLACED_TYPE;
    }

    /**
     * Does this ReferenceContext refer to only unplaced reads (whether their unmapped flags are set or not)?
     * @return true if the ReferenceContext refers only to unplaced reads
     */
    public static boolean isUnmappedUnplaced(final int sequenceId) {
        return sequenceId == UNMAPPED_UNPLACED_CONTEXT.getSerializableId();
    }


    /**
     * Does this ReferenceContext refer to:
     * - reads placed on multiple references
     * - or a combination of placed and unplaced reads?
     * @return true if the ReferenceContext relates to reads placed on multiple references
     * or a combination of placed and unplaced reads
     */
    public boolean isMultipleReference() {
        return type == ReferenceContextType.MULTIPLE_REFERENCE_TYPE;
    }

    /**
     * Does this ReferenceContext refer to:
     * - reads placed on multiple references
     * - or a combination of placed and unplaced reads?
     * @return true if the ReferenceContext relates to reads placed on multiple references
     * or a combination of placed and unplaced reads
     */
    public static boolean isMultipleReference(final int sequenceId) {
        return sequenceId == MULTIPLE_REFERENCE_CONTEXT.getSerializableId();
    }
    /**
     * Does this ReferenceContext refer to reads placed on a single reference (whether their unmapped flags are set or not)?
     * @return true if all reads referred to by this ReferenceContext are placed on a single reference
     */
    public boolean isMappedSingleRef() {
        return type == ReferenceContextType.SINGLE_REFERENCE_TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReferenceContext that = (ReferenceContext) o;

        if (serializableSequenceId != that.serializableSequenceId) return false;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + serializableSequenceId;
        return result;
    }

    @Override
    public int compareTo(final ReferenceContext o) {
        return Integer.compare(this.serializableSequenceId, o.serializableSequenceId);
    }

    @Override
    public String toString() {
        switch (serializableSequenceId) {
            case MULTIPLE_REFERENCE_ID:
                return "MULTIPLE_REFERENCE_CONTEXT";
            case UNMAPPED_UNPLACED_ID:
                return "UNMAPPED_UNPLACED_CONTEXT";
            default:
                return "SINGLE_REFERENCE_CONTEXT: " + serializableSequenceId;
        }
    }
}
