/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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

package htsjdk.variant.vcf;

import java.io.Closeable;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Locatable;
import htsjdk.variant.variantcontext.VariantContext;

/**
 * Interface for reading VCF/BCF files.
 */
public interface VCFReader extends Closeable, Iterable<VariantContext> {

    /**
     * Returns the VCFHeader associated with this VCFReader.
     */
    public VCFHeader getHeader();
    
    /**
     * Queries for records overlapping the region specified.
     * Note that this method requires VCF files with an associated index.  If no index exists a TribbleException will be thrown.
     *
     * @param chrom the chomosome to query
     * @param start query interval start
     * @param end   query interval end
     * @return non-null iterator over VariantContexts
     */
    public CloseableIterator<VariantContext> query(final String chrom, final int start, final int end);
    
    /**
     * Queries for records overlapping the {@link Locatable} specified.
     * Note that this method requires VCF files with an associated index.  If no index exists a TribbleException will be thrown.
     *
     * @return non-null iterator over VariantContexts
     */
    public default CloseableIterator<VariantContext> query(final Locatable locatable) {
        return query(locatable.getContig(), locatable.getStart(), locatable.getEnd());
    }

    /**
     * A method to check if the reader is query-able, i.e. if a call to {@link VCFFileReader#query(String, int, int)}
     * can be successful
     *
     * @return true if the reader can be queried, i.e. if the underlying Tribble reader is queryable.
     */
    public boolean isQueryable();

    @Override
    public CloseableIterator<VariantContext> iterator();

}
