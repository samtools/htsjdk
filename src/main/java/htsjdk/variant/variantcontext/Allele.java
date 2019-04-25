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

import org.apache.commons.lang3.ArrayUtils;

import java.io.Serializable;
import java.util.Collection;

/**
 * Immutable representation of an allele.
 * <h3>Types of alleles</h3>
 * Alleles can be classified in three categories:
 * <dl>
 *     <dt><b>Inline</b> base sequence alleles</dt>
 *     <dd>This is the most common type when doing short variant calling and
 *     encompases all those alleles that have concrete base sequences of 1
 *     or more bases in length (e.g. {@code "A", "C", "ATTC", "N", "GGNT"}).
 *     Their name derives from the fact that their sequence (difference vs
 *     the reference) are fully enclosed in the variant entry in the .vcf</dd>
 *     <dt><b>Symbolic</b> alleles</dt>
 *     <dd>In contrast to <i>inline</i> alleels, <i>symbolic</i> alleles
 *     edition vs the reference ar no explicitly noted in the variant entry.
 *     Examples: {@code "<DEL>", "C[13:121231[", "C<asmctg1002>"} ...</dd>
 *     <dt><b>Special</b> alleles</dt>
 *     <dd>These may not represent an allele <i>per-se</i> but act as markers
 *     or place-holders in very specific situations described below.
 *     Currently there are just four: {@code ".", "*", "<NON_REF>" and "<*>"}
 *     where the last two are also considered <i>symbolic</i>.</dd>
 * </dl>
 *
 * Allele instances can also be classified in <b>reference</b> or
 * <b>alternative</b> based on where they represent the unchanged reference
 * sequence or the diverging variant. Currently only <i>inline</i> alleles can
 * be reference as well as alternative whereas <i>symbolic</i> and
 * <i>special</i> alleles can only be <i>alternative</i>.
 *
 * <h3>Inline alleles</h3>
 * <p>
 * Any valid sequence of bases is considered to encode a <i>inline</i> allele.
 * The valid bases are all 4 standard nucleotide bases {@code "A", "C", "G"}
 * and {@code "T"} plus the ambiguous {@code "N"}. Any other ambiguous code
 * IUPAC (e.g. {@code "Y", "W", "M", "X"} ...) as well as special characters
 * used in nucleotide alignments such as {@code '-' '.' '*'} are not allowed
 * in inline alleles; these are in fact found in the encoding or
 * <i>symbolic</i> and <i>special</i> alleles.
 * </p>
 * <p>
 *    You can use lower or upper case character for the base sequences. The
 *    case is ignore in all operations and it might not be preserved if the
 *    allele is re-encoded. Therefore when comparing base sequences
 *    {@code "aCgT"} would be considered equivalent to {@code "AcGt", "ACGT"}
 *     or {@code "acgt"}.
 * </p>
 * <p>
 *     Whether an allele represent an SNP or a (short) insertion or deletion
 *     will depend on how it compares against the reference allele, in case
 *     it is an alternative allele, or how it compares to every alternative
 *     allele if it is reference.
 * </p>
 * <p>
 *     Examples:<pre>
 *         #CHROM   POS         ID      REF ALT ...
 *         1        4567321     SNP_1   T   A
 *         1        4735812     INS_2   G   GACT
 *         2        878931      DEL_3   TAC T
 *         2        256002131   CPX_4   ATA C,A,CTA,ATATA,CTATA
 *     </pre>
 * </p>
 * <p>
 *     First entry above would represent a SNP or mutation from {@code "T" --> "A"},
 *     the second one is a insertion of three bases {@code "ACT"}, the third is a
 *     deletion of two bases {@code "CA"} and the last one represents a complex variant,
 *     a combination of a SNP {@code "A" --> "C"} together with several alternative
 *     number of repetitions o a two bases unit {@code "TA"}.
 * </p>
 * <h3>Symbolic alleles</h3>
 * <p>Symbolic alleles are further divided in <i>simple</i> (or <i>plain</i>)
 * symbolic alleles, <i>breakends</i>, <i>assembly contig insertions</i> and
 * the special <i>unspecified alternative allele</i>.</p>
 * <p>
 * <h4>Simple symbolic alleles</h4>
 * <p>
 * These are encoded with their <i>ID</i> within angled brackets ({@code <>}):
 * {@code "<DEL>", "<INS>", "<CNV>"} ... Valid <i>ID</i>s are those that do
 * not contain any angle brackets (for obvious reasons) nor whitespaces. The
 * <i>ID</i> indicates what type of variant this is however any additional
 * details would be provided by annotations in the enclosing variant
 * context.
 * </p>
 * <h4>Breakends</h4>
 * These indicate the presence of a new adjacency of the current location in
 * the reference with a distant piece of DNA.
 *
 * <h5>Single breakends</h5>
 * If the provenience of the distant piece of DNA is not part of the reference
 * or the assembly files or is simply undetermined we simply represented it
 * with a placeholder {@code '.'}
 * <p>
 *     Examples:
 *     <pre>
 *         #CHROM   POS         ID      REF ALT ...
 *         10       10000101    BND_1   T   T.
 *         22       20001111    BND_2   A   .A
 *         23       99999       BND_3   A   C.
 *         23       199999      BND_4   GCG G,GCG.
 *     </pre>
 * </p>
 * <p>
 *     In this case {@code BND_1} stands for an allele that in the
 *     result of branching off from the reference right after the current
 *     reference position. The adjacent DNA would branch off
 *     the current position in the reference. We represent this type of
 *     breakend ends programmatically as {@link BreakendType#SINGLE_FORK}.
 * </p>
 * <p>
 *     In contrast {@code BND_2} indicates that the adjacent DNA joints
 *     the reference right before the current position. The breakend type in
 *     this cale is {@link BreakendType#SINGLE_JOIN}.
 * </p>
 * <p>The other two examples, {@code "C."} in addition to branching
 *  off breakend there is also SNP at that position from {@code "T" to "G"} ({@code BND_3})
 *  and a close by deletion o f two bases ({@code BND_4}).
 *  </p>
 * <h5>Paired breakends</h5>
 * When we know the origin of the distant DNA we can represent it by enclosing
 * its location and orientation in the text encoding the allele:
 * representation.
 * <p>
 *     Examples:<pre>
 *         #CHROM   POS         ID      REF ALT ...
 *         10       10000101    BND_1   T   T[5:1000[
 *         12       30012       BND_1_1 A   A[<asm101>:5123[
 *         22       20001111    BND_2   A   [7:2000000[A
 *         23       99999       BND_3   A   A]3:20133]
 *         23       199999      BND_4   G   ]5:1020121]G
 *     </pre>
 * </p>
 * <p>
 *     The location of the distant adjacent sequence is indicates between
 *     the square brackets using the usual compact genomic location format {@code "ctg-id:pos"}.
 *     If the contig name is surrounded with angle brackets ({@code <>}) (e.g. {@code BND_1_1}), instead of the
 *     reference the distant sequence is located in a sequence in the adjoined assembly files.
 * </p>
 * <p>
 *     When the reference aligned base starts
 *     the encoding ({@code BND_1} and {@code BND_3}) it indicates that
 *     the haplotype starts up-stream in the reference (always on the forward strand) and the is followed by
 *     the distant adjacent sequence.</p>
 * <p> In contrasts, when the bases are at the end of the encoding the haplotype
 * would start on the distant DNA and join at that position of the reference and continue downstream on
 * the forward strand.</p>
 * <p>The direction on the distant sequence is determined by the orientation of the
 * brackets: {@code [...[} right from and {@code ]...]} left from the mate breakpoint.</p>
 * <p>However the strand is a bit more tricky; when the bracket point away from the base then the
 *    haplotype goes along the forward strand on the distant DNA sequence ({@code BND_1, BND_1_1} and {@code BND_4}), whereas
 *    when they point towards that base, it means that the haplotype would run along the reverse (complement) strand.</p>
 * <p>For further details see {@link BreakendType} documentation</p>
 *
 * <h4>Full assembly contig insertion</h4>
 * The indicate the insertion of an assembly sequence in full:
 * <p>
 *     Examples:<pre>
 *         #CHROM   POS         ID      REF ALT ...
 *         10       10000101    BND_1   T   T<asm101>
 *     </pre>
 * </p>
 * <p>
 *     The assembly contig id appears between angle brackets ({@code <>})
 *     after the base aligned against the reference.
 * </p>
 * <h3>Special alleles</h3>
 * <h4>No-call allele</h4>
 * <p>Used in genotypes to indicate the lack of a call for a concrete allele.</p>
 * <p>It is encoded with a single period {@code "."} are is not allowed in the {@code REF} or
 *    {@code ALT} column of the .vcf; a lonely {@code "."} on those column means that that column
 *    for that variant record is empty and has to been given a value of any kind</p>
 * <h4>Span-del allele</h4>
 * <p>This special allele encoded as a asterisk without any brackets or {@code "*"} indicates that for some
 * samples that variant may have a lower ploidy due to a spanning larger deletion.</p>
 * <h4>Unspecified alternative allele</h4>
 * <p>This special type represents an unknown or unobserved alternative allele
 * and it is often used to describe the uncertainty or confidence on the lack
 * of variation at that site.
 * </p>
 * <p>
 *     We currently support two version of this allele, {@link #UNSPECIFIED_ALT} encoded as {@code "<*>"},
 *     and {@link #NON_REF} encoded as {@code "<NON_REF>"}. Despite their differences as how they are
 *     expressed in the output or input .gvcf or .vcf, they are considered equivalent or
 *     equal programmatically (i.e {@code Allele.NON_REF.equals(Allele.USPECIFIED_ALT)} and <i>vice-versa</i>).
 * </p>
 */
