/*
 * Copyright (c) 2012 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package htsjdk.variant.variantcontext;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Immutable representation of an allele.
 *<p>
 * Types of alleles:
 *</p>
 *<pre>
 Ref: a t C g a // C is the reference base
 : a t G g a // C base is a G in some individuals
 : a t - g a // C base is deleted w.r.t. the reference
 : a t CAg a // A base is inserted w.r.t. the reference sequence
 </pre>
 *<p> In these cases, where are the alleles?</p>
 *<ul>
 * <li>SNP polymorphism of C/G  -&gt; { C , G } -&gt; C is the reference allele</li>
 * <li>1 base deletion of C     -&gt; { tC , t } -&gt; C is the reference allele and we include the preceding reference base (null alleles are not allowed)</li>
 * <li>1 base insertion of A    -&gt; { C ; CA } -&gt; C is the reference allele (because null alleles are not allowed)</li>
 *</ul>
 *<p>
 * Suppose I see a the following in the population:
 *</p>
 *<pre>
 Ref: a t C g a // C is the reference base
 : a t G g a // C base is a G in some individuals
 : a t - g a // C base is deleted w.r.t. the reference
 </pre>
 * <p>
 * How do I represent this?  There are three segregating alleles:
 * </p>
 *<blockquote>
 *  { C , G , - }
 *</blockquote>
 *<p>and these are represented as:</p>
 *<blockquote>
 *  { tC, tG, t }
 *</blockquote>
 *<p>
 * Now suppose I have this more complex example:
 </p>
 <pre>
 Ref: a t C g a // C is the reference base
 : a t - g a
 : a t - - a
 : a t CAg a
 </pre>
 * <p>
 * There are actually four segregating alleles:
 * </p>
 *<blockquote>
 *   { Cg , -g, --, and CAg } over bases 2-4
 *</blockquote>
 *<p>   represented as:</p>
 *<blockquote>
 *   { tCg, tg, t, tCAg }
 *</blockquote>
 *<p>
 * Critically, it should be possible to apply an allele to a reference sequence to create the
 * correct haplotype sequence:</p>
 *<blockquote>
 * Allele + reference =&gt; haplotype
 *</blockquote>
 *<p>
 * For convenience, we are going to create Alleles where the GenomeLoc of the allele is stored outside of the
 * Allele object itself.  So there's an idea of an A/C polymorphism independent of it's surrounding context.
 *
 * Given list of alleles it's possible to determine the "type" of the variation
 </p>
 <pre>
 A / C @ loc =&gt; SNP
 - / A =&gt; INDEL
 </pre>
 * <p>
 * If you know where allele is the reference, you can determine whether the variant is an insertion or deletion.
 * </p>
 * <p>
 * Alelle also supports is concept of a NO_CALL allele.  This Allele represents a haplotype that couldn't be
 * determined. This is usually represented by a '.' allele.
 * </p>
 * <p>
 * Note that Alleles store all bases as bytes, in **UPPER CASE**.  So 'atc' == 'ATC' from the perspective of an
 * Allele.
 * </p>
 * @author gatk_team.
 */
public interface Allele extends Comparable<Allele>, Serializable {

    /** A generic static NO_CALL allele for use */
    String NO_CALL_STRING = ".";
    /** A generic static SPAN_DEL allele for use */
    String SPAN_DEL_STRING = "*";
    /** Non ref allele representations */

    char SINGLE_BREAKEND_INDICATOR = '.';
    char BREAKEND_EXTENDING_RIGHT = '[';
    char BREAKEND_EXTENDING_LEFT = ']';
    char SYMBOLIC_ALLELE_START = '<';
    char SYMBOLIC_ALLELE_END = '>';


    String NON_REF_STRING = "<NON_REF>";
    String UNSPECIFIED_ALTERNATE_ALLELE_STRING = "<*>";
    Allele REF_A = new SimpleAllele("A", true);
    Allele ALT_A = new SimpleAllele("A", false);
    Allele REF_C = new SimpleAllele("C", true);
    Allele ALT_C = new SimpleAllele("C", false);
    Allele REF_G = new SimpleAllele("G", true);
    Allele ALT_G = new SimpleAllele("G", false);
    Allele REF_T = new SimpleAllele("T", true);
    Allele ALT_T = new SimpleAllele("T", false);
    Allele REF_N = new SimpleAllele("N", true);
    Allele ALT_N = new SimpleAllele("N", false);
    Allele SPAN_DEL = new SimpleAllele(SPAN_DEL_STRING, false);
    Allele NO_CALL = new SimpleAllele(NO_CALL_STRING, false);
    Allele NON_REF_ALLELE = new SimpleAllele(NON_REF_STRING, false);
    Allele UNSPECIFIED_ALTERNATE_ALLELE = new SimpleAllele(UNSPECIFIED_ALTERNATE_ALLELE_STRING, false);

