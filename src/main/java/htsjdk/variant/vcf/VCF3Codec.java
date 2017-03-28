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

package htsjdk.variant.vcf;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * A feature codec for the VCF3 specification, to read older VCF files.  VCF3 has been
 * depreciated in favor of VCF4 (See VCF codec for the latest information)
 *
 * <p>
 * Reads historical VCF3 encoded files (1000 Genomes Pilot results, for example)
 * </p>
 *
 * <p>
 * See also: @see <a href="http://vcftools.sourceforge.net/specs.html">VCF specification</a><br>
 * See also: @see <a href="http://www.ncbi.nlm.nih.gov/pubmed/21653522">VCF spec. publication</a>
 * </p>
 *
 * @author Mark DePristo
 * @since 2010
 */
public class VCF3Codec extends AbstractVCFCodec {
    public final static String VCF3_MAGIC_HEADER = "##fileformat=VCFv3";

    public VCF3Codec() {
        // TODO: This defaults to "Unknown" and winds up in every VariantContext. Setting it
        // here breaks some GATK4 tests. Should we put useful something here ?
        //setName(String.format("htsjdk:%s:%s",
        //        VCFHeaderVersion.VCF3_2.getVersionString(),
        //        VCFHeaderVersion.VCF3_3.getVersionString()));
    }

    /**
     * Return true if this codec can handle the target version
     * @param targetHeaderVersion
     * @return true if this codec can handle this version
     */
    @Override
    public boolean canDecodeVersion(final VCFHeaderVersion targetHeaderVersion) {
        return targetHeaderVersion == VCFHeaderVersion.VCF3_3 || targetHeaderVersion == VCFHeaderVersion.VCF3_2;
    }

    @Override
    public boolean canDecode(final String potentialInputFile) {
        return canDecodeFile(potentialInputFile, VCF3_MAGIC_HEADER);
    }

    /**
     * parse the filter string, first checking to see if we already have parsed it in a previous attempt
     * @param filterString the string to parse
     * @return a set of the filters applied
     */
    @Override
    protected Set<String> parseFilters(String filterString) {

        // null for unfiltered
        if ( filterString.equals(VCFConstants.UNFILTERED) )
            return null;

        // empty set for passes filters
        HashSet<String> fFields = new HashSet<>();

        if ( filterString.equals(VCFConstants.PASSES_FILTERS_v3) )
            return new HashSet<>(fFields);

        if (filterString.isEmpty())
            generateException("The VCF specification requires a valid filter status");

        // do we have the filter string cached?
        if ( filterHash.containsKey(filterString) )
            return new HashSet<>(filterHash.get(filterString));

        // otherwise we have to parse and cache the value
        if ( filterString.indexOf(VCFConstants.FILTER_CODE_SEPARATOR) == -1 )
            fFields.add(filterString);
        else
            fFields.addAll(Arrays.asList(filterString.split(VCFConstants.FILTER_CODE_SEPARATOR)));

        filterHash.put(filterString, fFields);

        return fFields;
    }

    /**
     * Handle reporting of duplicate filter IDs
     * @param duplicateFilterMessage
     */
    @Override
    protected void reportDuplicateFilterIDs(final String duplicateFilterMessage) {
        // no-op since this codec's parseFilters method doesn't check for them
    }

    /**
     * Handle report of duplicate info field values
     * @param key
     * @param infoLine
     */
    public void reportDuplicateInfoKeyValue(final String key, final String infoLine) {}

}
