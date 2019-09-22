package htsjdk.samtools.cram.ref;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.CRAMException;

/**
 * A reference context defines the backing reference sequence for a given record, slice or container.
 * It is either a single-reference context, backed by a valid reference sequence ID; the sentinel value
 * ({@link ReferenceContext#UNMAPPED_UNPLACED_ID}) indicating unmapped/unplaced
 * ({@link ReferenceContextType#UNMAPPED_UNPLACED_TYPE}), or a multiple-reference context
 * ({@link ReferenceContext#MULTIPLE_REFERENCE_ID}) indicating a multi reference context
 * ({@link ReferenceContextType#MULTIPLE_REFERENCE_TYPE}).
 *
 * Are we handling {@link ReferenceContextType#MULTIPLE_REFERENCE_TYPE} records (-2,
 * ({@link ReferenceContext#MULTIPLE_REFERENCE_ID}) from the CRAM spec)?
 * Are we handling UNMAPPED_UNPLACED_TYPE records (-1, from the CRAM spec)?
 * Or are we handing a known SINGLE_REFERENCE_TYPE sequence (0 or higher, from the CRAM spec))?
 */
public class ReferenceContext implements Comparable<ReferenceContext> {
    public static final int UNMAPPED_UNPLACED_ID = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX; // -1
    public static final int MULTIPLE_REFERENCE_ID = -2;
    public static final int UNINITIALIZED_REFERENCE_ID = -3;

    public static final ReferenceContext MULTIPLE_REFERENCE_CONTEXT = new ReferenceContext(MULTIPLE_REFERENCE_ID);
    public static final ReferenceContext UNMAPPED_UNPLACED_CONTEXT = new ReferenceContext(UNMAPPED_UNPLACED_ID);

    private final ReferenceContextType type;

    // the values for this are either a valid (>=0) reference sequence ID, or one of the sentinel values indicating
    // unmapped/unplaced or multiple reference
    private final int referenceContextID;

    /**
     * Create a ReferenceContext from a value that is either a valid sequence ID, or a reference context
     * sentinel value:
     *
     * 0 or greater for single reference
     * -1 for unmapped-unplaced
     * -2 for multiple reference
     *
     * @param referenceContextID the sequence ID or sentinel value for constructing this ReferenceContext
     */
    public ReferenceContext(final int referenceContextID) {
        this.referenceContextID = referenceContextID;

        switch (referenceContextID) {
            case MULTIPLE_REFERENCE_ID:
                this.type = ReferenceContextType.MULTIPLE_REFERENCE_TYPE;
                break;
            case UNMAPPED_UNPLACED_ID:
                this.type = ReferenceContextType.UNMAPPED_UNPLACED_TYPE;
                break;
            default:
                if (referenceContextID >= 0) {
                    this.type = ReferenceContextType.SINGLE_REFERENCE_TYPE;
                } else {
                    throw new CRAMException("Invalid reference sequence ID: " + referenceContextID);
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
     * Get the ReferenceContext sequence ID, or, for unmapped or multiple context, a sentinel value suitable for
     * serialization:
     *
     * 0 or greater for single reference
     *  -1 for unmapped
     *  -2 for multiple reference
     * @return the sequence ID
     */
    public int getReferenceContextID() {
        return referenceContextID;
    }

    /**
     * Get the valid reference sequence ID. May only be called if this is reference context of type single-reference.
     * @throws CRAMException if this is not a single-ref reference context
     * @return the sequence ID
     */
    public int getReferenceSequenceID() {
        if (type != ReferenceContextType.SINGLE_REFERENCE_TYPE) {
            throw new CRAMException(
                    String.format("This ReferenceContext does not have a valid reference sequence ID because its type is %s",
                            type.toString()));
        }

        return referenceContextID;
    }

    /**
     * Does this ReferenceContext refer to only unmapped-unplaced reads?
     *
     * Note: not a guarantee that the unmapped flag is set for all records
     * @see htsjdk.samtools.cram.structure.CRAMRecord#isPlaced()
     *
     * @return true if the ReferenceContext refers only to unmapped-unplaced reads
     */
    public boolean isUnmappedUnplaced() {
        return type == ReferenceContextType.UNMAPPED_UNPLACED_TYPE;
    }

    /**
     * Does this ReferenceContext refer to:
     * - reads placed on multiple references
     * - or a combination of placed and unplaced reads?
     * @return true if the ReferenceContext relates to reads placed on multiple references
     * or a combination of placed and unplaced reads
     */
    public boolean isMultiRef() {
        return type == ReferenceContextType.MULTIPLE_REFERENCE_TYPE;
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

        if (referenceContextID != that.referenceContextID) return false;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + referenceContextID;
        return result;
    }

    @Override
    public int compareTo(final ReferenceContext o) {
        return Integer.compare(this.referenceContextID, o.referenceContextID);
    }

    @Override
    public String toString() {
        switch (referenceContextID) {
            case MULTIPLE_REFERENCE_ID:
                return "MULTIPLE_REFERENCE_CONTEXT";
            case UNMAPPED_UNPLACED_ID:
                return "UNMAPPED_UNPLACED_CONTEXT";
            default:
                return "SINGLE_REFERENCE_CONTEXT: " + referenceContextID;
        }
    }
}