public interface Allele extends BaseSequence, Serializable {

    ////////////////////////////////////////
    // Static instances of common allele encodings and symbolic IDs:
    //
    String NO_CALL_STRING = ".";
    char NO_CALL_CHAR = '.';
    String SPAN_DEL_STRING = "*";
    char SPAN_DEL_CHAR = '*';

    // Symbolics:
    String INS_ID = "INS";
    String DEL_ID = "DEL";
    String INV_ID = "INV";
    String DUP_ID = "DUP";
    String DUP_TANDEM_ID = "DUP:TANDEM";
    String INS_ME_ID = "INS:ME";
    String DEL_ME_ID = "DEL:ME";
    String CNV_ID = "CNV";

    ///////////////////////////////////////
    // Static instance for common alleles:
    //
    Allele NON_REF = AlleleUtils.registerSymbolic(UnspecifiedAlternativeAllele.NON_REF);
    String NON_REF_ID = NON_REF.getSymbolicID();
    Allele UNSPECIFIED_ALT = AlleleUtils.registerSymbolic(UnspecifiedAlternativeAllele.UNSPECIFIED_ALT);
    String UNSPECIFIED_ALT_ID = UNSPECIFIED_ALT.getSymbolicID();

    Allele INS = AlleleUtils.registerSymbolic(INS_ID, StructuralVariantType.INS);
    Allele DEL = AlleleUtils.registerSymbolic(DEL_ID, StructuralVariantType.DEL);
    Allele INV = AlleleUtils.registerSymbolic(INV_ID, StructuralVariantType.INV);
    Allele DUP = AlleleUtils.registerSymbolic(DUP_ID, StructuralVariantType.DUP);
    Allele DUP_TANDEM = AlleleUtils.registerSymbolic(DUP_TANDEM_ID, StructuralVariantType.DUP);
    Allele INS_ME =  AlleleUtils.registerSymbolic(INS_ME_ID, StructuralVariantType.INS);
    Allele DEL_ME = AlleleUtils.registerSymbolic(DEL_ME_ID, StructuralVariantType.DEL);
    Allele CNV = AlleleUtils.registerSymbolic(CNV_ID, StructuralVariantType.CNV);

