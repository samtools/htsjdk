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

import java.util.*;

/**
 * A feature codec for the VCF 4.0, 4.1 and 4.2 specification versions
 *
 * <p>
 * VCF is a text file format (most likely stored in a compressed manner). It contains meta-information lines, a
 * header line, and then data lines each containing information about a position in the genome.
 * </p>
 * <p>One of the main uses of next-generation sequencing is to discover variation amongst large populations
 * of related samples. Recently the format for storing next-generation read alignments has been
 * standardised by the SAM/BAM file format specification. This has significantly improved the
 * interoperability of next-generation tools for alignment, visualisation, and variant calling.
 * We propose the Variant Call Format (VCF) as a standardised format for storing the most prevalent
 * types of sequence variation, including SNPs, indels and larger structural variants, together
 * with rich annotations. VCF is usually stored in a compressed manner and can be indexed for
 * fast data retrieval of variants from a range of positions on the reference genome.
 * The format was developed for the 1000 Genomes Project, and has also been adopted by other projects
 * such as UK10K, dbSNP, or the NHLBI Exome Project. VCFtools is a software suite that implements
 * various utilities for processing VCF files, including validation, merging and comparing,
 * and also provides a general Perl and Python API.
 * The VCF specification and VCFtools are available from http://vcftools.sourceforge.net.</p>
 *
 * <p>
 * See also: @see <a href="http://vcftools.sourceforge.net/specs.html">VCF specification</a><br>
 * See also: @see <a href="http://www.ncbi.nlm.nih.gov/pubmed/21653522">VCF spec. publication</a>
 * </p>
 *
 * <h2>File format example</h2>
 * <pre>
 *     ##fileformat=VCFv4.0
 *     #CHROM  POS     ID      REF     ALT     QUAL    FILTER  INFO    FORMAT  NA12878
 *     chr1    109     .       A       T       0       PASS  AC=1    GT:AD:DP:GL:GQ  0/1:610,327:308:-316.30,-95.47,-803.03:99
 *     chr1    147     .       C       A       0       PASS  AC=1    GT:AD:DP:GL:GQ  0/1:294,49:118:-57.87,-34.96,-338.46:99
 * </pre>
 *
 * @author Mark DePristo
 * @since 2010
 */
public class VCFCodec extends AbstractVCFCodec {
    // Our aim is to read in the records and convert to VariantContext as quickly as possible, relying
    // on VariantContext to do the validation of any contradictory (or malformed) record parameters.
    public final static String VCF4_MAGIC_HEADER = "##fileformat=VCFv4";

    public VCFCodec() {
        // TODO: This defaults to "Unknown" and winds up in every VariantContext. Setting it
        // here breaks some GATK4 tests. Should we put useful something here ?
        //setName(String.format("%s:%s:%s",
        //            VCFHeaderVersion.VCF4_0.getVersionString(),
        //            VCFHeaderVersion.VCF4_1.getVersionString(),
        //            VCFHeaderVersion.VCF4_2.getVersionString())
        //);
    }

    /**
     * Return true if this codec can handle the target version
     * @param targetHeaderVersion
     * @return true if this codec can handle this version
     */
    @Override
    public boolean canDecodeVersion(final VCFHeaderVersion targetHeaderVersion) {
        return targetHeaderVersion == VCFHeaderVersion.VCF4_0 ||
                targetHeaderVersion == VCFHeaderVersion.VCF4_1 ||
                targetHeaderVersion == VCFHeaderVersion.VCF4_2;
    }

    @Override
    public boolean canDecode(final String potentialInput) {
        // TODO: this will succeed on 4.3 files since it only looks as far as ..."##fileformat=VCFv4"
        return canDecodeFile(potentialInput, VCF4_MAGIC_HEADER);
    }

    /**
     * Handle reporting of duplicate filter IDs
     * @param duplicateFilterMessage
     */
    @Override
    protected void reportDuplicateFilterIDs(final String duplicateFilterMessage) {
        // older versions of htsjdk have been silently dropping these for a while, but we can at least warn
        if (VCFUtils.getVerboseVCFLogging()) {
            logger.warn(duplicateFilterMessage);
        }
    }

    public void reportDuplicateInfoKeyValue(final String key, final String infoLine) {}

    /**
     * parse out the info fields
     * @param infoField the fields
     * @return a mapping of keys to objects
     */
    protected Map<String, Object> parseInfo(String infoField) {
        if (infoField.indexOf(' ') != -1) {
            generateException(
                    String.format("Whitespace is not allowed in the INFO field in VCF version %s: %s",
                            version == null ?
                                    "unknown" :
                                    version.getVersionString(),
                            infoField)
            );
        }
        return super.parseInfo(infoField);
    }

}
