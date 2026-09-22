package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Text transformer for attribute values embedded in VCF. VCF version 4.3 supports percent-encoding
 * of characters that have special meaning in VCF.
 */
public class VCFPercentEncodedTextTransformer implements VCFTextTransformer {
    private static final String ENCODING_SENTINEL_STRING = "%";
    private static final char ENCODING_SENTNEL_CHAR = '%';
    private static final int ENCODING_BASE_RADIX = 16;

    /**
     * Transform a single string, replacing % encoded values with their corresponding text.
     *
     * @param rawPart the raw string to be decoded
     * @return the decoded string
     * @throws TribbleException if the the encoding is uninterpretable
     */
    @Override
    public String decodeText(final String rawPart) {
        return decodePercentEncodedChars(rawPart);
    }

    /**
     * Transform a list of strings, replacing % encoded values with their corresponding text in each string.
     *
     * @param rawParts  a list of raw strings
     * @return a list of decoded strings
     * @throws TribbleException if the the encoding is uninterpretable
     */
    @Override
    public List<String> decodeText(final List<String> rawParts) {
        return rawParts.stream().map(this::decodeText).collect(Collectors.toList());
    }

    /**
     * Percent-encodes one value for a VCF record body (not a header): the characters that have a special meaning
     * there, which are {@code %} (0x25), {@code :} (0x3A), {@code ;} (0x3B), {@code =} (0x3D), {@code ,} (0x2C),
     * CR (0x0D), LF (0x0A) and TAB (0x09), per VCF 4.3 section 1.2. Returns the same instance when nothing needs
     * encoding, so the common path is allocation-free.
     *
     * @param value a single value to encode; a comma in it is part of the value and is encoded
     * @return the encoded string, or the same instance if nothing needed encoding
     */
    public static String percentEncode(final String value) {
        return encode(value, true);
    }

    /**
     * Percent-encodes the elements of a list that is already joined with commas, as {@link #percentEncode(String)}
     * does but leaving the commas alone: they are the list's delimiters, not part of any element.
     *
     * @param joinedValues the comma-joined list to encode
     * @return the encoded string, or the same instance if nothing needed encoding
     */
    public static String percentEncodeJoinedList(final String joinedValues) {
        return encode(joinedValues, false);
    }

    private static String encode(final String value, final boolean encodeCommas) {
        for (int i = 0; i < value.length(); i++) {
            if (needsEncoding(value.charAt(i), encodeCommas)) {
                return encodeFrom(value, i, encodeCommas);
            }
        }
        return value;
    }

    private static boolean needsEncoding(final char c, final boolean encodeCommas) {
        return c == '%'
                || c == ':'
                || c == ';'
                || c == '='
                || (c == ',' && encodeCommas)
                || c == '\r'
                || c == '\n'
                || c == '\t';
    }

    private static String encodeFrom(final String value, final int firstSpecial, final boolean encodeCommas) {
        final StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append(value, 0, firstSpecial);
        for (int i = firstSpecial; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (needsEncoding(c, encodeCommas)) {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Transform input strings containing embedded percent=encoded characters. For example, when given the
     * string '%3D%41' will return the string '=A'.
     *
     * @param rawText a string containing zero or more embedded encodings
     * @return a string with all encoded characters replaced with the corresponding character
     * @throws TribbleException if the the encoding is uninterpretable
     */
    protected static String decodePercentEncodedChars(final String rawText) {
        if (rawText.contains(ENCODING_SENTINEL_STRING)) {
            StringBuilder builder = new StringBuilder(rawText.length());
            for (int i = 0; i < rawText.length(); i++) {
                final char c = rawText.charAt(i);
                if (c == ENCODING_SENTNEL_CHAR && ((i + 2) < rawText.length())) {
                    try {
                        final char[] trans = Character.toChars(
                                Integer.parseInt(rawText.substring(i + 1, i + 3), ENCODING_BASE_RADIX));
                        if (trans.length != 1) {
                            throw new TribbleException(String.format(
                                    "escape sequence '%c' corresponds to an invalid encoding in '%s'", c, rawText));
                        }
                        builder.append(trans[0]);
                        i += 2;
                    } catch (IllegalArgumentException e) {
                        builder.append(c);
                    }
                } else {
                    builder.append(c);
                }
            }
            return builder.toString();
        }
        return rawText;
    }
}