    /**
     * @deprecated use {@link #NON_REF} instead.
     */
    @Deprecated
    Allele NON_REF_ALLELE = NON_REF;
    /**
     * @deprecated use {@link #UNSPECIFIED_ALT} instead.
     */
    @Deprecated
    Allele UNSPECIFIED_ALTERNATIVE_ALLELE = UNSPECIFIED_ALT;
    /**
     * @deprecated use {@link #INS} instead.
     */
    @Deprecated
    Allele SV_SIMPLE_INS = INS;
    /**
     * @deprecated use {@link #DEL} instead.
     */
    @Deprecated
    Allele SV_SIMPLE_DEL = DEL;
    /**
     * @deprecated use {@link #INV} instead.
     */
    @Deprecated
    Allele SV_SIMPLE_INV = INV;
    /**
     * @deprecated use {@link #DUP} instead.
     */
    @Deprecated
    Allele SV_SIMPLE_DUP = DUP;
    /**
     * @deprecated
     */
    @Deprecated
    Allele SV_SIMPLE_CNV = CNV;


    Allele SPAN_DEL = SpanDelAllele.INSTANCE;
    Allele NO_CALL = NoCallAllele.INSTANCE;

    // Single base inline alleles:

    Allele REF_A = new SingleBaseInLineAllele("A", true);
    Allele ALT_A = new SingleBaseInLineAllele("A", false);
    Allele REF_C = new SingleBaseInLineAllele("C", true);
    Allele ALT_C = new SingleBaseInLineAllele("C", false);
    Allele REF_G = new SingleBaseInLineAllele("G", true);
    Allele ALT_G = new SingleBaseInLineAllele("G", false);
    Allele REF_T = new SingleBaseInLineAllele("T", true);
    Allele ALT_T = new SingleBaseInLineAllele("T", false);
    Allele REF_N = new SingleBaseInLineAllele("N", true);
    Allele ALT_N = new SingleBaseInLineAllele("N", false);

    ////////////////////////////////////////////////////////////////
    // general creation methods from byte or char sequence encoding

    /**
     * Composes an allele from its encoding as an string.
     * @param bases bases representing an allele
     * @param isRef is this the reference allele?
     */
    static Allele create(final CharSequence bases, final boolean isRef) {
        return AlleleUtils.decode(bases, isRef);
    }

    static Allele create(final byte base, final boolean isRef) {
        return AlleleUtils.decode(base, isRef);
    }

