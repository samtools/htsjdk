package htsjdk.samtools.cram.structure;

/**
 * Bases from the base symbol space that are subject to substitution via the CRAM SubstitutionMatrix, in the order
 * in which they're serialized in the SubstitutionMatrix.
 */
enum SubstitutionBase {
    A('A'),
    C('C'),
    G('G'),
    T('T'),
    N('N');

    // The base this substitution represents
    private final byte base;

    /**
     * @param base the character represneting the base for this substitution
     */
    SubstitutionBase(final char base) {
        this.base = (byte) base;
    }

    /**
     * The base this substitution represents
     * @return the underlying base
     */
    public byte getBase() { return base; }
};
