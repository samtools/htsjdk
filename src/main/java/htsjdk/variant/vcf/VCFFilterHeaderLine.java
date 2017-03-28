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

import htsjdk.tribble.TribbleException;

import java.util.*;

/**
 * @author ebanks
 * 
 * A class representing FILTER fields in the VCF header
 */
public class VCFFilterHeaderLine extends VCFStructuredHeaderLine {

    private static final long serialVersionUID = 1L;

    private static List<String> requiredTagOrder = Collections.unmodifiableList(
            new ArrayList<String>(2) {{
            add(ID_ATTRIBUTE);
            add(VCFStructuredHeaderLine.DESCRIPTION_ATTRIBUTE);
        }}
    );

    /**
     * create a VCF filter header line
     *
     * @param id         the headerLineID for this header line
     * @param description  the description for this header line
     */
    public VCFFilterHeaderLine(final String id, final String description) {
        super(VCFConstants.FILTER_HEADER_KEY,
            new LinkedHashMap<String, String>(2) {{
                put(ID_ATTRIBUTE, id);
                put(DESCRIPTION_ATTRIBUTE, description);
            }}
        );
        validate();
    }

    /**
     * Convenience constructor for FILTER whose description is the name
     * @param name
     */
    public VCFFilterHeaderLine(final String name) {
        this(name, name);
    }

    /**
     * create a VCF filter header line
     *
     * @param line      the header line
     * @param version   the vcf header version
     */
    public VCFFilterHeaderLine(final String line, final VCFHeaderVersion version) {
        super(VCFConstants.FILTER_HEADER_KEY, VCFHeaderLineTranslator.parseLine(version, line, requiredTagOrder));
        validate();
        validateForVersion(version);
    }

    private void validate() {
        if (getDescription() == null) {
            throw new TribbleException.InvalidHeader("Missing Description attribute in filter header line");
        }
    }

    @Override
    public boolean shouldBeAddedToDictionary() {
        return true;
    }

    /**
     * get the "Description" field
     * @return the "Description" field
     */
    public String getDescription() {
        return getGenericFieldValue(DESCRIPTION_ATTRIBUTE);
    }
}