    /**
     * Create an allele encoded by a single byte.
     * <p>
     *     The resulting exception won't be a reference, thus it would be an alternative if it applies.
     * </p>
     * @param base the byte encoding the allele.
     * @return never {@code null}.
     * @throws AlleleEncodingException if the byte provide is not a valid allele encoding.
     */
    static Allele create(final byte base) {
        return AlleleUtils.decode(base, false);
    }

    /**
     * Creates a non-Ref allele.  @see Allele(byte[], boolean) for full information
     *
     * @param bases bases representing an allele
     */
    static Allele create(final CharSequence bases) {
        return AlleleUtils.decode(bases, false);
    }

    /**
     * Creates a non-Ref allele.  @see Allele(byte[], boolean) for full information
     *
     * @param bases bases representing an allele
     */
    static Allele create(final byte[] bases) {
        return AlleleUtils.decode(bases, false, false);
    }

    static Allele create(final byte[] bases, final boolean isReference) {
        return AlleleUtils.decode(bases, isReference, false);
    }

    /**
     * @deprecated  use {@link #extend(byte[])} on the instance to extend directly.
     * @see #extend(byte[]).
     */
    @Deprecated
    static Allele extend(final Allele toExtend, final byte[] tail) {
        return toExtend.extend(tail);
    }

    /**
     * @param bases bases representing an allele
     * @return true if the bases represent the null allele
     * @deprecated  no clear substitute.
     */
    @Deprecated
    static boolean wouldBeNullAllele(final byte[] bases) {
        return (bases.length == 1 && bases[0] == htsjdk.variant.vcf.VCFConstants.NULL_ALLELE) || bases.length == 0;
    }

    /**
     * @param bases bases representing an allele
     * @return true if the bases represent the SPAN_DEL allele
     * @deprecated  no clear substitute
     */
    @Deprecated
    static boolean wouldBeStarAllele(final byte[] bases) {
        return bases.length == 1 && bases[0] == htsjdk.variant.vcf.VCFConstants.SPANNING_DELETION_ALLELE;
    }

    /**
     * @param bases bases representing an allele
     * @return true if the bases represent the NO_CALL allele
     * @deprecated no clear substitute
     */
    @Deprecated
    static boolean wouldBeNoCallAllele(final byte[] bases) {
        return bases.length == 1 && bases[0] == htsjdk.variant.vcf.VCFConstants.NO_CALL_ALLELE;
    }

    /**
     * @param bases bases representing an allele
     * @return true if the bases represent a symbolic allele, including breakpoints and breakends
     * @deprecated simply try to create the allele, catch exception and check type.
     */
    @Deprecated
    static boolean wouldBeSymbolicAllele(final byte[] bases) {
        if (bases.length <= 1)
            return false;
        else {
            return bases[0] == '<' || bases[bases.length - 1] == '>' ||
                    wouldBeBreakpoint(bases) || wouldBeSingleBreakend(bases);
        }
    }

    /**
     * @param bases bases representing an allele
     * @return true if the bases represent a symbolic allele in breakpoint notation, (ex: G]17:198982] or ]13:123456]T )
     * @deprecated use {@link Breakend#looksLikeBreakend}
     */
    @Deprecated
    static boolean wouldBeBreakpoint(final byte[] bases) {
        return Breakend.looksLikeBreakend(bases);
    }

    /**
     * @deprecated
     */
    @Deprecated
    static boolean wouldBeSingleBreakend(final byte[] bases) {
        return Breakend.looksLikeBreakend(bases) && bases[0] == '.' || bases[bases.length - 1] == '.';
    }

    /**
     * @param bases bases representing a reference allele
     * @return true if the bases represent the well formatted allele
     * @deprecated consider just create the Allele and catching exceptions if you need to.
     */
    @Deprecated
    static boolean acceptableAlleleBases(final String bases) {
        return acceptableAlleleBases(bases.getBytes(), true);
    }

    /**
     * @param bases             bases representing an allele
     * @param isReferenceAllele is a reference allele
     * @return true if the bases represent the well formatted allele
     * @deprecated
     */
    @Deprecated
    static boolean acceptableAlleleBases(final String bases, boolean isReferenceAllele) {
        return acceptableAlleleBases(bases.getBytes(), isReferenceAllele);
    }

    /**
     * @param bases bases representing a reference allele
     * @return true if the bases represent the well formatted allele
     * @deprecated consider alternatives.
     */
    @Deprecated
    static boolean acceptableAlleleBases(final byte[] bases) {
        return acceptableAlleleBases(bases, true);
    }

