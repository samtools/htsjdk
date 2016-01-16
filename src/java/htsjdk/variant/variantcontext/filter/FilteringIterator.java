package htsjdk.variant.variantcontext.filter;

import htsjdk.variant.variantcontext.VariantContext;

import java.util.Iterator;

/**
 * A filtering iterator for VariantContexts that takes a base iterator and a VariantContextFilter.
 *
 * The iterator returns all the variantcontexts for which the filter's function "test" returns true (and only those)
 *
 * @author Yossi Farjoun
 *
 * use {@link FilteringVariantContextIterator} instead
 */

@Deprecated
public class FilteringIterator extends FilteringVariantContextIterator{
    public FilteringIterator(final Iterator<VariantContext> iterator, final VariantContextFilter filter) {
        super(iterator, filter);
    }
}
