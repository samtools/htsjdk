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

import htsjdk.variant.variantcontext.VariantContext;

import java.util.ArrayList;

/**
 * A Predicate on VariantContexts that returns true when either all its sub-predicates are true, or none are false.
 *
 * @author Yossi Farjoun
 */
public class CompoundFilter extends ArrayList<VariantContextFilter> implements VariantContextFilter {

    final boolean requireAll;

    /**
     * A constructor that will determine if this compound filter will require that *all* the included filters pass
     * or *some* of them pass (depending on the requireAll parameter in the constructor).
     *
     * @param requireAll a boolean parameter determining whether this filter requires all its elements to pass (true) for
     * it to pass, or only one (false). If there are no variantfilters it will return true.
     */
    public CompoundFilter(final boolean requireAll) {
        super();
        this.requireAll = requireAll;
    }

    /**
     * @param variantContext the record to examine against the sub-filters
     * @return true if variantContext either passes all the filters (when requireAll==true)
     * or doesn't fail any of the filters (when requireAll==false)
     */
    @Override
    public boolean test(final VariantContext variantContext) {

        if (requireAll) {
            for (final VariantContextFilter filter : this) {
                if (!filter.test(variantContext)) return false;
            }

            return true;
        } else {
            for (final VariantContextFilter filter : this) {
                if (filter.test(variantContext)) return true;
            }

            return isEmpty();
        }
    }
}
