package htsjdk.variant.vcf;

import java.io.IOException;

/**
 * Text encoder for attribute values embedded in VCF. VCF version 4.3 supports percent-encoding
 * of characters that have special meaning in VCF.
 */
public class VCFPercentEncoder {

    private static final char ENCODING_PREFIX_CHAR = '%';

    /**
     * Encode a string and return the encoded string
     * percent encoding characters all characters with special meaning in VCF characters
     * <p>
     * This method is suitable for encoding values in INFO and FORMAT fields
     *
     * @param value String to encode
     */
    public static String percentEncode(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        VCFPercentEncoder.percentEncode(value, sb);
        return sb.toString();
    }

    /**
     * Encode a string and append directly to an Appendable,
     * percent encoding characters all characters with special meaning in VCF characters
     * <p>
     * This method is suitable for encoding values in INFO and FORMAT fields
     *
     * @param value String to encode
     * @param out   Appendable to append to
     */
    public static void percentEncode(final String value, final Appendable out) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (VCFPercentEncoder.isSpecialCharacter(c)) {
                VCFPercentEncoder.appendPercentEncodedChar(c, out);
            } else {
                out.append(c);
            }
        }
    }

    /**
     * Encode a string and append directly to a StringBuilder,
     * percent encoding characters all characters with special meaning in VCF characters
     * <p>
     * This method is suitable for encoding values in INFO and FORMAT fields
     *
     * @param value String to encode
     * @param out   StringBuilder to append to
     */
    public static void percentEncode(final String value, final StringBuilder out) {
        try {
            VCFPercentEncoder.percentEncode(value, (Appendable) out);
        } catch (final IOException ignored) {
        } // Appending to StringBuilder should never fail
    }

    /**
     * Encode a header value and append directly to a StringBuilder, percent encoding characters disallowed characters
     * <p>
     * This method is suitable for encoding a header value in a key=value pair that is of type String (e.g. Description)
     *
     * @param value String to encode
     * @param out   StringBuilder to append to
     */
    public static void percentEncodeHeaderValue(final String value, final StringBuilder out) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (VCFPercentEncoder.isAllowedinHeaderValue(c)) {
                try {
                    VCFPercentEncoder.appendPercentEncodedChar(c, out);
                } catch (final IOException ignored) {
                }
            } else {
                out.append(c);
            }
        }
    }

    /**
     * Reject only a subset of the full list of special characters, as header values have fewer restrictions
     *
     * @param c character to validate
     */
    private static boolean isAllowedinHeaderValue(final char c) {
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
     * Reject all characters with special meaning in VCF
     *
     * @param c character to validate
     */
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

    private static void appendPercentEncodedChar(final char c, final Appendable out) throws IOException {
        final byte hi = (byte) (c >>> 4);
        final byte lo = (byte) (c & 0xf);
        out.append(ENCODING_PREFIX_CHAR);
        out.append(VCFPercentEncoder.upperCaseHex(hi));
        out.append(VCFPercentEncoder.upperCaseHex(lo));
    }

    private static char upperCaseHex(final byte b) {
        return b > 9
            ? (char) ('A' + (b - 10))
            : (char) ('0' + b);
    }
}
