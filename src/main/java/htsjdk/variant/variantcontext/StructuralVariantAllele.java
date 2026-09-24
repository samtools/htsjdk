package htsjdk.variant.variantcontext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable value type representing the structural-variant identity of an allele:
 * a major type (DEL, INS, DUP, INV, CNV, BND) and an ordered list of subtypes
 * (e.g. {@code ME}, {@code ALU}).
 *
 * <p>Instances are obtained via {@link #parse(String)} or {@link #of(StructuralVariantType, String...)}.
 * Two instances are equal when they have the same major type, the same subtype list and the same
 * {@link Breakend}, if any.
 */
public final class StructuralVariantAllele {

    /** Recommended subtype: mobile element */
    public static final String SUBTYPE_ME = "ME";
    /** Recommended subtype: tandem repeat */
    public static final String SUBTYPE_TR = "TR";
    /** Recommended subtype: tandem duplication */
    public static final String SUBTYPE_TANDEM = "TANDEM";

    private final StructuralVariantType type;
    private final List<String> subtypes;
    private final Breakend breakend;

    private StructuralVariantAllele(
            final StructuralVariantType type, final List<String> subtypes, final Breakend breakend) {
        this.type = type;
        this.subtypes = subtypes;
        this.breakend = breakend;
    }

    /** Returns the major structural variant type (never {@link StructuralVariantType#MIXED}). */
    public StructuralVariantType getType() {
        return type;
    }

    /** Returns the ordered, unmodifiable list of subtypes (may be empty; always empty for a breakend). */
    public List<String> getSubtypes() {
        return subtypes;
    }

    /**
     * Whether this is a breakend written in breakend notation ({@code G]17:198982]}, {@code .A}), whose type is
     * {@link StructuralVariantType#BND} and whose {@link #getBreakend()} is present.
     */
    public boolean isBreakend() {
        return breakend != null;
    }

    /** Whether this is a symbolic structural variant such as {@code <DEL:ME:ALU>}, rather than a breakend. */
    public boolean isSymbolic() {
        return !isBreakend();
    }

    /** The breakend's position, mate and orientation; empty unless {@link #isBreakend()}. */
    public Optional<Breakend> getBreakend() {
        return Optional.ofNullable(breakend);
    }

    /**
     * Returns the colon-joined form, e.g. {@code DEL:ME:ALU} or just {@code BND}.
     */
    @Override
    public String toString() {
        if (subtypes.isEmpty()) {
            return type.name();
        }
        final StringBuilder sb = new StringBuilder(type.name());
        for (final String sub : subtypes) {
            sb.append(':').append(sub);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof StructuralVariantAllele)) return false;
        final StructuralVariantAllele that = (StructuralVariantAllele) o;
        return type == that.type && subtypes.equals(that.subtypes) && Objects.equals(breakend, that.breakend);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subtypes, breakend);
    }

    /**
     * Parses a structural variant allele from a string. Accepts the same inputs as
     * {@link StructuralVariantType#parse(String)}, plus breakend notation (paired and
     * single breakends), which resolves to BND with no subtypes and its {@link Breakend}.
     * Text that uses breakend syntax but is not well-formed breakend notation is not a
     * structural variant.
     *
     * <p>Returns empty for non-SV symbolic alleles ({@code <NON_REF>}, {@code <*>},
     * {@code <FOO>}), sequence alleles, {@code .}, {@code *}, and null.
     *
     * @param s the string to parse
     * @return the structural variant allele, or empty
     */
    public static Optional<StructuralVariantAllele> parse(final String s) {
        if (s == null || s.isEmpty()) {
            return Optional.empty();
        }

        // Breakend notation: paired (e.g. G]17:198982] or ]13:123456]T) or single (e.g. .A or A.)
        if (isBreakendNotation(s)) {
            return Breakend.parse(s)
                    .map(breakend ->
                            new StructuralVariantAllele(StructuralVariantType.BND, Collections.emptyList(), breakend));
        }

        String text = s;
        if (text.charAt(0) == '<' && text.charAt(text.length() - 1) == '>') {
            text = text.substring(1, text.length() - 1);
        }

        final String[] parts = text.split(":", -1);
        final Optional<StructuralVariantType> majorType = StructuralVariantType.parse(parts[0]);
        if (majorType.isEmpty()) {
            return Optional.empty();
        }

        final List<String> subtypes = parts.length > 1
                ? Collections.unmodifiableList(Arrays.asList(Arrays.copyOfRange(parts, 1, parts.length)))
                : Collections.emptyList();

        return Optional.of(new StructuralVariantAllele(majorType.get(), subtypes, null));
    }

    /**
     * Creates a symbolic structural variant allele with the given major type and subtypes; a breakend
     * comes from {@link #parse(String)}.
     *
     * @param type the major type (must not be {@link StructuralVariantType#MIXED})
     * @param subtypes the subtypes (may be empty)
     * @return the structural variant allele
     * @throws IllegalArgumentException if the type is MIXED
     */
    public static StructuralVariantAllele of(final StructuralVariantType type, final String... subtypes) {
        if (type == StructuralVariantType.MIXED) {
            throw new IllegalArgumentException("Cannot create a StructuralVariantAllele with MIXED type");
        }
        final List<String> subtypeList = subtypes.length == 0
                ? Collections.emptyList()
                : Collections.unmodifiableList(Arrays.asList(subtypes.clone()));
        return new StructuralVariantAllele(type, subtypeList, null);
    }

    /** Detects paired breakend (contains [ or ]) or single breakend (.X or X. where X is a base). */
    private static boolean isBreakendNotation(final String s) {
        if (s.length() < 2) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '[' || c == ']') {
                return true;
            }
        }
        // Single breakend: .X... or ...X.
        if (s.charAt(0) == '.' && s.length() > 1 && isBase(s.charAt(1))) {
            return true;
        }
        if (s.charAt(s.length() - 1) == '.' && s.length() > 1 && isBase(s.charAt(s.length() - 2))) {
            return true;
        }
        return false;
    }

    private static boolean isBase(final char c) {
        return c == 'A' || c == 'C' || c == 'G' || c == 'T' || c == 'N' || c == 'a' || c == 'c' || c == 'g' || c == 't'
                || c == 'n';
    }
}
