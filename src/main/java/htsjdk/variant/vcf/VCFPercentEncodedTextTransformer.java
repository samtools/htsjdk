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
                if (c == ENCODING_SENTNEL_CHAR) {
                    try {
                        final char[] trans = Character.toChars(Integer.parseInt(rawText.substring(i + 1, i + 3), ENCODING_BASE_RADIX));
                        if (trans.length != 1) {
                            throw new TribbleException(String.format("escape sequence '%c' corresponds to an invalid encoding in '%s'", c, rawText));
                        }
                        builder.append(trans[0]);
                        i += 2;
                    } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
                        throw new TribbleException(String.format("'%c' is not a valid percent encoded character in '%s'", c, rawText), e);
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
