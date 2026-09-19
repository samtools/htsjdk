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

import htsjdk.tribble.util.ParsingUtils;
import htsjdk.variant.vcf.VCFConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A builder class for genotypes
 *
 * Provides convenience setter methods for all of the Genotype field
 * values.  Setter methods can be used in any order, allowing you to
 * pass through states that wouldn't be allowed in the highly regulated
 * immutable Genotype class.
 *
 * All fields default to meaningful MISSING values.
 *
 * Call make() to actually create the corresponding Genotype object from
 * this builder.  Can be called multiple times to create independent copies,
 * or with intervening sets to conveniently make similar Genotypes with
 * slight modifications.
 *
 * Re-using the same GenotypeBuilder to build multiple Genotype objects via calls
 * to make() is dangerous, since reference types in the builder (eg., Collections/arrays)
 * don't get copied when making each Genotype. To safely re-use the same builder object
 * multiple times, use makeWithShallowCopy() instead of make().
 *
 * @author Mark DePristo
 * @since 06/12
 */
public final class GenotypeBuilder {
    private static final List<Allele> HAPLOID_NO_CALL = Arrays.asList(Allele.NO_CALL);
    private static final List<Allele> DIPLOID_NO_CALL = Arrays.asList(Allele.NO_CALL, Allele.NO_CALL);

    private String sampleName = null;
    private List<Allele> alleles = Collections.emptyList();

    private boolean isPhased = false;
    private boolean[] allelePhasing;
    // Whether allelePhasing came with a copied genotype rather than from the caller. Set wherever allelePhasing is
    // set to an array and read only while it is one, so reset() and phased() need not touch it.
    private boolean allelePhasingIsInherited;
    private int GQ = -1;
    private int DP = -1;
    private int[] AD = null;
    private int[] PL = null;
    private Map<String, Object> extendedAttributes = null;
    private String filters = null;
    private int initialAttributeMapSize = 5;

    private static final Map<String, Object> NO_ATTRIBUTES =
            Collections.unmodifiableMap(new HashMap<String, Object>(0));

    // -----------------------------------------------------------------
    //
    // Factory methods
    //
    // -----------------------------------------------------------------

    public static Genotype create(final String sampleName, final List<Allele> alleles) {
        return new GenotypeBuilder(sampleName, alleles).make();
    }

    public static Genotype create(
            final String sampleName, final List<Allele> alleles, final Map<String, Object> attributes) {
        return new GenotypeBuilder(sampleName, alleles).attributes(attributes).make();
    }

    protected static Genotype create(final String sampleName, final List<Allele> alleles, final double[] gls) {
        return new GenotypeBuilder(sampleName, alleles).PL(gls).make();
    }

    /**
     * Create a new Genotype object for a sample that's missing from the VC (i.e., in
     * the output header).  Defaults to a diploid no call genotype ./.
     *
     * @param sampleName the name of this sample
     * @return an initialized Genotype with sampleName that's a diploid ./. no call genotype
     */
    public static Genotype createMissing(final String sampleName, final int ploidy) {
        final GenotypeBuilder builder = new GenotypeBuilder(sampleName);
        switch (ploidy) {
            case 1:
                builder.alleles(HAPLOID_NO_CALL);
                break;
            case 2:
                builder.alleles(DIPLOID_NO_CALL);
                break;
            default:
                builder.alleles(Collections.nCopies(ploidy, Allele.NO_CALL));
                break;
        }
        return builder.make();
    }

    /**
     * Create a empty builder.  Both a sampleName and alleles must be provided
     * before trying to make a Genotype from this builder.
     */
    public GenotypeBuilder() {}

    /**
     * Create a builder using sampleName.  Alleles must be provided
     * before trying to make a Genotype from this builder.
     * @param sampleName
     */
    public GenotypeBuilder(final String sampleName) {
        name(sampleName);
    }

    /**
     * Make a builder using sampleName and alleles for starting values
     * @param sampleName
     * @param alleles
     */
    public GenotypeBuilder(final String sampleName, final List<Allele> alleles) {
        name(sampleName);
        alleles(alleles);
    }