    // for simple deletion, e.g. "ALT==<DEL>" (note that the spec allows, for now at least, alt alleles like <DEL:ME>)
    @SuppressWarnings("unused")
    Allele SV_SIMPLE_DEL = StructuralVariantType.DEL.toSymbolicAltAllele();
    // for simple insertion, e.g. "ALT==<INS>"
    @SuppressWarnings("unused")
    Allele SV_SIMPLE_INS = StructuralVariantType.INS.toSymbolicAltAllele();
    // for simple inversion, e.g. "ALT==<INV>"
    @SuppressWarnings("unused")
    Allele SV_SIMPLE_INV = StructuralVariantType.INV.toSymbolicAltAllele();
    // for simple generic cnv, e.g. "ALT==<CNV>"
    @SuppressWarnings("unused")
    Allele SV_SIMPLE_CNV = StructuralVariantType.CNV.toSymbolicAltAllele();
    // for simple duplication, e.g. "ALT==<DUP>"
    @SuppressWarnings("unused")
    Allele SV_SIMPLE_DUP = StructuralVariantType.DUP.toSymbolicAltAllele();

    /**
     * Create a new Allele that includes bases and if tagged as the reference allele if isRef == true.  If bases
     * == '-', a Null allele is created.  If bases ==  '.', a no call Allele is created. If bases ==  '*', a spanning deletions Allele is created.
     *
     * @param bases the DNA sequence of this variation, '-', '.', or '*'
     * @param isRef should we make this a reference allele?
     * @throws IllegalArgumentException if bases contains illegal characters or is otherwise malformated
     */
    static Allele create(byte[] bases, boolean isRef) {
        if ( bases == null )
            throw new IllegalArgumentException("create: the Allele base string cannot be null; use new Allele() or new Allele(\"\") to create a Null allele");

        if ( bases.length == 1 ) {
            // optimization to return a static constant Allele for each single base object
            switch (bases[0]) {
                case '.':
                    if ( isRef ) throw new IllegalArgumentException("Cannot tag a NoCall allele as the reference allele");
                    return NO_CALL;
                case '*':
                    if ( isRef ) throw new IllegalArgumentException("Cannot tag a spanning deletions allele as the reference allele");
                    return SPAN_DEL;
                case 'A': case 'a' : return isRef ? REF_A : ALT_A;
                case 'C': case 'c' : return isRef ? REF_C : ALT_C;
                case 'G': case 'g' : return isRef ? REF_G : ALT_G;
                case 'T': case 't' : return isRef ? REF_T : ALT_T;
                case 'N': case 'n' : return isRef ? REF_N : ALT_N;
                default: throw new IllegalArgumentException("Illegal base [" + (char)bases[0] + "] seen in the allele");
            }
        } else {
            return new SimpleAllele(bases.clone(), isRef);
        }
    }

    static Allele create(byte base, boolean isRef) {
        return create( new byte[]{ base }, isRef);
    }

    static Allele create(byte base) {
        return create( base, false );
    }

    static Allele extend(Allele left, byte[] right) {
        if (left.isSymbolic())
            throw new IllegalArgumentException("Cannot extend a symbolic allele");
        byte[] bases = new byte[left.length() + right.length];
        System.arraycopy(left.getBases(), 0, bases, 0, left.length());
        System.arraycopy(right, 0, bases, left.length(), right.length);

        return create(bases, left.isReference());
    }

    /**
     * @param bases  bases representing an allele
     * @return true if the bases represent the null allele
     */
    @Deprecated
    static boolean wouldBeNullAllele(byte[] bases) {
        return (bases.length == 1 && bases[0] == htsjdk.variant.vcf.VCFConstants.NULL_ALLELE) || bases.length == 0;
    }

    /**
     * @param bases bases representing an allele
     * @return true if the bases represent the SPAN_DEL allele
     */
    @Deprecated
    static boolean wouldBeStarAllele(byte[] bases) {
        return bases.length == 1 && bases[0] == htsjdk.variant.vcf.VCFConstants.SPANNING_DELETION_ALLELE;
    }

    /**
     * @param bases  bases representing an allele
     * @return true if the bases represent the NO_CALL allele
     */
    @Deprecated
    static boolean wouldBeNoCallAllele(byte[] bases) {
        return bases.length == 1 && bases[0] == htsjdk.variant.vcf.VCFConstants.NO_CALL_ALLELE;
    }

    /**
     * @param bases  bases representing an allele
     * @return true if the bases represent a symbolic allele, including breakpoints and breakends
     */
    @Deprecated
    static boolean wouldBeSymbolicAllele(byte[] bases) {
    	if ( bases.length <= 1 )
            return false;
        else {
            return bases[0] == Allele.SYMBOLIC_ALLELE_START || bases[bases.length - 1] == Allele.SYMBOLIC_ALLELE_END ||
                    wouldBeBreakpoint(bases) ||
                    wouldBeSingleBreakend(bases);
        }
    }