    /**
     * @param bases             bases representing an allele
     * @param isReferenceAllele true if a reference allele
     * @return true if the bases represent the well formatted allele
     * @deprecated consider alternatives.
     */
    @Deprecated
    static boolean acceptableAlleleBases(final byte[] bases, final boolean isReferenceAllele) {
        if ( wouldBeNullAllele(bases) )
            return false;

        if ( wouldBeNoCallAllele(bases) || wouldBeSymbolicAllele(bases) )
            return true;

        if ( wouldBeStarAllele(bases) )
            return !isReferenceAllele;

        for (byte base :  bases ) {
            switch (base) {
                case 'A': case 'C': case 'G': case 'T': case 'a': case 'c': case 'g': case 't': case 'N': case 'n':
                    break;
                default:
                    return false;
            }
        }

        return true;
    }

    ////////////////////////////////////////////////////////
    // Type enquiring methods:

    /**
     * Checks whether this allele represents a called allele.
     * <p>
     *     This method must return in all circumstances exactly the opposite to {@link #isCalled()}.
     * </p>
     *
     * is not the no-call allele.
     * <p>
     *   This method must return exactly the opposite to {@link #isNoCall()}.
     * </p>
     * @return true if this method is called.
     */
    // Returns true if this is not the NO_CALL allele
    boolean isCalled();
    /**
     * Checks whether this allele is a breakend (e.g. {@code ".C", "T.", "A[13:400121[", "]<ctg1>:123]C"}, etc).
     */
    boolean isBreakend();

    /**
     * Checks whether this allele is a paired breakend (e.g. {@code "A[13:400121[", "]<ctg1>:123]C"}, etc).
     */
    boolean isPairedBreakend();

    /**
     * Returns true if this allele is a contig insertion allele (e.g. {@code "C<asmctg1>"}).
     * @return {@code true} iff this is a contig-insertion allele.
     */
    boolean isContigInsertion();

    /**
     * Checks whether this allele, by itself, indicates the presence of an structural variation.
     * <p>
     *     However, a {@code false} return does not exclude the possibility that in fact the containing variant-context
     *     is a structural variant. For example a regular inline base sequence allele are not considered structural by
     *     default but they may represent a relative large insertion/deletion that may be intrepetted as a structural variant.
     * </p>
     * <p>
     *     If this method returns {@code true} then {@link #getStructuralVariantType()} must not return {@code null}.
     *     Likewise if this method returns {@code false} then {@link #getStructuralVariantType()} must return {@code null}.
     * </p>
     * @return {@code true} for alleles that imply the presence of new adjacencies beyond insertion or deletion of
     * a few bases.
     */
    boolean isStructural();

    /**
     * Checks whether this allele represents a no-call.
     * <p>
     *     This method must return exactly the opposite to {@link #isCalled()}.
     * </p>
     * @return true iff this is (or is equal to) the {@link #NO_CALL} allele.
     */
    default boolean isNoCall() {
        return this.equals(NO_CALL);
    }

    /**
     * Checks whether this allele is a simple and self-contained sequence of bases. For example ("A", "AAAT", etc).
     * Anything else including symbolic alleles, span_deletions, no-call, breakends would return {@code false}.
     */
    boolean isInline();

    /**
     * @return true if this Allele is symbolic (i.e. no well-defined base sequence), this includes breakpoints and breakends
     */
    boolean isSymbolic();


    /**
     * @return true if this Allele is a single breakend (ex: .A or A.)
     */
    boolean isSingleBreakend();

    /**
     * Checks whether this is a span-deletion marking allele.
     * @return {@code true} iff it is an span-del.
     */
    boolean isSpanDeletion();

    /**
     * Checks whether this allele is either the {@link #NON_REF} or the {@link #UNSPECIFIED_ALT}.
     */
    boolean isUnspecifiedAlternative();

    ////////////////////////////////////////////////////////
    // Reference <--> Alternative status enquire and conversion methods.

    /**
     * Checks whether this allele is an alternative allele.
     * @return never {@code true}.
     */
    boolean isAlternative();

    /**
     * Checks whether this allele is the reference allele.
     * <p>
     *     This method must return exactly the opposite to {@link #isAlternative()}.
     * </p>
     * @return true iff this Allele is the reference allele
     */
    boolean isReference();

    /**
     * Returns the "alternative" version of this allele.
     * <p>
     *     Most type of alleles can (or must) be alternative alleles except for {@link #NO_CALL} that is not
     *     and can't become either reference nor alternative. Therefore such call on {@link #NO_CALL} will
     *     result in a {@link UnsupportedOperationException}.
     * </p>
     * @return never {@code null}.
     * @throws UnsupportedOperationException if this kind of allele cannot be an alternative allele.
     */
    Allele asAlternative();

    /**
     * Returns a reference version of this allele.
     * <p>
     *     In practice this only applies by inline alleles as the rest
     *     can't never be reference. Consequently conversion on these will fail.
     * </p>
     * @return never {@code null}.
     * @throws UnsupportedOperationException if this kind of allele cannot be a reference allele.
     */
    Allele asReference();

    //////////////////////////////////////////////
    // Allele type specific information accessors:

    /**
     * Returns the SV type that best matches this allele if any.
     *
     * @see #isStructural()
     * @return {@code null} for those alleles that do not have a corresponding SV type.
     */
    StructuralVariantType getStructuralVariantType();

    /**
     * Returns the ID/name of a symbolic allele. It return {@code null if it does not apply.}
     * <p>
     *     Typically the symbolic ID is the string between the angled brackets, so for example for {@code <DEL>} it is {@code "DEL"},
     *     for {@code <DUP:TANDEM>} is {@code "DUP:TANDEM"}, for {@code <*>} is {@code "*"} and so forth.
     * </p>
     * <p>
     *     For those symbolic alleles whose string encoding is not of the form {@code <NAME>} this method will return {@code null}.
     * </p>
     * <ul>
     * <li>
     *     So, for example breakend alleles such as {@code "A[13:123444[", ".A", ".[1:110000[", "T]7:2300000]"} etc. this method will return null. Notice however
     *     that {@link #isStructural()} would return {@code true} and {@link #getStructuralVariantType()} would be equal to {@link StructuralVariantType#BND}.
     * </li>
     * <li>
     *     Similarly for assembly contig insertions such as {@code "C<ctg1>"} this method returns also {@code null} but {@link #getStructuralVariantType()} would be equal to {@link StructuralVariantType#INS}.
     * </li>
     * </ul>
     * @return may be {@code null}.
     */
    String getSymbolicID();

    /**
     * Returns the equivalent breakend for this allele.
     *
     * @return {@code null} if this allele cannot be interpreted as a breakend.
     * @throws IllegalArgumentException if it looks like a breakend but it has some spec formatting
     *                                  issues thus indicating a bug or format violiation somewhere else.
     */
    Breakend asBreakend();

    /**
     * Returns the contig-id for those allele that contain one.
     * That is typically limited to contig insertion alleles and
     * paired breakend alleles.
     * <p>
     *     For other allele types  it will {@code null}.
     * </p>
     * @return may be {@code null}
     */
    String getContigID();

    /**
     * Checks whether this allele indeed have a contig ID.
     * <p>
     *     The following condition must always be met:
     *     <code>hasContigID() == getContigID() != null</code>
     * </p>
     * @see #getContigID()
     * @return {@code true} iff {@code getContigID() != null}.
     */
    boolean hasContigID();

    /////////////////////////////////////////
    // Encoding and display methods

    /**
     * Returns the encoding for the allele as a string.
     * <p>
     *     It is guaranteed that {@code Allele.create(A.encodeAsString(), A.isReference()).equals(A)}.
     * </p>
     * @return never {@code null}.
     */
    String encodeAsString();

    /**
     * Returns the encoding for the allele as a sequence of characters represented in bytes.
     * <p>
     *     It is guaranteed that {@code Allele.create(A.encodeAsBytes(), A.isReference()).equals(A)}.
     * </p>
     * <p>
     *     Change in the returned array won't change the state of this allele as is new every time this method in called
     *     so the invoking code is free to modify it or re-purpose it.
     * </p>
     * @return never {@code null}.
     */
    byte[] encodeAsBytes();

    /**
     * Returns an string containing only the bases in this allele.
     * <p>
     *     For those alleles that don't contain bases (e.g. plain symbolic alleles like {@code "<DEL>"}) thi method
     *     will return an empty string.
     * </p>
     * @return never {@code null}, but an empty string if there is no base in this allele.
     */
    String getBaseString();

    /////////////////////////////
    // Other operations.

    /**
     * Returns true if this and other are equal.  If ignoreRefState is true, then doesn't require
     * both alleles to have the same reference/alternative status.
     *
     * @param other          allele to compare to
     * @param ignoreRefState if true, ignore ref state in comparison
     * @return true if this and other are equal
     */
    boolean equals(final Allele other, final boolean ignoreRefState);

    /**
     * Attempts to extend this allele with the bases provided.
     * <p>
     *     This operation only make sense and is supported in sequence inline alleles.
     * </p>
     * @param tail the based to add at the end.
     * @throws UnsupportedOperationException if this type of allele does not support extension.
     * @throws NullPointerException if {@code tail} is {@code null}.
     * @throws AlleleEncodingException if {@code tail} contain invalid bases.
     * @return never {@code null}.
     */
    Allele extend(final byte[] tail);

    /////////////////////////////
    // Deprecated methods:



    /**
     * Creates a new allele based on the provided one.  Ref state will be copied unless ignoreRefState is true
     * (in which case the returned allele will be non-Ref).
     * <p>
     * This method is efficient because it can skip the validation of the bases (since the original allele was already validated)
     *
     * @param allele         the allele from which to copy the bases
     * @param ignoreRefState should we ignore the reference state of the input allele and use the default ref state?
     * @deprecated use {@code #asAlternative} or {@code #asReference} to obtain the same allele with the
     * other reference status.
     */
    @Deprecated
    static Allele create(final Allele allele, final boolean ignoreRefState) {
        if (allele.isAlternative() || !ignoreRefState) {
            return allele;
        } else {
            return allele.asAlternative();
        }
    }