    /**
     * Create a new builder starting with the values in Genotype g
     * @param g
     */
    public GenotypeBuilder(final Genotype g) {
        copy(g);
    }

    /**
     * Copy all of the values for this builder from Genotype g
     * @param g
     * @return
     */
    public GenotypeBuilder copy(final Genotype g) {
        name(g.getSampleName());
        alleles(g.getAlleles());
        phased(g.isPhased());
        if (g.hasPerAllelePhasing()) {
            final boolean[] phasing = new boolean[g.getPloidy()];
            for (int i = 0; i < phasing.length; i++) {
                phasing[i] = g.isAllelePhased(i);
            }
            allelePhasing(phasing);
            allelePhasingIsInherited = true;
        }
        GQ(g.getGQ());
        DP(g.getDP());
        AD(g.getAD());
        PL(g.getPL());
        filter(g.getFilters());
        attributes(g.getExtendedAttributes());
        return this;
    }

    /**
     * Reset all of the builder attributes to their defaults.  After this
     * function you must provide sampleName and alleles before trying to
     * make more Genotypes.
     */
    public final void reset(final boolean keepSampleName) {
        if (!keepSampleName) sampleName = null;
        alleles = Collections.emptyList();
        isPhased = false;
        allelePhasing = null;
        GQ = -1;
        DP = -1;
        AD = null;
        PL = null;
        filters = null;
        extendedAttributes = null;
    }

    /**
     * Create a new Genotype object using the values set in this builder.
     *
     * After creation the values in this builder can be modified and more Genotypes
     * created, althrough the contents of array values like PL should never be modified
     * inline as they are not copied for efficiency reasons.
     *
     * Note: if attributes are added via this builder after a call to make(), the new Genotype will
     * be modified. Use {@link #makeWithShallowCopy} to safely re-use the same builder object
     * multiple times.
     *
     * @return a newly minted Genotype object with values provided from this builder
     */
    public Genotype make() {
        final Map<String, Object> ea = (extendedAttributes == null) ? NO_ATTRIBUTES : extendedAttributes;
        if (allelePhasing == null) {
            // nearly every genotype, and made by the million: nothing here but the constructor call
            return new FastGenotype(sampleName, alleles, isPhased, GQ, DP, AD, PL, filters, ea);
        }
        return makeWithAllelePhasing(alleles, AD, PL, ea, false);
    }

    /**
     * Create a new Genotype object using the values set in this builder, and perform a
     * shallow copy of reference types to allow safer re-use of this builder
     *
     * After creation the values in this builder can be modified and more Genotypes
     * created.
     *
     * @return a newly minted Genotype object with values provided from this builder
     */
    public Genotype makeWithShallowCopy() {
        final Map<String, Object> ea = (extendedAttributes == null) ? NO_ATTRIBUTES : new HashMap<>(extendedAttributes);
        final List<Allele> al = new ArrayList<>(alleles);
        final int[] copyAD = (AD == null) ? null : Arrays.copyOf(AD, AD.length);
        final int[] copyPL = (PL == null) ? null : Arrays.copyOf(PL, PL.length);
        if (allelePhasing == null) {
            return new FastGenotype(sampleName, al, isPhased, GQ, DP, copyAD, copyPL, filters, ea);
        }
        return makeWithAllelePhasing(al, copyAD, copyPL, ea, true);
    }

    /**
     * Makes the genotype of a builder that holds per-allele phases, which few do.
     *
     * @param copyPhases whether the genotype gets its own copy of the phases, as {@link #makeWithShallowCopy()} promises
     */
    private Genotype makeWithAllelePhasing(
            final List<Allele> alleles,
            final int[] AD,
            final int[] PL,
            final Map<String, Object> ea,
            final boolean copyPhases) {
        if (allelePhasingIsInherited && allelePhasing.length != alleles.size()) {
            // The phases came with a copied genotype and the caller then gave it another ploidy, so they no longer
            // say anything about these alleles; what copy() took from isPhased() still does.
            return new FastGenotype(sampleName, alleles, isPhased, GQ, DP, AD, PL, filters, ea);
        }
        final boolean[] needed = perAllelePhasingIfNeeded();
        final boolean[] phases = copyPhases && needed != null ? needed.clone() : needed;
        return new FastGenotype(sampleName, alleles, isAnyAllelePhased(), phases, GQ, DP, AD, PL, filters, ea);
    }

