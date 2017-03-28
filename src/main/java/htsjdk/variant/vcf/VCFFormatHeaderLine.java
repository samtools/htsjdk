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
        validateForVersion(version);
    }

    private void validate() {
        if (this.getType() == VCFHeaderLineType.Flag) {
            throw new TribbleException("Flag is an unsupported type for format fields");
        }
    }

    @Override
    public boolean shouldBeAddedToDictionary() {
        return true;
    }
}