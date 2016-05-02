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

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.variant.variantcontext.VariantContext;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A filtering iterator for VariantContexts that takes a base iterator and a VariantContextFilter.
 *
 * The iterator returns all the variantcontexts for which the filter's function "test" returns true (and only those)
 *
 * @author Yossi Farjoun
 */
public class FilteringVariantContextIterator implements CloseableIterator<VariantContext>, Iterable<VariantContext>{
    private final Iterator<VariantContext> iterator;
    private final VariantContextFilter filter;
    private VariantContext next = null;

    /**
     * Constructor of an iterator based on the provided iterator and predicate. The resulting
     * records will be all those VariantContexts from iterator for which filter.test( . ) is true
     *
     * @param iterator the backing iterator
     * @param filter   the filter
     */
    public FilteringVariantContextIterator(final Iterator<VariantContext> iterator, final VariantContextFilter filter) {
        this.iterator = iterator;
        this.filter = filter;
        next = getNextVC();
    }

    @Override
    public void close() {
        CloserUtil.close(iterator);
    }

    /**
     * Returns true if the iteration has more elements.
     *
     * @return true if the iteration has more elements.  Otherwise returns false.
     */
    @Override
    public boolean hasNext() {
        return next != null;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if there are no more elements to return
     *
     */
    @Override
    public VariantContext next() throws NoSuchElementException {
        if (next == null) {
            throw new NoSuchElementException("Iterator has no more elements.");
        }
        final VariantContext result = next;
        next = getNextVC();
        return result;
    }

    /**
     * Required method for Iterator API.
     *
     * @throws UnsupportedOperationException since it is unsupported here.
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove() not supported by FilteringVariantContextIterator");
    }

    /**
     * Gets the next record from the underlying iterator that passes the filter
     *
     * @return VariantContext the next filter-passing record
     */
    private VariantContext getNextVC() {

        while (iterator.hasNext()) {
            final VariantContext record = iterator.next();

            if (filter.test(record)) {
                return record;
            }
        }
        return null;
    }

    /**
     * function to satisfy the Iterable interface
     *
     * @return itself since the class inherits from Iterator
     */
    @Override
    public Iterator<VariantContext> iterator() {
        return this;
    }
}