    /**
     * Set this genotype's name
     * @param sampleName
     * @return
     */
    public GenotypeBuilder name(final String sampleName) {
        this.sampleName = sampleName;
        return this;
    }

    /**
     * Set this genotype's alleles
     * @param alleles
     * @return
     */
    public GenotypeBuilder alleles(final List<Allele> alleles) {
        if (alleles == null) this.alleles = Collections.emptyList();
        else this.alleles = alleles;
        return this;
    }

    /**
     * Is this genotype phased?
     * @param phased
     * @return
     */
    public GenotypeBuilder phased(final boolean phased) {
        isPhased = phased;
        // Tested rather than just cleared: this runs once per genotype, and the unconditional reference store showed
        // up in BCF decoding, where a genotype costs only some 45 ns. make() keeps its common path bare for the same
        // reason.
        if (allelePhasing != null) {
            allelePhasing = null;
        }
        return this;
    }

    /**
     * Gives each allele its own phase, as VCF 4.4 does: element {@code i} says whether allele {@code i} is phased,
     * that is, whether the separator before it is {@code |}, the first element standing for a leading indicator.
     * Needed only for what {@link #phased(boolean)} cannot say: mixed separators ({@code 0/1|2}) or a first allele
     * whose phase is not the one the others imply ({@code |0/1}). Replaces any earlier call to either method; the
     * genotype then reports {@link Genotype#isPhased()} if any allele is phased.
     *
     * <p>A lone {@code false} for a haploid genotype is an explicitly unphased allele ({@code /1}), which only VCF 4.4
     * and later can write; a haploid genotype left to {@link #phased(boolean)} can always be written.
     *
     * @param allelePhasing one element per allele; not copied by {@link #make()}, like the other arrays
     * @throws IllegalStateException from {@link #make()} if its length is not the number of alleles
     */
    public GenotypeBuilder allelePhasing(final boolean[] allelePhasing) {
        this.allelePhasing = allelePhasing;
        this.allelePhasingIsInherited = false;
        return this;
    }

    /**
     * The phases to store in the genotype being made, or null when one flag says as much: every separator is the
     * same and the first allele's phase is the one they imply, which is {@code |} unless one of them is {@code /}.
     */
    private boolean[] perAllelePhasingIfNeeded() {
        if (allelePhasing.length != alleles.size()) {
            throw new IllegalStateException("Sample " + sampleName + " was given " + allelePhasing.length
                    + " allele phases for " + alleles.size() + " alleles");
        }
        if (allelePhasing.length == 0) {
            return null;
        }
        boolean allTheOthersPhased = true;
        boolean anyOtherPhased = false;
        for (int i = 1; i < allelePhasing.length; i++) {
            allTheOthersPhased &= allelePhasing[i];
            anyOtherPhased |= allelePhasing[i];
        }
        final boolean mixed = anyOtherPhased && !allTheOthersPhased;
        final boolean firstIsImplied = allelePhasing[0] == allTheOthersPhased;
        return mixed || !firstIsImplied ? allelePhasing : null;
    }

    /** What {@link Genotype#isPhased()} reports for a genotype given per-allele phases: whether any is set. */
    private boolean isAnyAllelePhased() {
        for (final boolean phased : allelePhasing) {
            if (phased) {
                return true;
            }
        }
        return false;
    }

    public GenotypeBuilder GQ(final int GQ) {
        this.GQ = GQ;
        return this;
    }

