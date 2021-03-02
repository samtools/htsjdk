package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Text transformer for attribute values embedded in VCF. VCF version 4.3 supports percent-encoding
 * of characters that have special meaning in VCF.
 */
public class VCFPercentEncodedTextTransformer implements VCFTextTransformer {
    final static private String ENCODING_SENTINEL_STRING = "%";
    final static private char ENCODING_SENTNEL_CHAR = '%';
    final static private int ENCODING_BASE_RADIX = 16;

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
     * Transform a single string, % encoding values that have special meanings in VCF.
     *
     * @param rawPart the raw string to be encoded
     * @return the encoded string
     */
    @Override
    public String encodeText(final String rawPart) {
        final StringBuilder sb = new StringBuilder(rawPart.length());
        for (int i = 0; i < rawPart.length(); i++) {
            final char c = rawPart.charAt(i);
            if (VCFPercentEncodedTextTransformer.isSpecialCharacter(c)) {
                final byte hi = (byte) (c >>> 4);
                final byte lo = (byte) (c & 0xf);
                sb.append(ENCODING_SENTNEL_CHAR);
                sb.append(VCFPercentEncodedTextTransformer.upperCaseHex(hi));
                sb.append(VCFPercentEncodedTextTransformer.upperCaseHex(lo));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static char upperCaseHex(final byte b) {
        return b > 9
            ? (char) ('A' + (b - 10))
            : (char) ('0' + b);
    }

    private static boolean isSpecialCharacter(final char c) {
        switch (c) {
            case ':':
            case ';':
            case '=':
            case '%':
            case ',':
            case '\r':
            case '\n':
            case '\t':
                return true;
            default:
                return false;
        }
    }

    /**
     * Transform a single string, % encoding values that have special meanings in VCF.
     * <p>
     * This method is suitable for encoding a header value in a key=value pair that is of type String (e.g. Description)
     * which have fewer restriction than fields in the body of the VCF such as INFO and FORMAT.
     *
     * @param rawPart String to encode
     * @return the encoded string
     */
    public String encodeHeaderText(final String rawPart) {
        final StringBuilder sb = new StringBuilder(rawPart.length());
        for (int i = 0; i < rawPart.length(); i++) {
            final char c = rawPart.charAt(i);
            if (VCFPercentEncodedTextTransformer.isAllowedInHeader(c)) {
                final byte hi = (byte) (c >>> 4);
                final byte lo = (byte) (c & 0xf);
                sb.append(ENCODING_SENTNEL_CHAR);
                sb.append(VCFPercentEncodedTextTransformer.upperCaseHex(hi));
                sb.append(VCFPercentEncodedTextTransformer.upperCaseHex(lo));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isAllowedInHeader(final char c) {
        switch (c) {
            case '%':
            case '\r':
            case '\n':
            case '\t':
                return true;
            default:
                return false;
        }
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
            final StringBuilder builder = new StringBuilder(rawText.length());
            for (int i = 0; i < rawText.length(); i++) {
                final char c = rawText.charAt(i);
                if (c == ENCODING_SENTNEL_CHAR && ((i + 2) < rawText.length())) {
                    try {
                        final char[] trans = Character.toChars(Integer.parseInt(rawText.substring(i + 1, i + 3), ENCODING_BASE_RADIX));
                        if (trans.length != 1) {
                            throw new TribbleException(String.format("escape sequence '%c' corresponds to an invalid encoding in '%s'", c, rawText));
                        }
                        builder.append(trans[0]);
                        i += 2;
                    } catch (final IllegalArgumentException e) {
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
