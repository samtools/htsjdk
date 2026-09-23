package htsjdk.variant.variantcontext;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A breakend allele in VCF breakend notation, paired ({@code t[p[}, {@code t]p]}, {@code ]p]t}, {@code [p[t}) or
 * single ({@code t.}, {@code .t}), where {@code t} is the bases the ALT carries and {@code p} is the mate's
 * {@code contig:position}.
 *
 * <p>The notation reads this record's bases on the forward strand. A breakend {@linkplain #isJoinedAfter() joined
 * after} its bases reads them up to the record's position and then jumps to the mate; one {@linkplain
 * #isJoinedBefore() joined before} them arrives from the mate and then reads them from the record's position. The
 * mate's strand is relative to that reading: on the negative strand the mate's sequence is read towards lower
 * coordinates, as the reverse complement.
 */
public final class Breakend {
    private static final char SINGLE_BREAKEND = '.';
    private static final char EXTENDS_RIGHT = '[';
    private static final char EXTENDS_LEFT = ']';

    private final String bases;
    private final boolean joinedAfter;
    // null for a single breakend, whose other mate fields are then unused
    private final String mateContig;
    private final int matePosition;
    private final boolean mateAssemblyContig;
    private final boolean mateNegativeStrand;

    private Breakend(
            final String bases,
            final boolean joinedAfter,
            final String mateContig,
            final int matePosition,
            final boolean mateAssemblyContig,
            final boolean mateNegativeStrand) {
        this.bases = bases;
        this.joinedAfter = joinedAfter;
        this.mateContig = mateContig;
        this.matePosition = matePosition;
        this.mateAssemblyContig = mateAssemblyContig;
        this.mateNegativeStrand = mateNegativeStrand;
    }

    /**
     * Parses an allele in breakend notation. A contig name may contain colons: the position follows the last one.
     *
     * @param allele the ALT allele's text, e.g. {@code G]17:198982]}, {@code C[<ctg1>:1[} or {@code TCC.}
     * @return the breakend, or empty if the text is not well-formed breakend notation
     */
    public static Optional<Breakend> parse(final String allele) {
        if (allele == null || allele.length() < 2) {
            return Optional.empty();
        }
        final int open = indexOfBracket(allele, 0);
        return open < 0 ? parseSingle(allele) : parsePaired(allele, open);
    }

    /**
     * Parses a single breakend, {@code t.} (joined after {@code t}) or {@code .t} (joined before {@code t}).
     *
     * @param allele text of at least two characters that contains no square bracket
     * @return the breakend, or empty unless exactly one end of the text is a {@code .} and the rest is bases
     */
    private static Optional<Breakend> parseSingle(final String allele) {
        final boolean joinedAfter = allele.charAt(allele.length() - 1) == SINGLE_BREAKEND;
        final boolean joinedBefore = allele.charAt(0) == SINGLE_BREAKEND;
        if (joinedAfter == joinedBefore) {
            return Optional.empty();
        }
        final String bases = joinedAfter ? allele.substring(0, allele.length() - 1) : allele.substring(1);
        return areBases(bases)
                ? Optional.of(new Breakend(bases, joinedAfter, null, 0, false, false))
                : Optional.empty();
    }

    /**
     * Parses a paired breakend: {@code t[p[}, {@code t]p]}, {@code ]p]t} or {@code [p[t}. The text must hold exactly
     * two square brackets, both the same, enclosing the mate {@code p}, with the bases {@code t} (or {@code .} at a
     * telomere) on one side of them and nothing on the other. The side {@code t} is on gives the join, and the join
     * together with the bracket gives the mate's strand.
     *
     * @param allele the text to parse
     * @param open the index of the first square bracket in {@code allele}
     * @return the breakend, or empty if the text does not have that shape, {@code t} is not bases, or {@code p} is
     *     not a non-empty contig, a colon and a {@linkplain #parsePosition position}
     */
    private static Optional<Breakend> parsePaired(final String allele, final int open) {
        final char bracket = allele.charAt(open);
        final int close = allele.indexOf(bracket, open + 1);
        if (close < 0 || indexOfBracket(allele, open + 1) != close || indexOfBracket(allele, close + 1) >= 0) {
            return Optional.empty();
        }
        final String before = allele.substring(0, open);
        final String after = allele.substring(close + 1);
        if (before.isEmpty() == after.isEmpty()) {
            return Optional.empty();
        }
        final boolean joinedAfter = !before.isEmpty();
        final String written = joinedAfter ? before : after;
        // a breakend at a telomere has no bases of its own, written "."
        final String bases = written.equals(String.valueOf(SINGLE_BREAKEND)) ? "" : written;
        if (!bases.isEmpty() && !areBases(bases)) {
            return Optional.empty();
        }

        final String mate = allele.substring(open + 1, close);
        final int colon = mate.lastIndexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        final OptionalInt position = parsePosition(mate.substring(colon + 1));
        if (position.isEmpty()) {
            return Optional.empty();
        }
        String contig = mate.substring(0, colon);
        final boolean assemblyContig = contig.length() > 2 && contig.startsWith("<") && contig.endsWith(">");
        if (assemblyContig) {
            contig = contig.substring(1, contig.length() - 1);
        }
        // the mate is reverse complemented when both pieces extend the same way from the junction
        final boolean negativeStrand = joinedAfter ? bracket == EXTENDS_LEFT : bracket == EXTENDS_RIGHT;
        return Optional.of(
                new Breakend(bases, joinedAfter, contig, position.getAsInt(), assemblyContig, negativeStrand));
    }

