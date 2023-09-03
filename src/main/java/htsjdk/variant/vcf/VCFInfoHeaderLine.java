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
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 *         <p>
 *         Class VCFInfoHeaderLine
 *         </p>
 *         <p>
 *         A class representing an INFO field in the VCF header
 *         </p>
 */
public class VCFInfoHeaderLine extends VCFCompoundHeaderLine {
    private static final long serialVersionUID = 1L;

    protected final static Log logger = Log.getInstance(VCFFormatHeaderLine.class);

    public VCFInfoHeaderLine(String name, VCFHeaderLineCount count, VCFHeaderLineType type, String description) {
        super(VCFConstants.INFO_HEADER_KEY, name, count, type, description);
    }

    public VCFInfoHeaderLine(String name, int count, VCFHeaderLineType type, String description) {
        super(VCFConstants.INFO_HEADER_KEY, name, count, type, description);
    }

    public VCFInfoHeaderLine(String name, int count, VCFHeaderLineType type, String description, String source, String version) {
        super(VCFConstants.INFO_HEADER_KEY, name, count, type, description);
        this.updateGenericField(SOURCE_ATTRIBUTE, source);
        this.updateGenericField(VERSION_ATTRIBUTE, version);
    }

    public VCFInfoHeaderLine(String name, VCFHeaderLineCount count, VCFHeaderLineType type, String description, String source, String version) {
        super(VCFConstants.INFO_HEADER_KEY, name, count, type, description);
        this.updateGenericField(SOURCE_ATTRIBUTE, source);
        this.updateGenericField(VERSION_ATTRIBUTE, version);
    }

    public VCFInfoHeaderLine(String line, VCFHeaderVersion version) {
        super(VCFConstants.INFO_HEADER_KEY,
              VCFHeaderLineTranslator.parseLine(version, line, expectedTagOrder),
              version
        );
        validateForVersionOrThrow(version);
    }

    /**
     * Compare two VCFInfoHeaderLine objects to determine if they have compatible number types, and return a
     * VCFInfoHeaderLine that represents the result of merging these two lines.
     *
     * @param infoLine1 first info line to merge
     * @param infoLine2 second info line to merge
     * @param conflictWarner conflict warning emitter
     * @return a merged VCFInfoHeaderLine
     */
    public static VCFInfoHeaderLine getMergedInfoHeaderLine(
            final VCFInfoHeaderLine infoLine1,
            final VCFInfoHeaderLine infoLine2,
            final VCFHeaderMerger.HeaderMergeConflictWarnings conflictWarner)
    {
        ValidationUtils. nonNull(infoLine1);
        ValidationUtils. nonNull(infoLine2);
        ValidationUtils. nonNull(conflictWarner);

        // delegate to the generic VCFCompoundHeaderLine merger, passing a resolver lambda
        return VCFCompoundHeaderLine.getMergedCompoundHeaderLine(
                infoLine1,
                infoLine2,
                conflictWarner,
                (l1, l2) -> new VCFInfoHeaderLine(
                        l1.getID(),
                        VCFHeaderLineCount.UNBOUNDED,
                        l1.getType(),
                        l1.getDescription())
        );
    }

    @Override
    protected Optional<String> validateID(final String id) {
        return id.equals(VCFConstants.THOUSAND_GENOMES_KEY)
            ? Optional.empty()
            : super.validateID(id);
    }

    @Override
    public boolean shouldBeAddedToDictionary() {
        return true;
    }

}
