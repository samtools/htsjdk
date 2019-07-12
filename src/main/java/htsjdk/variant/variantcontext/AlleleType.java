package htsjdk.variant.variantcontext;

/**
 * Enumeration of possible allele types/categories.
 */
public enum AlleleType {
    /**
     * Alleles that are self-contained base sequences.
     * <p>
     *     Examples:  {@code "A", "ATT", "N", "CGAGT", "T", ...}
     * </p>
     */
    INLINE,
    /**
     * Symbolic alleles of the simples form {@code "<SYMBOLIC_ID>"}.
     * <p>
     *     Examples: {@code "<INS>", "<SYMBOLIC>", "<DUP:TANDEM>", ...}
     * </p>
     *
     */
    PLAIN_SYBMOLIC,
    /**
     * Breakend symbolic alleles.
     * <p>
     *     Examples: {@code "A[chr21:700123", ".G", "G.", "[chr1:6001235[T", ...}
     * </p>
     */
    BREAKEND,

    /**
     * Contig insertion symbolic alleles.
     */
    CONTIG_INSERTION,

    /**
     * Type for the special allele {@link Allele#NO_CALL} only.
     */
    NO_CALL,

    /**
     * Type for the special allele {@link Allele#SPAN_DEL} only.
     */
    SPAN_DEL,

    /**
     * Type for the special allele {@link Allele#UNSPECIFIED_ALT}.
     * <p>
     *     This type is shared only with its alternative version {@link Allele#NON_REF}.
     * </p>
     */
    UNSPECIFIC_ALT,

    /**
     * Represent other types not listed here.
     * <p>
     *     This type should be returned by those {@link Allele} implementation that do not conform to
     *     any the types above.
     * </p>
     */
    OTHER;
}
