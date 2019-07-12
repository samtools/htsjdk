package htsjdk.variant.variantcontext;

/**
 * Possible breakend types.
 * <p>
 *     There are two <em>single</em> break types and four <em>paired</em> break end types.
 * </p>
 * <h3>Single breakend types</h3>
 * <p>Examples:<pre>
 *     #CHROM  POS  ID       REF    ALT  ...
 *     1       100  BND_1    T      T.   ...
 *     3       400  BND_2    G      .G   ...
 * </pre>
 * <p>
 *     There are two single break end types: {@link #SINGLE_FORK} and {@link #SINGLE_JOIN}. Both types
 *     refers how the new adjacency reads left to right atop the reference forward strand.
 * </p>
 *     <p>So {@link #SINGLE_FORK} is simply a fork out or branch off the reference sequence after
 *     the current position in the reference so the adjacent DNA would "dangle" off the right of the up-stream sequence
 *     leading to this point in the reference. (E.g. see {@code BND_1} above)</p>
 *     <p>In contrast, {@link #SINGLE_JOIN} would represent the opposite (yet still from the forward strand perspective,
 *     where the adjacent sequence would "dangle" left from the current position and where it joins the reference and
 *     continues downstream on the reference from that point. (E.g. see {@code BND_2} above)</p>
 * <h3>Paired breakend types</h3>
 * <p>There four paired types are proof to be more challenging to name and interpret. This enumeration
 * uses how the terms <em>"left"</em>, <em>"right"</em> and <em>"reverse (complement)"</em> are used in their definition in the VCF
 * to name them. </p>
 * <p>The following table show the correspondence
 * between type constants, encoding format and their description in the spec.</p>
 * 
 * <table>
 *     <thead>
 *     <tr>
 *         <th width="15%">Type constant</th>
 *         <th width="5%">Encoding format</th>
 *         <th width="15%">Example</th>
 *         <th width="55%">VCF 4.3 description</th>
 *     </tr>
 *     </thead>
 *     <tbody>
 *     <tr>
 *         <td>{@link #RIGHT_FORWARD}</td>
 *         <td align="center">t[p[</td>
 *         <td align="center">T[13:212121[</td>
 *         <td><i>"piece extending to the <b>right</b> of p is joined after t"</i></td>
 *     </tr>
 *     <tr>
 *         <td>{@link #LEFT_REVERSE}</td>
 *         <td align="center">t]p]</td>
 *         <td align="center">T]13:212121]</td>
 *         <td><i>"<b>reverse</b> comp piece extending <em>left</em> of p is joined after t"</i></td>
 *     </tr>
 *     <tr>
 *         <td>{@link #LEFT_FORWARD}</td>
 *         <td align="center">]p]t</td>
 *         <td align="center">]13:212121]T</td>
 *         <td><i>"piece extending to the <b>left</b> of p is joined before t"</i></td>
 *     </tr>
 *     <tr>
 *         <td>{@link #RIGHT_REVERSE}</td>
 *         <td align="center">[p[t</td>
 *         <td align="center">[13:212121[T</td>
 *         <td>"<b>reverse</b> comp piece extending <b>right</b> of p is joined before t"</td>
 *     </tr>
 *     </tbody>
 * </table>
 * <p>Notice that the enum constant name, as is the case with the VCF description of each type, makes reference as the location of the rest of the adjacent sequence with respect to
 * the mate breakend location.</p>
 */
public enum BreakendType {
    /**
     * Single left break-end, where the adjacency extends to the right of the enclosing location.
     */
    SINGLE_FORK(true) {                    // t.
        public BreakendType mateType() {
            return null;
        }
    },
    SINGLE_JOIN(false) {                   // .t
        public BreakendType mateType() {
            return null;
        }
    },
    RIGHT_FORWARD(false, true) {          // t[p[  piece extending to the right of p is joined after t
        public BreakendType mateType() {
            return LEFT_FORWARD;
        }
    },
    LEFT_REVERSE(true, false) {           // t]p]  reverse comp piece extending left of p is joined after t
        public BreakendType mateType() {
            return this;
        }
    },
    LEFT_FORWARD(true, true) {            // ]p]t  piece extending to the left of p is joined before t
        public BreakendType mateType() {
            return RIGHT_FORWARD;
        }
    },
    RIGHT_REVERSE(false, false) {        // [p[t  reverse comp piece extending right of p is joined before t
        public BreakendType mateType() {
            return this;
        }
    };

