/*
 * The MIT License
 *
 * Copyright (c) 2016 Pierre Lindenbaum @yokofakun Institut du Thorax - Nantes - France
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package htsjdk.variant.variantcontext;

import java.util.Map;
import java.util.Optional;

/**
 * Major type of a structural variant as defined in the VCF specification.
 *
 * <p>The six concrete types (DEL, INS, DUP, INV, CNV, BND) are the first-level types
 * recognised in symbolic alleles and SVTYPE values. {@link #MIXED} represents a record
 * whose alleles or SVTYPE disagree on the major type; it is never produced by
 * {@link #parse(String)}.
 */
public enum StructuralVariantType {
    /** Deletion relative to the reference */
    DEL,
    /** Insertion of novel sequence relative to the reference */
    INS,
    /** Region of elevated copy number relative to the reference */
    DUP,
    /** Inversion of reference sequence */
    INV,
    /** Copy number variable region */
    CNV,
    /** Breakend structural variation */
    BND,
    /**
     * A record whose alleles or SVTYPE value name more than one distinct major type.
     * Never returned by {@link #parse(String)}.
     */
    MIXED;

    private static final Map<String, StructuralVariantType> BY_NAME = Map.of(
            "DEL", DEL,
            "INS", INS,
            "DUP", DUP,
            "INV", INV,
            "CNV", CNV,
            "BND", BND);

    /**
     * Parses a structural variant type from a symbolic allele string, an SVTYPE value,
     * or a breakend notation string. Accepts forms like {@code DEL}, {@code DEL:ME:ALU},
     * {@code <DEL:ME:ALU>}, and {@code <BND>}. Returns the major type (the part before
     * the first colon), or empty for anything that is not one of the six concrete names
     * ({@code <NON_REF>}, {@code <*>}, {@code <FOO>}, sequence text, {@code .}, {@code *}).
     *
     * <p>Case-sensitive. Never returns {@link #MIXED}.
     *
     * @param s the string to parse
     * @return the major structural variant type, or empty if the string is not a structural variant
     */
    public static Optional<StructuralVariantType> parse(final String s) {
        if (s == null || s.isEmpty()) {
            return Optional.empty();
        }

        String text = s;
        if (text.charAt(0) == '<' && text.charAt(text.length() - 1) == '>') {
            text = text.substring(1, text.length() - 1);
        }

        final int colon = text.indexOf(':');
        final String majorName = colon >= 0 ? text.substring(0, colon) : text;
        return Optional.ofNullable(BY_NAME.get(majorName));
    }

    /**
     * Creates an angle-bracketed symbolic alt allele for this type (e.g. {@code <DEL>}).
     *
     * @return the symbolic alt allele
     * @throws UnsupportedOperationException if this is {@link #BND} or {@link #MIXED}
     */
    Allele toSymbolicAltAllele() {
        if (this == BND) {
            throw new UnsupportedOperationException("BND type does not have angle bracketed alt allele");
        }
        if (this == MIXED) {
            throw new UnsupportedOperationException("MIXED type does not have angle bracketed alt allele");
        }
        return Allele.create("<" + name() + ">", false);
    }
}