    /**
     * Parses a mate's position. Only digits are accepted, so a sign, a space or an empty string is not a position.
     * Zero is, and so is a value past the contig's end, since 0 and N+1 are the virtual telomeric breakends of a
     * contig of length N.
     *
     * @param text the text after the last colon of the mate
     * @return the position, or empty if the text is not all digits or does not fit in an int
     */
    private static OptionalInt parsePosition(final String text) {
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return OptionalInt.empty();
            }
        }
        try {
            return OptionalInt.of(Integer.parseInt(text));
        } catch (final NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * The index of the first square bracket of either kind at or after {@code from}.
     *
     * @param s the text to search
     * @param from the index to start at
     * @return the bracket's index, or -1 if there is none
     */
    private static int indexOfBracket(final String s, final int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == EXTENDS_RIGHT || s.charAt(i) == EXTENDS_LEFT) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the text is one or more bases: A, C, G, T or N in either case, the bases {@link Allele} accepts.
     *
     * @param s the text to check
     * @return true if the text is a non-empty run of bases
     */
    private static boolean areBases(final String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case 'A', 'C', 'G', 'T', 'N', 'a', 'c', 'g', 't', 'n':
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * The bases the ALT carries at this breakend: the REF base and any inserted bases; empty for a breakend at a
     * telomere ({@code .[13:123457[}).
     */
    public String getBases() {
        return bases;
    }

    /**
     * The bases inserted at the junction: {@link #getBases()} without the REF base, which is the first base when
     * the breakend is joined after its bases and the last when it is joined before them; empty if none.
     */
    public String getInsertedBases() {
        if (bases.isEmpty()) {
            return bases;
        }
        return joinedAfter ? bases.substring(1) : bases.substring(0, bases.length() - 1);
    }

    /** Whether the mate is joined after this record's bases ({@code t[p[}, {@code t]p]}, {@code t.}). */
    public boolean isJoinedAfter() {
        return joinedAfter;
    }

    /** Whether the mate is joined before this record's bases ({@code ]p]t}, {@code [p[t}, {@code .t}). */
    public boolean isJoinedBefore() {
        return !joinedAfter;
    }

    /** Whether this is a single breakend ({@code t.}, {@code .t}), whose mate is unknown. */
    public boolean isSingle() {
        return mateContig == null;
    }

    /** The mate's contig, without the angle brackets of a contig in the assembly file; empty for a single breakend. */
    public Optional<String> getMateContig() {
        return Optional.ofNullable(mateContig);
    }

    /**
     * The mate's 1-based position, where its joined piece starts; 0 or N+1 for a virtual telomeric breakend of a
     * contig of length N. Empty for a single breakend.
     */
    public OptionalInt getMatePosition() {
        return isSingle() ? OptionalInt.empty() : OptionalInt.of(matePosition);
    }

    /** Whether the mate's contig is one in the assembly file, written in angle brackets ({@code C[<ctg1>:1[}). */
    public boolean isMateAssemblyContig() {
        return mateAssemblyContig;
    }

    /**
     * Whether the mate's sequence is read on the positive strand, towards higher coordinates ({@code t[p[},
     * {@code ]p]t}). False for a single breakend.
     */
    public boolean isMatePositiveStrand() {
        return !isSingle() && !mateNegativeStrand;
    }

    /**
     * Whether the mate's sequence is read on the negative strand, towards lower coordinates, as the reverse
     * complement ({@code t]p]}, {@code [p[t}). False for a single breakend.
     */
    public boolean isMateNegativeStrand() {
        return !isSingle() && mateNegativeStrand;
    }

    /** The breakend in VCF breakend notation. */
    @Override
    public String toString() {
        if (isSingle()) {
            return joinedAfter ? bases + SINGLE_BREAKEND : SINGLE_BREAKEND + bases;
        }
        final String local = bases.isEmpty() ? String.valueOf(SINGLE_BREAKEND) : bases;
        final String contig = mateAssemblyContig ? "<" + mateContig + ">" : mateContig;
        final char bracket = joinedAfter == mateNegativeStrand ? EXTENDS_LEFT : EXTENDS_RIGHT;
        final String mate = bracket + contig + ":" + matePosition + bracket;
        return joinedAfter ? local + mate : mate + local;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Breakend)) return false;
        final Breakend that = (Breakend) o;
        return joinedAfter == that.joinedAfter
                && matePosition == that.matePosition
                && mateAssemblyContig == that.mateAssemblyContig
                && mateNegativeStrand == that.mateNegativeStrand
                && bases.equals(that.bases)
                && Objects.equals(mateContig, that.mateContig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bases, joinedAfter, mateContig, matePosition, mateAssemblyContig, mateNegativeStrand);
    }
}
