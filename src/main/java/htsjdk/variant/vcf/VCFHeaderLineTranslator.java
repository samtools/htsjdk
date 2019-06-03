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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A class for translating between vcf header versions
 */
public class VCFHeaderLineTranslator {
    private static final Map<VCFHeaderVersion,VCFLineParser> mapping;

    static {
        final Map<VCFHeaderVersion,VCFLineParser> map = new HashMap<>();
        map.put(VCFHeaderVersion.VCF4_0, new VCF4Parser());
        map.put(VCFHeaderVersion.VCF4_1, new VCF4Parser());
        map.put(VCFHeaderVersion.VCF4_2, new VCF4Parser());
        map.put(VCFHeaderVersion.VCF4_3, new VCF4Parser());
        map.put(VCFHeaderVersion.VCF3_3, new VCF3Parser());
        map.put(VCFHeaderVersion.VCF3_2, new VCF3Parser());
        mapping = Collections.unmodifiableMap(map);
    }

    public static Map<String,String> parseLine(VCFHeaderVersion version, String valueLine, List<String> expectedTagOrder) {
        return parseLine(version, valueLine, expectedTagOrder, Collections.emptyList());
    }
    
    public static Map<String,String> parseLine(VCFHeaderVersion version, String valueLine, List<String> expectedTagOrder, List<String> recommendedTags) {
        return mapping.get(version).parseLine(valueLine, expectedTagOrder, recommendedTags);
    }
}


interface VCFLineParser {
    /**
     * parse a VCF line
     * 
     * @see #parseLine(String, List, List) VCFv4.2+ recommended tags support
     * 
     * @param valueLine the line
     * @param expectedTagOrder List of expected tags
     * @return a mapping of the tags parsed out
     */
    default Map<String,String> parseLine(String valueLine, List<String> expectedTagOrder) {
        return parseLine(valueLine, expectedTagOrder, Collections.emptyList());
    }

    /**
     * parse a VCF line
     * 
     * The recommended tags were introduced in VCFv4.2. 
     * Older implementations may throw an exception when the recommendedTags field is not empty.
     * 
     * We use a list to represent tags as we assume there will be a very small amount of them,
     * so using a {@code Set} is overhead.
     * 
     * @param valueLine the line
     * @param expectedTagOrder List of expected tags
     * @param recommendedTags List of tags that may or may not be present. Use an empty list instead of NULL for none.
     * @return a mapping of the tags parsed out
     */
    Map<String,String> parseLine(String valueLine, List<String> expectedTagOrder, List<String> recommendedTags);
}


/**
 * a class that handles the to and from disk for VCF 4 lines
 */
class VCF4Parser implements VCFLineParser {
    
    @Override
    public Map<String, String> parseLine(String valueLine, List<String> expectedTagOrder, List<String> recommendedTags) {
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

        // validate the tags against the expected list
        index = 0;
        if ( expectedTagOrder != null ) {
            if (ret.keySet().isEmpty() && !expectedTagOrder.isEmpty()) {
                throw new TribbleException.InvalidHeader("Header with no tags is not supported when there are expected tags in line " + valueLine);
            }
            for ( String str : ret.keySet() ) {
                if (index < expectedTagOrder.size()) {
                    if (!expectedTagOrder.get(index).equals(str)) {
                        if (expectedTagOrder.contains(str)) {
                            throw new TribbleException.InvalidHeader("Tag " + str + " in wrong order (was #" + (index+1) + ", expected #" + (expectedTagOrder.indexOf(str)+1) + ") in line " + valueLine);
                        } else if (recommendedTags.contains(str)) {
                            throw new TribbleException.InvalidHeader("Recommended tag " + str + " must be listed after all expected tags in line " + valueLine);
                        }
                        else {
                            throw new TribbleException.InvalidHeader("Unexpected tag " + str + " in line " + valueLine);
                        }
                    }
                }
                index++;
            }
        }
        return ret;
    }
}

class VCF3Parser implements VCFLineParser {

    @Override
    public Map<String, String> parseLine(String valueLine, List<String> expectedTagOrder, List<String> recommendedTags) {
        if (!recommendedTags.isEmpty()) {
            throw new TribbleException.InternalCodecException("Recommended tags are not allowed in VCFv3.x");
        }
        
        // our return map
        Map<String, String> ret = new LinkedHashMap<String, String>();

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
                case (',') : if (!inQuote) { ret.put(expectedTagOrder.get(tagIndex++),builder.toString()); builder = new StringBuilder(); break; } // drop the current key value to the return map
                default: builder.append(c); // otherwise simply append to the current string
            }
            index++;
        }
        ret.put(expectedTagOrder.get(tagIndex++),builder.toString());
        
        // validate the tags against the expected list
        index = 0;
        if (tagIndex != expectedTagOrder.size()) throw new IllegalArgumentException("Unexpected tag count " + tagIndex + ", we expected " + expectedTagOrder.size());
        for (String str : ret.keySet()){
            if (!expectedTagOrder.get(index).equals(str)) throw new IllegalArgumentException("Unexpected tag " + str + " in string " + valueLine);
            index++;
        }
        return ret;
    }
}
