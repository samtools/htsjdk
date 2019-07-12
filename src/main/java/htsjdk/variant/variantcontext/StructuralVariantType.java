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

import java.util.HashMap;
import java.util.Map;

/**
 * Type of Structural Variant as defined in the VCF spec 4.2
 */
public enum StructuralVariantType {
    /** Deletion relative to the reference */
    DEL { public Allele asAllele() { return Allele.DEL; }},
    /** Insertion of novel sequence relative to the reference */
    INS { public Allele asAllele() { return Allele.INS; }},
    /** Region of elevated copy number relative to the reference */
    DUP { public Allele asAllele() { return Allele.DUP; }},
    /** Inversion of reference sequence */
    INV { public Allele asAllele() { return Allele.INV; }},
    /** Copy number variable region */
    CNV { public Allele asAllele() { return Allele.CNV; }},
    /** breakend structural variation. VCF Specification : <cite>An arbitrary rearrangement
     *  event can be summarized as a set of novel adjacencies.
     *  Each adjacency ties together two breakends.</cite>
     */
    BND { public Allele asAllele() { throw new UnsupportedOperationException("there is no particular allele that best represents the SVType BND"); }};

    private static Map<String, StructuralVariantType> byName = new HashMap<>(6);

    static {
        for (final StructuralVariantType type : values()) {
            byName.put(type.name().toUpperCase(), type);
        }
    }

    /**
     * Returns the SV type that matches a symbolic allele ID.
     * <p>
     *     As per the VCF 4.3 spec we take on the suggestion that the SV type of a
     *     symbolic would be the one indicated by the "top level" (i.e. the first) part of such symbolic id where
     *     parts are separated with colon characters (':'). So for example {@link #DUP} would be
     *     the SV type for {@code "<DUP>" or "<DUP:TANDEM>"} symbolic alleles but not for {@code "<:DUP>", "<DUP_TANDEM>" or "<DUPPY>"}.
     * </p>
     * <p>
     *     Here we ignore case so we consider "<dup>" or "<DUP>" or "<dUP>" equivalent.
     * </p>
     * @param id the id to get the SV type for.
     * @return it might return {@code null} indicating that we cannot determine a SV type
     *  that matches that symbolic ID.
     */
    public static StructuralVariantType fromSymbolicID(final String id) {
        final int colonIdx = id.indexOf(':');
        final String topLevel = colonIdx < 0 ? id : id.substring(0, colonIdx);
        return byName.get(topLevel.toUpperCase());
    }

    /**
     * Returns a symbolic allele that represents this SV type.
     * <p>
     * In the case of {@link #BND} it returns {@code null}, as breakend alleles contain information
     * specific to the breakend (mate position, prefix-suffix bases etc).
     * </p>
     * <p>
     *     For those SV types that may have "subtype" alleles, e.g. DUP has {@code <DUP>, <DUP:TANDEM>}) this method returns
     *     the least specific allele so in the sample example it would be {@code <DUP>}.
     * </p>
     *
     * @return null indicates that there is no allele that best presesent this SV type.
     */
     public abstract Allele asAllele();
}
