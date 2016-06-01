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

import htsjdk.variant.vcf.VCFConstants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>Builder class for <code>VariantContext</code>.</p>
 *
 * <p>Some basic assumptions here:</p>
 * <ol>
 * <li> data isn't protectively copied.  If you provide an attribute map to
 * the build, and modify it later, the builder will see this and so will any
 * resulting variant contexts.  It's best not to modify collections provided
 * to a builder.</li>
 *
 * <li> the system uses the standard builder model, allowing the simple construction idiom:
 *<blockquote>
 *   <code>builder.source("a").genotypes(gc).id("x").make()</code> =&gt; <code>VariantContext</code>
 *</blockquote></li>
 *<li>The best way to copy a VariantContext is:
 *<blockquote>
 *   <code>new VariantContextBuilder(vc).make()</code> =&gt; a copy of VC
 *</blockquote>
 * <li> validation of arguments is done at the during the final <code>make()</code> call, so a
 * <code>VariantContextBuilder</code> can exist in an inconsistent state as long as those issues
 * are resolved before the call to <code>make()</code> is issued.
 *</ol>
 * @author depristo
 */
public class VariantContextBuilder {
    // required fields
    private boolean fullyDecoded = false;
    private String source = null;
    private String contig = null;
    private long start = -1;
    private long stop = -1;
    private Collection<Allele> alleles = null;

    // optional -> these are set to the appropriate default value
    private String ID = VCFConstants.EMPTY_ID_FIELD;
    private GenotypesContext genotypes = GenotypesContext.NO_GENOTYPES;
    private double log10PError = VariantContext.NO_LOG10_PERROR;
    private Set<String> filters = null;
    private Map<String, Object> attributes = null;
    private boolean attributesCanBeModified = false;

    /** enum of what must be validated */
    final private EnumSet<VariantContext.Validation> toValidate = EnumSet.noneOf(VariantContext.Validation.class);

    /**
     * Create an empty VariantContextBuilder where all values adopt their default values.  Note that
     * source, chr, start, stop, and alleles must eventually be filled in, or the resulting VariantContext
     * will throw an error.
     */
    public VariantContextBuilder() {}

    /**
     * Create an empty VariantContextBuilder where all values adopt their default values, but the bare min.
     * of info (source, chr, start, stop, and alleles) have been provided to start.
     */
    public VariantContextBuilder(final String source, final String contig, final long start, final long stop, final Collection<Allele> alleles) {
        this.source = source;
        this.contig = contig;
        this.start = start;
        this.stop = stop;
        this.alleles = alleles;
        this.attributes = Collections.emptyMap(); // immutable
        toValidate.add(VariantContext.Validation.ALLELES);
    }

    /**
     * Returns a new builder based on parent -- the new VC will have all fields initialized
     * to their corresponding values in parent.  This is the best way to create a derived VariantContext
     *
     * @param parent  Cannot be null
     */
    public VariantContextBuilder(final VariantContext parent) {
        if ( parent == null ) throw new IllegalArgumentException("BUG: VariantContextBuilder parent argument cannot be null in VariantContextBuilder");
        this.alleles = parent.getAlleles();
        this.attributes = parent.getAttributes();
        this.attributesCanBeModified = false;
        this.contig = parent.getContig();
        this.filters = parent.getFiltersMaybeNull();
        this.genotypes = parent.getGenotypes();
        this.ID = parent.getID();
        this.log10PError = parent.getLog10PError();
        this.source = parent.getSource();
        this.start = parent.getStart();
        this.stop = parent.getEnd();
        this.fullyDecoded = parent.isFullyDecoded();
    }

    public VariantContextBuilder(final VariantContextBuilder parent) {
        if ( parent == null ) throw new IllegalArgumentException("BUG: VariantContext parent argument cannot be null in VariantContextBuilder");
        this.alleles = parent.alleles;
        this.attributesCanBeModified = false;
        this.contig = parent.contig;
        this.genotypes = parent.genotypes;
        this.ID = parent.ID;
        this.log10PError = parent.log10PError;
        this.source = parent.source;
        this.start = parent.start;
        this.stop = parent.stop;
        this.fullyDecoded = parent.fullyDecoded;

        this.attributes(parent.attributes);
        this.filters(parent.filters);
    }

    public VariantContextBuilder copy() {
        return new VariantContextBuilder(this);
    }

    /**
     * Tells this builder to use this collection of alleles for the resulting VariantContext
     *
     * @param alleles
     * @return this builder
     */
    public VariantContextBuilder alleles(final Collection<Allele> alleles) {
        this.alleles = alleles;
        toValidate.add(VariantContext.Validation.ALLELES);
        return this;
    }

