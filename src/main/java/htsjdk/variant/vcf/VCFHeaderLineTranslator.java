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
import htsjdk.utils.Utils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A class for translating between vcf header versions and corresponding header line parsers.
 */
public class VCFHeaderLineTranslator {
    private static Map<VCFHeaderVersion,VCFLineParser> mapping;

    static {
        mapping = new HashMap<>();
        mapping.put(VCFHeaderVersion.VCF4_0,new VCF4Parser());
        mapping.put(VCFHeaderVersion.VCF4_1,new VCF4Parser());
        mapping.put(VCFHeaderVersion.VCF4_2,new VCF4Parser());
        mapping.put(VCFHeaderVersion.VCF4_3,new VCF4Parser());
        mapping.put(VCFHeaderVersion.VCF3_3,new VCF3Parser());
        mapping.put(VCFHeaderVersion.VCF3_2,new VCF3Parser());
    }

    /**
     * Parse a VCFHeaderLine for the given version.
     *
     * @param version VCFHeaderVersion of the header line
     * @param valueLine the header line string
     * @param expectedTagOrder List of expected tags (interpreted differently by the VCF3 and VCF4 parsers).
     * @return a mapping of the tags parsed out
     */
    public static Map<String,String> parseLine(VCFHeaderVersion version, String valueLine, List<String> expectedTagOrder) {
        return mapping.get(version).parseLine(valueLine, expectedTagOrder);
    }
}

/**
 * Parse a VCFHeaderLine.
 */
interface VCFLineParser {
    public Map<String,String> parseLine(String valueLine, List<String> expectedTagOrder);
}

/**
 * a class that handles the to and from disk for VCF 4 lines
 */
class VCF4Parser implements VCFLineParser {

    /**
     * Parse a VCFHeaderLine. The expectedTagOrder list prescribes the order in which tags should appear, but
     * all tags are treated as optional. Additional tags are allowed after the expected tags, and may appear in
     * any order. It is the caller's responsibility to validate that all required tags are present and that
     * any additional "optional" tags are valid.
     *
     * @param valueLine the header line string
     * @param expectedTagOrder List of tags that are required to appear in the order they're expected. Additional
     *                         "extra" tags are allowed after the tags in this list, and must be validated by
     *                         the caller.
     * @return a mapping of all tags parsed out
     */
    @Override
    public Map<String, String> parseLine(String valueLine, List<String> expectedTagOrder) {
        // our return map
        Map<String, String> ret = new LinkedHashMap<String, String>();

        // a builder to store up characters as we go
        StringBuilder builder = new StringBuilder();

        // store the key when we're parsing out the values
        String key = "";

        // where are we in the stream of characters?
        int index = 0;

        // are we inside a quotation? we don't special case ',' then
        boolean inQuote = false;

        // if we are in a quote and we see a backslash followed by quote, treat it as an escaped quote
        boolean escape = false;

        // a little switch machine to parse out the tags. Regex ended up being really complicated and ugly [yes, but this machine is getting ugly now... MAD]
        for (char c: valueLine.toCharArray()) {
            if ( c == '\"') {
                if (escape) {
                    builder.append(c);
                    escape = false;
                } else {
                    inQuote = !inQuote;
                }
            } else if ( inQuote ) {
                if (escape) {
                    // in VCF 4.2 spec the only valid characters to escape are double quote and backslash; otherwise copy the backslash through
                    if (c == '\\') {
                        builder.append(c);
                    } else {
                        builder.append('\\');
                        builder.append(c);
                    }
                    escape = false;
                } else if (c != '\\') {
                    builder.append(c);
                } else {
                    escape = true;
                }
            } else {
                escape = false;
                switch (c) {
                    case ('<') : if (index == 0) break; // if we see a open bracket at the beginning, ignore it
                    case ('>') : if (index == valueLine.length()-1) ret.put(key,builder.toString().trim()); break; // if we see a close bracket, and we're at the end, add an entry to our list
                    case ('=') : key = builder.toString().trim(); builder = new StringBuilder(); break; // at an equals, copy the key and reset the builder
                    case (',') : ret.put(key,builder.toString().trim()); builder = new StringBuilder(); break; // drop the current key value to the return map
                    default: builder.append(c); // otherwise simply append to the current string
                }
            }
            
            index++;
        }

        if (inQuote) {
            throw new TribbleException.InvalidHeader("Unclosed quote in header line value " + valueLine);
        }

        // Validate the order of all discovered tags against requiredTagOrder. All tags are treated as
        // "optional". Succeeding does not mean that all expected tags in the list were seen. Also, all
        // structured header lines can have "extra" tags, with no order specified, so additional tags
        // are tolerated.
        if ( expectedTagOrder != null ) {
            index = 0;
            for (String str : ret.keySet()) {
                if (index >= expectedTagOrder.size()) {
                    break; // done - end of requiredTagOrder list
                } else if (!expectedTagOrder.get(index).equals(str)) {
                    throw new TribbleException.InvalidHeader("Unexpected tag or tag order" + str + " in line " + valueLine);
                }
                index++;
            }
        }

        return ret;
    }
}

class VCF3Parser implements VCFLineParser {

    @Override
    public Map<String, String> parseLine(String valueLine, List<String> expectedTagOrder) {
        // our return map
        Map<String, String> ret = new LinkedHashMap<>();

        // a builder to store up characters as we go
        StringBuilder builder = new StringBuilder();

        // where are we in the stream of characters?
        int index = 0;
        // where in the expected tag order are we?
        int tagIndex = 0;

        // are we inside a quotation? we don't special case ',' then
        boolean inQuote = false;

        // a little switch machine to parse out the tags. Regex ended up being really complicated and ugly
        for (char c: valueLine.toCharArray()) {
            switch (c) {
                case ('\"') : inQuote = !inQuote; break; // a quote means we ignore ',' in our strings, keep track of it
                case (',') :
                    if (!inQuote) {
                        ret.put(expectedTagOrder.get(tagIndex++),builder.toString());
                        builder = new StringBuilder();
                        break;
                    } // drop the current key value to the return map
                default: builder.append(c); // otherwise simply append to the current string
            }
            index++;
        }
        ret.put(expectedTagOrder.get(tagIndex++),builder.toString());
        
        // Validate that:
        //      we have no more tags than are expected
        //      the ones we have are in the expected list
        //      they appear in the same order as in the expected list
        // This does no checking for missing tags; all tags are treated as optional
        //
        index = 0;
        if (tagIndex != expectedTagOrder.size()) {
            throw new IllegalArgumentException("Unexpected tag count " + tagIndex + ", we expected " + expectedTagOrder.size());
        }
        for (String str : ret.keySet()){
            if (!expectedTagOrder.get(index).equals(str)) {
                throw new IllegalArgumentException("Unexpected tag " + str + " in string " + valueLine);
            }
            index++;
        }
        return ret;
    }
}