    private final boolean isBasePrefix;
    private final boolean isSingle;
    private final boolean isLeftEnd;
    private final boolean isForward;
    private final boolean isRightEnd;
    private final boolean isReverse;

    // Constructor for single types:
    BreakendType(final boolean basePrefix) {
        isSingle = true;
        isLeftEnd = isRightEnd = isForward = isReverse = false;
        isBasePrefix = basePrefix;
    }

    // Constructor for paired types:
    BreakendType(final boolean left, final boolean forward) {
        isRightEnd = !(isLeftEnd = left);
        isReverse = !(isForward = forward);
        isBasePrefix = left != forward;
        isSingle = false;
    }


    /**
     * Checks whether the encoding start with the reference base.
     * @return {@code true} iff the first character in the encoding is the reference (or snp) base.
     * Otherwise such character is placed at the end.
     */
    boolean startsWithBase() {
        return isBasePrefix;
    }

    /**
     * For paired breakend type, checks whether the adjacent DNA sequence comes from the
     * left (upstream) of the mate's position.
     * <p>
     *     For single type it returns false as it is not applicable.
     * </p>
     * @return {@code true} iff this is a paired type the tested condition is true.
     */
    public boolean isLeftSide() {
        return isLeftEnd;
    }

    /**
     * For paired breakend type, checks whether the adjacent DNA sequence comes from the
     * forward strand around the mate position.
     * <p>
     *     For single type it returns false as it is not applicable.
     * </p>
     * @return {@code true} iff this is a paired type the tested condition is true.
     */
    public boolean isForward() {
        return isForward;
    }

    /**
     * For paired breakend type, checks whether the adjacent DNA sequence comes from the
     * right (downstream) of the mate's position.
     * <p>
     *     For single type it returns false as it is not applicable.
     * </p>
     * @return {@code true} iff this is a paired type the tested condition is true.
     */
    public boolean isRightSide() {
        return isRightEnd;
    }

    /**
     * For paired breakend type, checks whether the adjacent DNA sequence is the reverse complement from the
     * reverse strand around the mate position.
     * <p>
     *     For single type it returns false as it is not applicable.
     * </p>
     * @return {@code true} iff this is a paired type the tested condition is true.
     */
    public boolean isReverse() {
        return isReverse;
    }

    /**
     * Checks whether this type is a single breakend type.
     * @return {@code true} iff this is indeed a single breakend type.
     */
    public boolean isSingle() {
        return isSingle;
    }

    /**
     * Checks whether this type is a paired breakend type.
     * @return {@code true} iff this is indeed a paired breakend type.
     */
    public boolean isPaired() {
        return !isSingle;
    }

    /**
     * Returns a paired type based on requirements on its left-right, forward-reverse status.
     * @param left whether the type must be left-sided ({@code true}) or right-sided ({@code false})
     * @param forward whether the type must be forward ({@code true}) or reverse ({@code false})
     * @return never {@code null}.
     */
    public static BreakendType paired(final boolean left, final boolean forward) {
        if (left) {
            return forward ? LEFT_FORWARD : LEFT_REVERSE;
        } else {
            return forward ? RIGHT_FORWARD : RIGHT_REVERSE;
        }
    }

    /**
     * Returns the type for the mate-breakend.
     * <p>
     *     When this cannot be determined (i.e. this is a single type) it returns {@code null}.
     * </p>
     * @return may return {@code null}. It does so with single types.
     */
    public BreakendType mateType() {
        switch (this) {
            case LEFT_FORWARD:
                return RIGHT_FORWARD;
            case RIGHT_FORWARD:
                return LEFT_FORWARD;
            case LEFT_REVERSE:
                return LEFT_REVERSE;
            case RIGHT_REVERSE:
                return RIGHT_REVERSE;
            default:
                return null;
        }
    }
}