    public VariantContextBuilder alleles(final List<String> alleleStrings) {
        final List<Allele> alleles = new ArrayList<Allele>(alleleStrings.size());

        for ( int i = 0; i < alleleStrings.size(); i++ ) {
            alleles.add(Allele.create(alleleStrings.get(i), i == 0));
        }

        return alleles(alleles);
    }

    public VariantContextBuilder alleles(final String ... alleleStrings) {
        return alleles(Arrays.asList(alleleStrings));
    }

    public List<Allele> getAlleles() {
        return new ArrayList<Allele>(alleles);
    }

    /**
     * Tells this builder to use this map of attributes for the resulting <code>VariantContext</code>. The
     * contents of the Map are copied to a new Map to ensure that modifications to the provided Map post-invocation
     * don't affect the VariantContext and also to ensure additional attributes can be added in case the provided
     * map doesn't support changes (e.g. UnmodifiableMap).
     *
     * Attributes can be <code>null</code> -&gt; meaning there are no attributes.  After
     * calling this routine the builder assumes it can modify the attributes
     * object here, if subsequent calls are made to set attribute values
     *
     * Value for each attribute must be of a type that implements {@link Serializable} or else
     * serialization will fail.
     *
     * @param attributes a Map of attributes to replace any existing attributes with
     */
    public VariantContextBuilder attributes(final Map<String, ?> attributes) {
        this.attributes = new HashMap<>();
        if (attributes != null) this.attributes.putAll(attributes);
        this.attributesCanBeModified = true;
        return this;
    }

    /**
     * Puts the key -&gt; value mapping into this builder's attributes
     *
     * @param key key for the attribute
     * @param value value for the attribute (must be of a type that implements {@link Serializable} or else serialization will fail)
     */
    public VariantContextBuilder attribute(final String key, final Object value) {
        makeAttributesModifiable();
        attributes.put(key, value);
        return this;
    }

    /**
     * Removes key if present in the attributes
     *
     * @param key  key to remove
     * @return
     */
    public VariantContextBuilder rmAttribute(final String key) {
        makeAttributesModifiable();
        attributes.remove(key);
        return this;
    }

    /**
     * Removes list of keys if present in the attributes
     *
     * @param keys  list of keys to remove
     * @return
     */
    public VariantContextBuilder rmAttributes(final List<String> keys) {
        makeAttributesModifiable();
        for ( final String key : keys )
            attributes.remove(key);
        return this;
    }

    /**
     * Makes the attributes field modifiable.  In many cases attributes is just a pointer to an immutable
     * collection, so methods that want to add / remove records require the attributes to be copied to a
     */
    private void makeAttributesModifiable() {
        if ( ! attributesCanBeModified ) {
            this.attributesCanBeModified = true;
            if (attributes == null) {
            	this.attributes = new HashMap<String, Object>();
            } else {
            	this.attributes = new HashMap<String, Object>(attributes);
            }
        }
    }

    /**
     * This builder's filters are set to this value
     *
     * filters can be <code>null</code> -&gt; meaning there are no filters
     * @param filters
     */
    public VariantContextBuilder filters(final Set<String> filters) {
        this.filters = filters;
        return this;
    }

    /**
     * {@link #filters}
     *
     * @param filters
     * @return
     */
    public VariantContextBuilder filters(final String ... filters) {
        filters(new LinkedHashSet<String>(Arrays.asList(filters)));
        return this;
    }

    public VariantContextBuilder filter(final String filter) {
        if ( this.filters == null ) this.filters = new LinkedHashSet<String>(1);
        this.filters.add(filter);
        return this;
    }

    /**
     * Tells this builder that the resulting VariantContext should have PASS filters
     *
     * @return
     */
    public VariantContextBuilder passFilters() {
        return filters(VariantContext.PASSES_FILTERS);
    }

    /**
     * Tells this builder that the resulting VariantContext be unfiltered
     *
     * @return
     */
    public VariantContextBuilder unfiltered() {
        this.filters = null;
        return this;
    }

    /**
     * Tells this builder that the resulting <code>VariantContext</code> should use this genotype's <code>GenotypeContext</code>.
     *
     * Note that genotypes can be <code>null</code> -&gt; meaning there are no genotypes
     *
     * @param genotypes
     */
    public VariantContextBuilder genotypes(final GenotypesContext genotypes) {
        this.genotypes = genotypes;
        if ( genotypes != null )
            toValidate.add(VariantContext.Validation.GENOTYPES);
        return this;
    }

    public VariantContextBuilder genotypesNoValidation(final GenotypesContext genotypes) {
        this.genotypes = genotypes;
        return this;
    }

    /**
     * Tells this builder that the resulting <code>VariantContext</code> should use a <code>GenotypeContext</code> containing genotypes
     *
     * Note that genotypes can be <code>null</code>, meaning there are no genotypes
     *
     * @param genotypes
     */
    public VariantContextBuilder genotypes(final Collection<Genotype> genotypes) {
        return genotypes(GenotypesContext.copy(genotypes));
    }

