package htsjdk.samtools.cram.structure;

/**
 * Bases from the base symbol space that are subject to substitution via the CRAM SubstitutionMatrix, in the order
 * in which they're serialized in the SubstitutionMatrix.
 */
enum SubstitutionBase {
    A,
    C,
    G,
    T,
    N;

    // The base this substitution represents
    private final byte base;

    SubstitutionBase() { this.base = (byte) name().charAt(0); }

    /**
     * The base this substitution represents
     * @return the underlying base
     */
    public byte getBase() { return base; }
};