    /**  Set the GQ with a log10PError value
     *
     * @param pLog10Error
     * @return
     */
    public GenotypeBuilder log10PError(final double pLog10Error) {
        if (pLog10Error == CommonInfo.NO_LOG10_PERROR) return noGQ();
        else return GQ((int) Math.round(pLog10Error * -10));
    }

    /**
     * This genotype has no GQ value
     * @return
     */
    public GenotypeBuilder noGQ() {
        GQ = -1;
        return this;
    }

    /**
     * This genotype has no AD value
     * @return
     */
    public GenotypeBuilder noAD() {
        AD = null;
        return this;
    }

    /**
     * This genotype has no DP value
     * @return
     */
    public GenotypeBuilder noDP() {
        DP = -1;
        return this;
    }

    /**
     * This genotype has no PL value
     * @return
     */
    public GenotypeBuilder noPL() {
        PL = null;
        return this;
    }

    /**
     * This genotype has this DP value
     * @return
     */
    public GenotypeBuilder DP(final int DP) {
        this.DP = DP;
        return this;
    }

    /**
     * This genotype has this AD value
     * @return
     */
    public GenotypeBuilder AD(final int[] AD) {
        this.AD = AD;
        return this;
    }

    /**
     * This genotype has this PL value, as int[].  FAST
     * @return
     */
    public GenotypeBuilder PL(final int[] PL) {
        this.PL = PL;
        return this;
    }

    /**
     * This genotype has this PL value, converted from double[]. SLOW
     * @return
     */
    public GenotypeBuilder PL(final double[] GLs) {
        this.PL = GenotypeLikelihoods.fromLog10Likelihoods(GLs).getAsPLs();
        return this;
    }

    /**
     * This genotype has these attributes. Attributes are added to previous ones.
     *
     * Cannot contain inline attributes (DP, AD, GQ, PL). Note: this is not checked
     * @return
     */
    public GenotypeBuilder attributes(final Map<String, Object> attributes) {
        for (Map.Entry<String, Object> pair : attributes.entrySet()) attribute(pair.getKey(), pair.getValue());
        return this;
    }

    /**
     * Tells this builder to remove all extended attributes
     *
     * @return
     */
    public GenotypeBuilder noAttributes() {
        this.extendedAttributes = null;
        return this;
    }

    /**
     * This genotype has this attribute key / value pair.
     *
     * Cannot contain inline attributes (DP, AD, GQ, PL). Note: this is not checked
     * @return
     */
    public GenotypeBuilder attribute(final String key, final Object value) {
        if (extendedAttributes == null) extendedAttributes = new HashMap<String, Object>(initialAttributeMapSize);
        extendedAttributes.put(key, value);
        return this;
    }

    /**
     * Tells this builder to make a Genotype object that has had filters applied,
     * which may be empty (passes) or have some value indicating the reasons
     * why it's been filtered.
     *
     * @param filters non-null list of filters.  empty list =&gt; PASS
     * @return this builder
     */
    public GenotypeBuilder filters(final List<String> filters) {
        if (filters.isEmpty()) return filter(null);
        else if (filters.size() == 1) return filter(filters.get(0));
        else return filter(ParsingUtils.join(";", ParsingUtils.sortList(filters)));
    }

    /**
     * varargs version of #filters
     * @param filters
     * @return
     */
    public GenotypeBuilder filters(final String... filters) {
        return filters(Arrays.asList(filters));
    }

    /**
     * Most efficient version of setting filters -- just set the filters string to filters
     *
     * @param filter if filters == null or filters.equals("PASS") =&gt; genotype is PASS
     * @return
     */
    public GenotypeBuilder filter(final String filter) {
        this.filters = VCFConstants.PASSES_FILTERS_v4.equals(filter) ? null : filter;
        return this;
    }

    /**
     * This genotype is unfiltered
     *
     * @return
     */
    public GenotypeBuilder unfiltered() {
        return filter(null);
    }

    /**
     * Tell's this builder that we have at most these number of attributes
     * @return
     */
    public GenotypeBuilder maxAttributes(final int i) {
        initialAttributeMapSize = i;
        return this;
    }
}