    /**
     * Tells this builder that the resulting <code>VariantContext</code> should use a <code>GenotypeContext</code> containing genotypes
     * @param genotypes
     */
    public VariantContextBuilder genotypes(final Genotype ... genotypes) {
        return genotypes(GenotypesContext.copy(Arrays.asList(genotypes)));
    }

    /**
     * Tells this builder that the resulting VariantContext should not contain any GenotypeContext
     */
    public VariantContextBuilder noGenotypes() {
        this.genotypes = null;
        return this;
    }

    /**
     * Tells us that the resulting VariantContext should have ID
     * @param ID
     * @return
     */
    public VariantContextBuilder id(final String ID) {
        this.ID = ID;
        return this;
    }

    /**
     * Tells us that the resulting VariantContext should not have an ID
     * @return
     */
    public VariantContextBuilder noID() {
        return id(VCFConstants.EMPTY_ID_FIELD);
    }

    /**
     * Tells us that the resulting VariantContext should have log10PError
     * @param log10PError
     * @return
     */
    public VariantContextBuilder log10PError(final double log10PError) {
        this.log10PError = log10PError;
        return this;
    }

    /**
     * Tells us that the resulting VariantContext should have source field set to source
     * @param source
     * @return
     */
    public VariantContextBuilder source(final String source) {
        this.source = source;
        return this;
    }

    /**
     * Tells us that the resulting VariantContext should have the specified location
     * @param contig
     * @param start
     * @param stop
     * @return
     */
    public VariantContextBuilder loc(final String contig, final long start, final long stop) {
        this.contig = contig;
        this.start = start;
        this.stop = stop;
        toValidate.add(VariantContext.Validation.ALLELES);
        return this;
    }

    /**
     * Tells us that the resulting VariantContext should have the specified contig chr
     * @param contig
     * @return
     */
    public VariantContextBuilder chr(final String contig) {
        this.contig = contig;
        return this;
    }

    /**
     * Tells us that the resulting VariantContext should have the specified contig start
     * @param start
     * @return
     */
    public VariantContextBuilder start(final long start) {
        this.start = start;
        toValidate.add(VariantContext.Validation.ALLELES);
        return this;
    }

    /**
     * Tells us that the resulting VariantContext should have the specified contig stop
     * @param stop
     * @return
     */
    public VariantContextBuilder stop(final long stop) {
        this.stop = stop;
        return this;
    }

    /**
     * @see #computeEndFromAlleles(java.util.List, int, int) with endForSymbolicAlleles == -1
     */
    public VariantContextBuilder computeEndFromAlleles(final List<Allele> alleles, final int start) {
        return computeEndFromAlleles(alleles, start, -1);
    }

    /**
     * Compute the end position for this VariantContext from the alleles themselves
     *
     * assigns this builder the stop position computed.
     *
     * @param alleles the list of alleles to consider.  The reference allele must be the first one
     * @param start the known start position of this event
     * @param endForSymbolicAlleles the end position to use if any of the alleles is symbolic.  Can be -1
     *                              if no is expected but will throw an error if one is found
     * @return this builder
     */
    public VariantContextBuilder computeEndFromAlleles(final List<Allele> alleles, final int start, final int endForSymbolicAlleles) {
        stop(VariantContextUtils.computeEndFromAlleles(alleles, start, endForSymbolicAlleles));
        return this;
    }

    /**
     * @return true if this builder contains fully decoded data
     *
     * See VariantContext for more information
     */
    public boolean isFullyDecoded() {
        return fullyDecoded;
    }

    /**
     * Sets this builder's fully decoded state to true.
     *
     * A fully decoded builder indicates that all fields are represented by their
     * proper java objects (e.g., Integer(10) not "10").
     *
     * See VariantContext for more information
     *
     * @param isFullyDecoded
     */
    public VariantContextBuilder fullyDecoded(boolean isFullyDecoded) {
        this.fullyDecoded = isFullyDecoded;
        return this;
    }

    /**
     * Takes all of the builder data provided up to this point, and instantiates
     * a freshly allocated VariantContext with all of the builder data.  This
     * VariantContext is validated as appropriate and if not failing QC (and
     * throwing an exception) is returned.
     *
     * Note that this function can be called multiple times to create multiple
     * VariantContexts from the same builder.
     */
    public VariantContext make() {
        return make(false);
    }

    public VariantContext make(final boolean leaveModifyableAsIs) {
        if(!leaveModifyableAsIs) attributesCanBeModified = false;

        return new VariantContext(source, ID, contig, start, stop, alleles,
                genotypes, log10PError, filters, attributes,
                fullyDecoded, toValidate);
    }
}