    /**
     * @return true if this Allele is a breakpoint ( ex: G]17:198982] or ]13:123456]T )
     * @deprecated please use {@link #isBreakend()} instead.
     */
    //todo we need to choose either breakend or breakpoint, not both.
    @Deprecated
    boolean isBreakpoint();

    /**
     * @return true if Allele is either {@link #NON_REF} or {@code #USPECIFIED_ALT}.
     * @deprecated use {@link #isUnspecifiedAlternative()} instead.
     */
    @Deprecated
    default boolean isNonRefAllele() {
        return isUnspecifiedAlternative();
    }

    /**
     * @return true if this Allele is not the reference allele
     * @deprecated use {@link #isAlternative()} instead.
     */
    @Deprecated
    boolean isNonReference();

    /**
     * @deprecated consider <code>allAlleles.stream().filter(a -> a.equalBases(alleleBases)).findFirst().orElse(...)</code>
     */
    @Deprecated
    static Allele getMatchingAllele(final Collection<Allele> allAlleles, final byte[] alleleBases) {
        for (Allele a : allAlleles) {
            if (a.basesMatch(alleleBases)) {
                return a;
            }
        }

        if (wouldBeNoCallAllele(alleleBases))
            return NO_CALL;
        else
            return null;    // couldn't find anything
    }

    /**
     * @deprecated is a very peculiar operation only used in one place in GATK. Can be substituted by
     *   a.equalBases(0, b, 0, Math.min(a.numberOfBases(), b.numberOfBases())
     */
    @Deprecated
    static boolean oneIsPrefixOfOther(final Allele a1, final Allele a2) {
        return a1.isInline() && a2.isInline() &&
                a1.equalBases(0, a2, 0, Math.min(a1.numberOfBases(), a2.numberOfBases()));
    }

    /**
     * Same as {@link #encodeAsString()}.
     *
     * @return the allele string representation
     * @deprecated  use {@link #encodeAsString()}
     */
    @Deprecated
    default String getDisplayString() {
        return encodeAsString();
    }

    /**
     * Same as {@link #encodeAsBytes}.
     *
     * @return the allele string representation
     * @deprecated
     */
    @Deprecated
    default byte[] getDisplayBases() {
        return encodeAsBytes();
    }

    /** @deprecated  use {@link #copyBases} instead. */
    @Deprecated
    default byte[] getBases() {
        return copyBases();
    }

    /**
     * @param test bases to test against
     * @return true if this Allele contains the same bases as test, regardless of its reference status; handles Null and NO_CALL alleles
     * @deprecated use {@link #equalBases(byte[])} instead.
     */
    @Deprecated
    default boolean basesMatch(byte[] test) {
        return equalBases(test);
    }

    /**
     * @param test bases to test against
     * @return true if this Allele contains the same bases as test, regardless of its reference status; handles Null and NO_CALL alleles
     * @deprecated use {@link #equalBases(CharSequence)} instead.
     */
    @Deprecated
    default boolean basesMatch(String test) {
        return equalBases(test);
    }

    /**
     * @param test allele to test against
     * @return true if this Allele contains the same bases as test, regardless of its reference status; handles Null and NO_CALL alleles
     * @deprecated use {@link #equalBases(BaseSequence)}
     */
    @Deprecated
    default boolean basesMatch(final Allele test) {
        return equalBases(test);
    }

    /**
     * @return the length of this allele.  Null and NO_CALL alleles have 0 length.
     * @deprecated use {@link #numberOfBases()}, bad naming since the actual allele might be longer
     * than the number of bases, also very conflictive for a very specific interface.
     */
    @Deprecated
    default int length() {
        return numberOfBases();
    }

    ////////////////////////////////////////
    // Allele type specific creation methods

    /**
     * Returns a inline base sequence allele given its based encoded in an
     * @param bases sequences of bases for this allele
     * @param isRef whether the allele must be reference ({@code true}) or alternative ({@code false}).
     * @return never {@code null}.
     * @throws NullPointerException if {@code bases} is {@code null}.
     * @throws AlleleEncodingException if {@code bases} is empty or contains values that are not considered valid bases.
     */
    static Allele inline(final CharSequence bases, final boolean isRef) {
        if (bases.length() == 0) {
            throw AlleleEncodingException.emptyEncoding();
        } else if (bases.length() == 1) {
            return AlleleUtils.decodeSingleBaseInline((byte) bases.charAt(0), isRef);
        } else {
            return new MultiBaseInLineAllele(AlleleUtils.extractBases(bases), isRef);
        }
    }

