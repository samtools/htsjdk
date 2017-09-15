/*
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
package htsjdk.variant.vcf;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;

/**
 * An iterator of `VariantContext`. This iterator can be used to
 * decode VCF data on the fly .
 *
 * Example:
 *
 * <pre>
 * VCFIterator r = new VCFIteratorBuilder().open(System.in);
 * while (r.hasNext()) {
 *     System.out.println(r.next());
 * }
 * r.close();
 * </pre>
 * 
 * @author Pierre Lindenbaum / @yokofakun
 * @see {@link VCFIteratorBuilder}
 *
 */
public interface VCFIterator extends CloseableIterator<VariantContext> {
    /** Returns the VCFHeader associated with this VCF/BCF file. */
    public VCFHeader getHeader();

    /**
     * Returns the next object but does not advance the iterator. Subsequent
     * calls to peek() and next() will return the same object.
     */
    public VariantContext peek();
}
