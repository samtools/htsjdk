/*
 * The MIT License
 *
 * Copyright (c) 2015 The Broad Institute
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

package htsjdk.variant.variantcontext.filter;

import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;

/**
 * A Predicate on VariantContexts that either returns true at heterozygous sites (invertible to false).
 * if optional "sample" argument to constructor is given, the genotype of that sample will be examined,
 * otherwise first genotype will be used.
 *
 * Missing sample, or no genotype will result in an exception being thrown.
 *
 * @author Yossi Farjoun
 */
public class HeterozygosityFilter implements VariantContextFilter {

    final private String sample;
    final private boolean keepHets;

    /**
     * Constructor for a filter that will keep (or remove, if keepHets is false) VC for which the
     * genotype of sample is heterozygous. If sample is null, the first genotype in the
     * variant context will be used.
     *
     * @param keepHets determine whether to keep the het sites (true) or filter them out (false)
     * @param sample the name of the sample in the variant context whose genotype should be examined.
     */
    public HeterozygosityFilter(final boolean keepHets, final String sample) {
        this.keepHets = keepHets;
        this.sample = sample;
    }

    /**
     * Constructor as above that doesn't take a sample, instead it will look at the first genotype of the variant context.
     * @param keepHets if true, the heterozygous variant contexts will pass the filter, otherwise they will fail.
     */
    public HeterozygosityFilter(final boolean keepHets) {
        this(keepHets, null);
    }

    /**
     * @return true if variantContext is to be kept, otherwise false
     * Assumes that this.sample is a sample in the variantContext, if not null,
     * otherwise looks for the first genotype (and assumes it exists).
     * @param variantContext the record to examine for heterozygosity
     */
    @Override
    public boolean test(final VariantContext variantContext) {
        final Genotype gt = (sample == null) ? variantContext.getGenotype(0) : variantContext.getGenotype(sample);

        if (gt == null) {
            throw new IllegalArgumentException((sample == null) ?
                    "Cannot find any genotypes in VariantContext: " + variantContext :
                    "Cannot find sample requested: " + sample);
        }

        //XOR operator to reverse behaviour if keepHets is true.
        return gt.isHet() ^ !keepHets;
    }
}