    /**
     * Returns an single base inline sequence allele.
     * @param base the base for such an allele.
     * @param isRef whether the returned allele must be reference ({@code true}) or alternative {@code false}}
     * @return never {@code null}.
     * @throws AlleleEncodingException if {@code base} is not a valid base.
     */
    static Allele inline(final byte base, final boolean isRef) {
        return AlleleUtils.decodeSingleBaseInline(base, isRef);
    }

    /**
     * Returns an single base inline sequence allele.
     * @param bases the bases for such an allele.
     * @param isRef whether the returned allele must be reference ({@code true}) or alternative {@code false}}
     * @return never {@code null}.
     * @throws AlleleEncodingException if {@code bases} is empty or contains invalid base codes.
     */
    static Allele inline(final byte[] bases, final boolean isRef) {
        if (bases.length == 0) {
            throw AlleleEncodingException.invalidBases(bases);
        } else if (bases.length == 1) {
            return AlleleUtils.decodeSingleBaseInline(bases[0], isRef);
        } else if (AlleleUtils.areValidBases(bases)) {
            return new MultiBaseInLineAllele(bases.clone(), isRef);
        } else {
            throw AlleleEncodingException.invalidBases(bases);
        }
    }

    /**
     * Returns a symbolic allele given its ID.
     * @param id the alleles symbolic ID.
     * @return never {@code null}.
     * @throws NullPointerException if {@code id} is {@code null} .
     * @throws AlleleEncodingException if {@code id} is not a valid symbolic allele ID.
     */
    static Allele symbolic(final String id) {
        final Allele cached = AlleleUtils.lookupSymbolic(id);
        if (cached != null) {
            return cached;
        } else if (!AlleleUtils.isValidSymbolicID(id)) {
            throw new AlleleEncodingException("bad symbolic id: '%s'", id);
        } else {
            return new PlainSymbolicAllele(id);
        }
    }

    /**
     * Returns an allele representing a breakend.
     * @param be the breakend information.
     * @return never {@code null}.
     * @throws NullPointerException if {@code be} is {@code nul}.
     */
    static Allele breakend(final Breakend be) {
        return be.asAllele();
    }

    /**
     * Composes an allele that represent a full assembly contig insertion when there is exactly one
     * base preeceding the insertion.
     * <p>
     *     The input {@code base} can be any of the 5 valid codes {@code A C G T N} (lower case are also allowed) and '.' that in this case
     *     indicates that the insertion is before the first base on another contig.
     * </p>
     * @param base preceeding the insertion.
     * @param contig the contig ID.
     * @return never {@code null}.
     * @throws NullPointerException if {@code contig} is {@code null}.
     * @throws AlleleEncodingException if {@code contig} is not a valid contig ID or {@code base}
     *    is not a valid base.
     */
    static Allele contigInsertion(final byte base, final String contig) {
        if (!AlleleUtils.isValidContigID(contig)) {
            throw AlleleEncodingException.invalidContigID(contig);
        } else if (AlleleUtils.isValidBase(base)) {
            return new ContigInsertAllele(new byte[]{base}, contig);
        } else if (base == '.') {
            return new ContigInsertAllele(ArrayUtils.EMPTY_BYTE_ARRAY, contig);
        } else {
            throw new AlleleEncodingException("not a valid base for a contig insertion allele: '%s'", (char) base);
        }
    }

    /**
     * Composes an allele that represent a full assembly contig insertion when there is an arbitrary
     * number of bases preceding the insertion.
     * <p>
     *     The input {@code base} can be any of the 5 valid codes {@code A C G T N} (lower case are also allowed).
     * </p>
     * <p>
     *     Alternatively {@code bases} may be an empty array or have exactly one entry equal to '.' indicating
     *     that this is in insertion before the first base of a reference contig. In either case the resulting allele would
     *     have zero bases.
     * </p>
     * <p>
     *     Notice that the special '.' base cannot be followed bay another sequence of bases as that is considered invalid.
     * </p>
     * @param bases preceding the insertion.
     * @param contig the contig ID.
     * @return never {@code null}.
     * @throws NullPointerException if {@code contig} is {@code null}.
     * @throws AlleleEncodingException if {@code contig} is not a valid contig ID or {@code bases}
     * contains invalid bases.
     */
    static Allele contigInsertion(final byte bases[], final String contig) {
        if (AlleleUtils.isValidContigID(contig)) {
            throw AlleleEncodingException.invalidContigID(contig);
        } else if (bases.length == 0) {
            return new ContigInsertAllele(bases, contig);
        } else if (bases.length == 1 && bases[0] == '.') {
            return new ContigInsertAllele(ArrayUtils.EMPTY_BYTE_ARRAY, contig);
        } else if (AlleleUtils.areValidBases(bases)) {
            return new ContigInsertAllele(bases.clone(), contig);
        } else {
            throw AlleleEncodingException.invalidBases(bases);
        }
    }
}
