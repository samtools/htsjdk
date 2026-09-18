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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A class for translating between vcf header versions
 */
public class VCFHeaderLineTranslator {
    private static final VCFLineParser VCF3_PARSER = new VCF3Parser();
    private static final VCFLineParser VCF4_PARSER = new VCF4Parser();

    public static Map<String, String> parseLine(
            VCFHeaderVersion version, String valueLine, List<String> expectedTagOrder) {
        return parseLine(version, valueLine, expectedTagOrder, Collections.emptyList());
    }

    public static Map<String, String> parseLine(
            VCFHeaderVersion version, String valueLine, List<String> expectedTagOrder, List<String> recommendedTags) {
        // the header-line syntax changed between VCF 3 and VCF 4 and has been stable since, so the parser is chosen
        // by major version rather than looked up per version
        final VCFLineParser parser =
                version != null && !version.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0) ? VCF3_PARSER : VCF4_PARSER;
        return parser.parseLine(valueLine, expectedTagOrder, recommendedTags);
    }
}

interface VCFLineParser {
    /**
     * parse a VCF line
     *
     * @see #parseLine(String, List, List) VCFv4.2+ recommended tags support
     *
     * @param valueLine the line
     * @param expectedTagOrder the tags in their order, which VCF 3 lines need since they carry values only
     * @return a mapping of the tags parsed out
     */
    default Map<String, String> parseLine(String valueLine, List<String> expectedTagOrder) {
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
    Map<String, String> parseLine(String valueLine, List<String> expectedTagOrder, List<String> recommendedTags);
}

/**
 * Parses the {@code <key=value,...>} body of a VCF 4 structured header line.
 *
 * <p>Attributes may come in any order and any attribute may be present, since a parser must not rely on either;
 * the expected tags are not checked here, and each header line class checks for the ones it cannot do without. A
 * value is quoted when it starts with a double quote right after its {@code =}, and inside quotes {@code \"} and
 * {@code \\} are escapes. Outside quotes only the first {@code =} of an attribute separates key from value, so IDs
 * and values may themselves contain {@code =} and {@code "}.
 */
class VCF4Parser implements VCFLineParser {

    @Override
    public Map<String, String> parseLine(
            final String valueLine, final List<String> expectedTagOrder, final List<String> recommendedTags) {
        final Map<String, String> ret = new LinkedHashMap<String, String>();
        StringBuilder builder = new StringBuilder();
        String key = null; // null while reading a key; set once its '=' has been seen
        boolean inQuote = false;
        boolean escape = false;

        final int last = valueLine.length() - 1;
        for (int index = 0; index <= last; index++) {
            final char c = valueLine.charAt(index);
            if (inQuote) {
                if (escape) {
                    // only a double quote and a backslash can be escaped; any other backslash is copied through
                    if (c != '"' && c != '\\') {
                        builder.append('\\');
                    }
                    builder.append(c);
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inQuote = false;
                } else {
                    builder.append(c);
                }
            } else if (c == '"' && key != null && builder.length() == 0) {
                inQuote = true;
            } else if (c == '<' && index == 0) {
                // the opening bracket
            } else if (c == '>' && index == last) {
                putAttribute(ret, key, builder);
            } else if (c == '=' && key == null) {
                key = builder.toString().trim();
                builder = new StringBuilder();
            } else if (c == ',') {
                putAttribute(ret, key, builder);
                key = null;
                builder = new StringBuilder();
            } else {
                builder.append(c);
            }
        }

        if (inQuote) {
            throw new TribbleException.InvalidHeader("Unclosed quote in header line value " + valueLine);
        }
        return ret;
    }

    /** A token without an {@code =} is kept as a key with an empty value; an empty token is dropped. */
    private static void putAttribute(
            final Map<String, String> attributes, final String key, final StringBuilder value) {
        final String text = value.toString().trim();
        if (key != null) {
            attributes.put(key, text);
        } else if (!text.isEmpty()) {
            attributes.put(text, "");
        }
    }
}

class VCF3Parser implements VCFLineParser {

    @Override
    public Map<String, String> parseLine(
            String valueLine, List<String> expectedTagOrder, List<String> recommendedTags) {
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
        for (char c : valueLine.toCharArray()) {
            switch (c) {
                case ('\"'):
                    inQuote = !inQuote;
                    break; // a quote means we ignore ',' in our strings, keep track of it
                case (','):
                    if (!inQuote) {
                        ret.put(expectedTagOrder.get(tagIndex++), builder.toString());
                        builder = new StringBuilder();
                        break;
                    } // drop the current key value to the return map
                default:
                    builder.append(c); // otherwise simply append to the current string
            }
            index++;
        }
        ret.put(expectedTagOrder.get(tagIndex++), builder.toString());

        // validate the tags against the expected list
        index = 0;
        if (tagIndex != expectedTagOrder.size())
            throw new IllegalArgumentException(
                    "Unexpected tag count " + tagIndex + ", we expected " + expectedTagOrder.size());
        for (String str : ret.keySet()) {
            if (!expectedTagOrder.get(index).equals(str))
                throw new IllegalArgumentException("Unexpected tag " + str + " in string " + valueLine);
            index++;
        }
        return ret;
    }
}
