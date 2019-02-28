package htsjdk.samtools.cram.ref;

/**
 * Is this {@link ReferenceContext} Single Reference,
 * Multiple Reference, or Unmapped?
 *
 * Section 8.5 of the CRAM spec defines the following values for the Slice Header sequence ID field:
 *      -2: Multiple Reference Slice
 *      -1: Unmapped-Unplaced Slice
 *      Any positive integer (including zero): Single Reference Slice
 */
public enum ReferenceContextType {
    MULTIPLE_REFERENCE_TYPE,
    UNMAPPED_UNPLACED_TYPE,
    SINGLE_REFERENCE_TYPE
}