    /**
     * @param bases  bases representing an allele
     * @return true if the bases represent a symbolic allele in breakpoint notation, (ex: G]17:198982] or ]13:123456]T )
     */
    @Deprecated
    static boolean wouldBeBreakpoint(byte[] bases) {
        if (bases.length <= 1) {
            return false;
        }
        for (final byte base : bases) {
            if (base == Allele.BREAKEND_EXTENDING_LEFT || base == Allele.BREAKEND_EXTENDING_RIGHT) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param bases  bases representing an allele
     * @return true if the bases represent a symbolic allele in single breakend notation (ex: .A or A. )
     */
    @Deprecated
    static boolean wouldBeSingleBreakend(byte[] bases) {
        if ( bases.length <= 1 )
            return false;
        else {
            return bases[0] == Allele.SINGLE_BREAKEND_INDICATOR || bases[bases.length - 1] == Allele.SINGLE_BREAKEND_INDICATOR;
        }
    }

    /**
     * @param bases  bases representing a reference allele
     * @return true if the bases represent the well formatted allele
     */
    static boolean acceptableAlleleBases(String bases) {
        return acceptableAlleleBases(bases.getBytes(), true);
    }

    /**
     * @param bases bases representing an allele
     * @param isReferenceAllele is a reference allele
     * @return true if the bases represent the well formatted allele
     */
    static boolean acceptableAlleleBases(String bases, boolean isReferenceAllele) {
        return acceptableAlleleBases(bases.getBytes(StandardCharsets.UTF_8), isReferenceAllele);
    }

    /**
     * @param bases  bases representing a reference allele
     * @return true if the bases represent the well formatted allele
     */
    static boolean acceptableAlleleBases(byte[] bases) {
        return acceptableAlleleBases(bases, true);
    }

    /**
     *
     * @param bases bases representing an allele
     * @param isReferenceAllele true if a reference allele
     * @return true if the bases represent the well formatted allele
     */
    static boolean acceptableAlleleBases(byte[] bases, boolean isReferenceAllele) {
        if ( wouldBeNullAllele(bases) )
            return false;

        if ( wouldBeNoCallAllele(bases) || wouldBeSymbolicAllele(bases) )
            return true;

        if ( wouldBeStarAllele(bases) )
            return !isReferenceAllele;

        for (byte base :  bases ) {
            switch (base) {
                case 'A': case 'C': case 'G': case 'T':  case 'a': case 'c': case 'g': case 't': case 'N' : case 'n' :
                    break;
                default:
                    return false;
            }
        }

        return true;
    }

    /**
     * Returns an allele with the given bases and reference status.
     *
     * @param bases  bases representing an allele
     * @param isRef  is this the reference allele?
     */
    static Allele create(String bases, boolean isRef) {
        return create(bases.getBytes(), isRef);
    }

    /**
     * Creates a non-Ref allele.  @see Allele(byte[], boolean) for full information
     *
     * @param bases  bases representing an allele
     */
    static Allele create(String bases) {
        return create(bases, false);
    }

    /**
     * Creates a non-Ref allele.  @see Allele(byte[], boolean) for full information
     *
     * @param bases  bases representing an allele
     */
    static Allele create(byte[] bases) {
        return create(bases, false);
    }

    /**
     * Creates a new allele based on the provided one.  Ref state will be copied unless ignoreRefState is true
     * (in which case the returned allele will be non-Ref).
     *
     * This method is efficient because it can skip the validation of the bases (since the original allele was already validated)

     *
     * @param allele  the allele from which to copy the bases
     * @param ignoreRefState  should we ignore the reference state of the input allele and use the default ref state?
     */
    static Allele create(Allele allele, boolean ignoreRefState) {
        return new SimpleAllele(allele.getBases(), allele.isReference() && !ignoreRefState);
    }

    static boolean oneIsPrefixOfOther(final Allele a1, final Allele a2) {
        if ( a2.length() >= a1.length() )
            return a1.isPrefixOf(a2);
        else
            return a2.isPrefixOf(a1);
    }

    boolean isPrefixOf(final Allele other);

    /** @return true if this is the NO_CALL allele */
    boolean isNoCall();
    // Returns true if this is not the NO_CALL allele
    boolean isCalled();

    /** @return true if this Allele is the reference allele */
    boolean isReference();

    /** @return true if this Allele is not the reference allele */
    boolean isNonReference();

    /** @return true if this Allele is symbolic (i.e. no well-defined base sequence), this includes breakpoints and breakends */
    boolean isSymbolic();

    /** @return true if this Allele is a breakpoint ( ex: G]17:198982] or ]13:123456]T ) */
    boolean isBreakpoint();

    /** @return true if this Allele is a single breakend (ex: .A or A.) */
    boolean isSingleBreakend();

    // Returns a nice string representation of this object
    String toString();

    byte[] getBases();

    String getBaseString();

    String getDisplayString();

    byte[] getDisplayBases();

    boolean equals(Object other);

    int hashCode();

    boolean equals(Allele other, boolean ignoreRefState);

    boolean basesMatch(byte[] test);

    boolean basesMatch(String test);

    boolean basesMatch(Allele test);

    int length();

    /**
     *  @return true if Allele is either {@code <NON_REF>} or {@code <*>}
     */
    boolean isNonRefAllele();
}
