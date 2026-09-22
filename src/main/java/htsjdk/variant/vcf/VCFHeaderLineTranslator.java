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
                version != null && version.isOlderThan(VCFHeaderVersion.VCF4_0) ? VCF3_PARSER : VCF4_PARSER;
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
 * value is quoted when a double quote is the first thing after its {@code =}; what is inside the quotes is kept
 * exactly, whitespace included, with {@code \"} and {@code \\} as escapes. Outside quotes only the first
 * {@code =} of an attribute separates key from value, so IDs and values may themselves contain {@code =} and
 * {@code "}, and surrounding whitespace is dropped.
 *
 * <p>Lines in the wild are not always well formed, and what can be read without guessing is: a double quote
 * inside a quoted value that the writer forgot to escape is taken as part of the value (see
 * {@link #closesValue}), angle brackets outside quotes are never content, whitespace around the line is ignored,
 * and a line that lacks its closing bracket still yields its last attribute.
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
        int quotedLength = -1; // how much of the value was inside quotes, or -1 for a value without quotes

        final String line = valueLine.trim();
        final int last = line.length() - 1;
        for (int index = 0; index <= last; index++) {
            final char c = line.charAt(index);
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
                } else if (c == '"' && closesValue(line, index)) {
                    inQuote = false;
                    quotedLength = builder.length();
                } else {
                    builder.append(c);
                }
            } else if (c == '"' && key != null && quotedLength < 0 && isBlank(builder)) {
                inQuote = true;
                builder.setLength(0);
            } else if (c == '<' || c == '>') {
                // the pair around the line, or a stray one: never part of a key or of an unquoted value
                if (c == '>' && index == last) {
                    putAttribute(ret, key, builder, quotedLength);
                }
            } else if (c == '=' && key == null) {
                key = builder.toString().trim();
                builder = new StringBuilder();
            } else if (c == ',') {
                putAttribute(ret, key, builder, quotedLength);
                key = null;
                quotedLength = -1;
                builder = new StringBuilder();
            } else {
                builder.append(c);
            }
        }

        if (inQuote) {
            throw new TribbleException.InvalidHeader("Unclosed quote in header line value " + valueLine);
        }
        if (last < 0 || line.charAt(last) != '>') {
            // no closing bracket to have ended the last attribute
            putAttribute(ret, key, builder, quotedLength);
        }
        return ret;
    }

    /**
     * Whether the double quote at {@code quoteIndex}, met inside a quoted value, ends that value: it does if the
     * line ends there, or if another attribute follows, that is, a comma and then a key up to its {@code =} (or up
     * to the end of the line, for a key without a value), with any number of keys without a value before it.
     * Followed by anything else it is a quote the writer did not escape, as in
     * {@code Description="the "best", really"}, and belongs to the value.
     */
    private static boolean closesValue(final String line, final int quoteIndex) {
        final int last = line.length() - 1;
        int index = quoteIndex + 1;
        while (index <= last && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        if (index > last) {
            return true;
        }
        if (line.charAt(index) != ',') {
            return line.charAt(index) == '>' && index == last;
        }
        for (index++; index <= last; index++) {
            final char c = line.charAt(index);
            if (c == '=') {
                return true;
            }
            if (c == '>') {
                return index == last;
            }
            // a comma only ends a key without a value, so what follows it decides
            if (c == '"' || c == '<') {
                return false;
            }
        }
        return false;
    }

    private static boolean isBlank(final StringBuilder text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * A token without an {@code =} is kept as a key with an empty value; an empty token is dropped. The first
     * {@code quotedLength} characters of a quoted value are kept as they are.
     */
    private static void putAttribute(
            final Map<String, String> attributes, final String key, final StringBuilder value, final int quotedLength) {
        final String text = quotedLength < 0
                ? value.toString().trim()
                : value.substring(0, quotedLength)
                        + value.substring(quotedLength).trim();
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
