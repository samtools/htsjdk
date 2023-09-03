package htsjdk.variant.vcf;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/**
 * Text transformer for attribute values embedded in VCF. VCF version 4.3 supports percent-encoding
 * of characters that have special meaning in VCF.
 */
public class VCFPercentEncodedTextTransformer implements VCFTextTransformer {
    private static final char ENCODING_SENTINEL_CHAR = '%';

    private static final byte invalidHexEncoding = ~0;
    private static final byte maxPossibleHexDigit = 'f' + 1;
    private static final byte[] hexToBytes = new byte[maxPossibleHexDigit];
    private static final char[] bytesToHex = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
    };

    static {
        Arrays.fill(hexToBytes, invalidHexEncoding);
        for (byte i = '0'; i <= '9'; i++) hexToBytes[i] = (byte) (i - '0');
        for (byte i = 'A'; i <= 'F'; i++) hexToBytes[i] = (byte) (10 + i - 'A');
        for (byte i = 'a'; i <= 'f'; i++) hexToBytes[i] = (byte) (10 + i - 'a');
    }

    /**
     * Transform a single string, replacing percent encoded values with their corresponding text.
     *
     * @param rawPart the raw string to be decoded
     * @return the decoded string
     */
    @Override
    public String decodeText(final String rawPart) {
        return percentDecode(rawPart);
    }

    /**
     * Transform a list of strings, replacing percent encoded values with their corresponding text in each string.
     *
     * @param rawParts a list of raw strings
     * @return a list of decoded strings
     */
    @Override
    public List<String> decodeText(final List<String> rawParts) {
        return rawParts.stream().map(VCFPercentEncodedTextTransformer::percentDecode).collect(Collectors.toList());
    }

    /**
     * Transform input strings containing embedded percent encoded characters. For example, when given the
     * string '%3D%41' will return the string '=A'.
     * <p>
     * This method is permissive in the input it accepts. Capitalized and lower case percent encoding are both
     * accepted, although the VCF spec only allows capitalized encoding. Uninterpretable escape sequences
     * (the % character followed by fewer than 2 characters before the end of the string, or the % sentinel
     * followed by 2 characters either of which does not match the regular expression [0-9A-Fa-f]) are passed through
     * uninterpreted.
     * <p>
     * If the input text does not contain any valid percent encoded sequences, a new string is not allocated,
     * and the original string is returned.
     *
     * @param rawString a string containing zero or more embedded encodings
     * @return a string with all encoded characters replaced with the corresponding character
     */
    public static String percentDecode(final String rawString) {
        int matches = 0;
        final int length = rawString.length();
        // A valid percent encoding requires at least 3 characters (the % character and 2 hex digits)
        // so we do not scan for % characters in the last 2 characters of the string
        // The spec does not specify how "truncated" encodings (% followed by fewer than 2 hex digits
        // before the string ends) should be interpreted, but we treat them as literal characters
        // and append them uninterpreted
        for (int i = 0, l = length - 2; i < l; i++) {
            if (rawString.charAt(i) == ENCODING_SENTINEL_CHAR) matches++;
        }

        if (matches == 0) {
            return rawString;
        } else {
            final StringBuilder s = new StringBuilder(length - 2 * matches);
            int lastMatchEnd = 0;
            int matched = 0;
            for (int i = 0; ; i++) {
                if (rawString.charAt(i) == ENCODING_SENTINEL_CHAR) {
                    final int hiDecoded = hexDigitToInt(rawString.charAt(++i));
                    final int loDecoded = hexDigitToInt(rawString.charAt(++i));
                    // Only decode and append the character if both characters after the % were interpretable
                    // as hex digits
                    if ((hiDecoded | loDecoded) != invalidHexEncoding) {
                        // Append on the portion of the original string that came before this matching character
                        s.append(rawString, lastMatchEnd, i - 2);
                        s.append((char) ((hiDecoded << 4) | (loDecoded & 0x0F)));
                        lastMatchEnd = i + 1;
                    }
                    matched++;

                    // Found all sequences to decode in the string, so append the rest of the original string
                    if (matched == matches) {
                        s.append(rawString, lastMatchEnd, length);
                        return s.toString();
                    }
                }
            }
        }
    }

    private static int hexDigitToInt(final char c) {
        return c < maxPossibleHexDigit ? hexToBytes[c] : invalidHexEncoding;
    }

    /**
     * Transform a single string, percent encoding values that have special meanings in VCF.
     *
     * @param rawPart the raw string to be encoded
     * @return the encoded string
     */
    @Override
    public String encodeText(final String rawPart) {
        return percentEncode(rawPart);
    }

    /**
     * Transform a single string, percent encoding values that have special meanings in VCF.
     *
     * @param rawPart the raw string to be encoded
     * @return the encoded string
     */
    public static String percentEncode(final String rawPart) {
        return percentEncode(rawPart, VCFPercentEncodedTextTransformer::isVCFSpecialChar);
    }

    /**
     * Transform a single string, percent encoding values that have special meanings in VCF.
     * <p>
     * This method is suitable for encoding a header value in a key=value pair that is of type String (e.g. Description)
     * which have fewer restrictions than fields in the body of the VCF such as INFO and FORMAT.
     *
     * @param rawString String to encode
     * @return the encoded string
     */
    public static String percentEncodeHeaderText(final String rawString) {
        return percentEncode(rawString, VCFPercentEncodedTextTransformer::isHeaderSpecialChar);
    }

    private static String percentEncode(final String rawString, final IntPredicate charPredicate) {
        int matches = 0;
        final int length = rawString.length();
        for (int i = 0; i < length; i++) {
            if (charPredicate.test(rawString.charAt(i))) matches++;
        }

        if (matches == 0) {
            return rawString;
        } else {
            final StringBuilder s = new StringBuilder(length + 2 * matches);
            int lastMatchEnd = 0;
            int matched = 0;
            for (int i = 0; ; i++) {
                final char c = rawString.charAt(i);
                if (charPredicate.test(c)) {
                    // Append on the portion of the original string that came before this matching character
                    s.append(rawString, lastMatchEnd, i);
                    s.append(ENCODING_SENTINEL_CHAR);
                    s.append(bytesToHex[c >>> 4]);
                    s.append(bytesToHex[c & 0x0F]);

                    lastMatchEnd = i + 1;
                    matched++;

                    // Found all matching characters in the string, so append the rest of the original string
                    if (matched == matches) {
                        s.append(rawString, lastMatchEnd, length);
                        return s.toString();
                    }
                }
            }
        }
    }

    // Characters that have special meaning in the value part of a structured header line key=value pair.
    // Note that this is less restrictive than the full set of characters with special meaning in VCF.
    // Space and comma are allowed due to the double-quoting introduced in VCF 4.2, and '=' is allowed because
    // key=value pairs are comma-delimited, so internal '=' is unambiguously part of the value as long as ',' is quoted
    private static boolean isHeaderSpecialChar(final int c) {
        switch (c) {
            case '\n':
            case '\t':
            case '\r':
            case '%':
                return true;
            default:
                return false;
        }
    }

    private static boolean isVCFSpecialChar(final int c) {
        switch (c) {
            case '\n':
            case '\t':
            case '\r':
            case '%':
            case ',':
            case ':':
            case ';':
            case '=':
                return true;
            default:
                return false;
        }
    }
}
