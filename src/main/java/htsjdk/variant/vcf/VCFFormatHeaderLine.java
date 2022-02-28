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


import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import htsjdk.utils.ValidationUtils;

/**
 * @author ebanks
 *         <p>
 *         Class VCFFormatHeaderLine
 *         </p>
 *         <p>
 *         A class representing genotype FORMAT fields in the VCF header</p>
 */
public class VCFFormatHeaderLine extends VCFCompoundHeaderLine {
    private static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFFormatHeaderLine.class);

    public VCFFormatHeaderLine(String name, int count, VCFHeaderLineType type, String description) {
        super(VCFConstants.FORMAT_HEADER_KEY, name, count, type, description);
        validate();
    }

    public VCFFormatHeaderLine(String name, VCFHeaderLineCount count, VCFHeaderLineType type, String description) {
        super(VCFConstants.FORMAT_HEADER_KEY, name, count, type, description);
        validate();
    }

    public VCFFormatHeaderLine(String line, VCFHeaderVersion version) {
        super(VCFConstants.FORMAT_HEADER_KEY,
              VCFHeaderLineTranslator.parseLine(version, line, expectedTagOrder),
              version);
        validate();
        validateForVersionOrThrow(version);
    }

    /**
     * Compare two VCFFormatHeaderLine objects to determine if they have compatible number types, and return a
     * VCFFormatHeaderLine that represents the result of merging these two lines.
     *
     * @param formatLine1 first format line to merge
     * @param formatLine2 second format line to merge
     * @param conflictWarner conflict warning emitter
     * @return a merged VCFFormatHeaderLine
     */
    public static VCFFormatHeaderLine getMergedFormatHeaderLine(
            final VCFFormatHeaderLine formatLine1,
            final VCFFormatHeaderLine formatLine2,
            final VCFHeaderMerger.HeaderMergeConflictWarnings conflictWarner)
    {
        ValidationUtils. nonNull(formatLine1);
        ValidationUtils. nonNull(formatLine2);
        ValidationUtils. nonNull(conflictWarner);

        // delegate to the generic VCFCompoundHeaderLine merger, passing a resolver lambda
        return VCFCompoundHeaderLine.getMergedCompoundHeaderLine(
                formatLine1,
                formatLine2,
                conflictWarner,
                (l1, l2) -> new VCFFormatHeaderLine(
                        l1.getID(),
                        VCFHeaderLineCount.UNBOUNDED,
                        l1.getType(),
                        l1.getDescription())
        );
    }

    private void validate() {
        if (this.getType() == VCFHeaderLineType.Flag) {
            throw new TribbleException(String.format("Flag is an unsupported type for format fields: ", this.toStringEncoding()));
        }
    }

    @Override
    public boolean shouldBeAddedToDictionary() {
        return true;
    }
}