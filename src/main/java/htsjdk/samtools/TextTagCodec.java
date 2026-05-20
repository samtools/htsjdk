/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.DateParser;
import htsjdk.samtools.util.Iso8601Date;
import htsjdk.samtools.util.StringUtil;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Map;

/**
 * Converter between SAM text representation of a tag, and in-memory Object representation.
 * Note that this class is not thread-safe, in that some local variables have been made into instance
 * variables in order to reduce object creation, but it should not ever be the case that the same
 * instance is used in multiple threads.
 */
public class TextTagCodec {
    // 3 fields for non-empty strings 2 fields if the string is empty.
    private static final int NUM_TAG_FIELDS = 3;

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * This is really a local variable of decode(), but allocated here to reduce allocations.
     */
    private final String[] fields = new String[NUM_TAG_FIELDS];

    /**
     * Convert in-memory representation of tag to SAM text representation.
     * @param tagName Two-character tag name.
     * @param value Tag value as appropriate Object subclass.
     * @return SAM text String representation, i.e. name:type:value
     */
    public String encode(final String tagName, Object value) {
        final StringBuilder sb = new StringBuilder(tagName);
        sb.append(':');
        char tagType = BinaryTagCodec.getTagValueType(value);
        switch (tagType) {
            case 'c':
            case 'C':
            case 's':
            case 'S':
            case 'I':
                tagType = 'i';
        }
        if (tagType == 'H') {
            // H should never happen anymore.
            value = StringUtil.bytesToHexString((byte[]) value);
        } else if (tagType == 'B') {
            value = getArrayType(value, false) + encodeArrayValue(value);
        } else if (tagType == 'i') {
            final long longVal = ((Number) value).longValue();
            // as the spec says: [-2^31, 2^32)
            if (longVal < Integer.MIN_VALUE || longVal > BinaryCodec.MAX_UINT) {
                throw new IllegalArgumentException("Value for tag " + tagName
                        + " cannot be stored in either a signed or unsigned 32-bit integer: " + longVal);
            }
        }
        sb.append(tagType).append(':').append(value.toString());
        return sb.toString();
    }

    private static char getArrayType(final Object array, final boolean isUnsigned) {
        final char type;
        final Class<?> componentType = array.getClass().getComponentType();
        if (componentType == Float.TYPE) {
            if (isUnsigned) throw new IllegalArgumentException("float array cannot be unsigned");
            return 'f';
        } else if (componentType == Byte.TYPE) type = 'c';
        else if (componentType == Short.TYPE) type = 's';
        else if (componentType == Integer.TYPE) type = 'i';
        else throw new IllegalArgumentException("Unrecognized array type " + componentType);
        return (isUnsigned ? Character.toUpperCase(type) : type);
    }

    private static String encodeArrayValue(final Object value) {
        final int length = Array.getLength(value);
        final StringBuilder ret = new StringBuilder();
        for (int i = 0; i < length; ++i) {
            ret.append(',');
            ret.append(Array.get(value, i).toString());
        }
        return ret.toString();
    }

    private static long[] widenToUnsigned(final Object array) {
        final Class<?> componentType = array.getClass().getComponentType();
        final long mask;
        if (componentType == Byte.TYPE) mask = 0xffL;
        else if (componentType == Short.TYPE) mask = 0xffffL;
        else if (componentType == Integer.TYPE) mask = 0xffffffffL;
        else throw new IllegalArgumentException("Unrecognized unsigned array type " + componentType);
        final long[] ret = new long[Array.getLength(array)];
        for (int i = 0; i < ret.length; ++i) {
            ret[i] = Array.getLong(array, i) & mask;
        }
        return ret;
    }

    String encodeUnsignedArray(final String tagName, final Object array) {
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("Non-array passed to encodeUnsignedArray: " + array.getClass());
        }
        final long[] widened = widenToUnsigned(array);
        return tagName + ":B:" + getArrayType(array, true) + encodeArrayValue(widened);
    }

    /**
     * Encode a standard header tag, which should not have a type field.
     * @param tagName 2-character String.
     * @param value Not necessarily a String.  Some of these are integers but the type is implied by
     * the tagName.  Converted to String with toString().
     * @return Colon-separated text representation suitable for a SAM header, i.e. name:value.
     */
    public String encodeUntypedTag(final String tagName, final Object value) {
        return new StringBuilder(tagName).append(':').append(value.toString()).toString();
    }

    /**
     * Convert typed tag in SAM text format (name:type:value) into tag name and Object value representation.
     *
     * @param tag SAM text format name:type:value tag.
     * @return Tag name as 2-character String, and tag value in appropriate class based on tag type.
     * If value is an unsigned array, then the value is a TagValueAndUnsignedArrayFlag object.
     */
    public Map.Entry<String, Object> decode(final String tag) {
        decodeValue(tag);
        return new AbstractMap.SimpleImmutableEntry<>(tag.substring(0, 2), lastValue);
    }

    /**
     * Holds the most recently-decoded value when {@link #decodeValue} is used to avoid
     * the {@link Map.Entry} allocation in {@link #decode}. This is the value associated
     * with the most recent {@link #decodeValue} call.
     */
    private Object lastValue;

    /**
     * @return the value decoded by the most recent {@link #decodeValue} call.
     */
    Object getLastValue() {
        return lastValue;
    }

    /**
     * Decode a tag string ({@code KEY:T:VALUE}) and store the decoded value in {@link #lastValue},
     * to be retrieved by {@link #getLastValue}. Faster than {@link #decode} because it skips the
     * 2-character key substring and the {@link Map.Entry} bookkeeping; the caller is responsible
     * for computing the binary tag directly from the input String.
     */
    void decodeValue(final String tag) {
        // Tags follow the fixed layout KEY:T:VALUE where KEY is 2 chars and T is 1 char,
        // so a direct character index walk is cheaper than splitting into a String[].
        if (tag.length() < 4 || tag.charAt(2) != ':' || (tag.length() > 4 && tag.charAt(4) != ':')) {
            throw new SAMFormatException("Malformed tag '" + tag + "'");
        }
        final char typeChar = tag.charAt(3);
        final String stringVal = tag.length() == 4 ? "" : tag.substring(5);
        lastValue = convertStringToObject(typeChar, stringVal);
    }

    /**
     * Byte-based variant of {@link #decodeValue(String)}. Parses a tag from a {@code KEY:T:VALUE}
     * byte range without allocating a {@link String} for the tag substring. For value types whose
     * parsers operate on bytes directly ({@code i}, {@code A}, {@code H}) no value-side
     * {@link String} is allocated either; types whose parsers require a {@link String}
     * ({@code Z}, {@code f}, {@code B}) construct one lazily for just the value bytes.
     */
    void decodeValue(final byte[] buf, final int off, final int len) {
        if (len < 4 || buf[off + 2] != ':' || (len > 4 && buf[off + 4] != ':')) {
            // Build the error message from the bytes so the user can see what we got.
            throw new SAMFormatException(
                    "Malformed tag '" + new String(buf, off, len, StandardCharsets.ISO_8859_1) + "'");
        }
        final char typeChar = (char) (buf[off + 3] & 0xff);
        // When len == 4 (e.g. "XY:Z" with no trailing colon+value) the tag has an empty value;
        // mirror the String overload which treats this as "". Without the guard we'd produce a
        // negative valueLen and crash inside the type-specific value parsers.
        final int valueOff = len > 4 ? off + 5 : off;
        final int valueLen = len > 4 ? len - 5 : 0;
        lastValue = convertBytesToObject(typeChar, buf, valueOff, valueLen);
    }

    private static Number parseIntegerTagValue(final byte[] buf, final int off, final int len) {
        if (len == 0) {
            throw new SAMFormatException("Tag of type i should have signed decimal value");
        }
        int i = 0;
        final boolean negative;
        final byte first = buf[off];
        if (first == '-') {
            negative = true;
            i = 1;
        } else if (first == '+') {
            negative = false;
            i = 1;
        } else {
            negative = false;
        }
        if (i == len) {
            throw new SAMFormatException("Tag of type i should have signed decimal value");
        }
        long acc = 0;
        for (; i < len; i++) {
            final byte b = buf[off + i];
            if (b < '0' || b > '9') {
                throw new SAMFormatException("Tag of type i should have signed decimal value");
            }
            acc = acc * 10 + (b - '0');
            if (acc > Integer.MAX_VALUE + 1L) {
                return parseIntegerTagValueSlow(new String(buf, off, len, StandardCharsets.ISO_8859_1));
            }
        }
        final long signed = negative ? -acc : acc;
        if (signed >= Integer.MIN_VALUE && signed <= Integer.MAX_VALUE) {
            return (int) signed;
        }
        return parseIntegerTagValueSlow(new String(buf, off, len, StandardCharsets.ISO_8859_1));
    }

    private static Object convertBytesToObject(final char type, final byte[] buf, final int off, final int len) {
        switch (type) {
            case 'Z':
                return new String(buf, off, len, StandardCharsets.ISO_8859_1);
            case 'A':
                if (len != 1) {
                    throw new SAMFormatException("Tag of type A should have a single-character value");
                }
                return (char) (buf[off] & 0xff);
            case 'i':
                return parseIntegerTagValue(buf, off, len);
            case 'f':
                try {
                    return Float.parseFloat(new String(buf, off, len, StandardCharsets.ISO_8859_1));
                } catch (NumberFormatException e) {
                    throw new SAMFormatException("Tag of type f should have single-precision floating point value");
                }
            case 'H':
                try {
                    return StringUtil.hexStringToBytes(new String(buf, off, len, StandardCharsets.ISO_8859_1));
                } catch (NumberFormatException e) {
                    throw new SAMFormatException(
                            "Tag of type H should have valid hex string with even number of digits");
                }
            case 'B':
                return covertStringArrayToObject(new String(buf, off, len, StandardCharsets.ISO_8859_1));
            default:
                throw new SAMFormatException("Unrecognized tag type: " + type);
        }
    }

    /**
     * Fast path for the {@code 'i'} tag value: parse a signed decimal directly into a
     * {@code Number}. The vast majority of SAM integer tags (e.g. {@code AS}, {@code NM},
     * {@code MQ}, {@code MAPQ}) are small signed integers that fit in {@code int}, so we
     * accumulate digits manually and skip {@link Long#parseLong}. Values outside the signed
     * 32-bit range fall through to {@link Long#parseLong} so that the SAM-allowed unsigned
     * 32-bit range [-2^31, 2^32) is still handled correctly. Returns {@link Integer} when the
     * value fits, {@link Long} otherwise (matching the prior contract).
     */
    private static Number parseIntegerTagValue(final String stringVal) {
        final int len = stringVal.length();
        if (len == 0) {
            throw new SAMFormatException("Tag of type i should have signed decimal value");
        }
        int i = 0;
        final boolean negative;
        final char first = stringVal.charAt(0);
        if (first == '-') {
            negative = true;
            i = 1;
        } else if (first == '+') {
            negative = false;
            i = 1;
        } else {
            negative = false;
        }
        if (i == len) {
            throw new SAMFormatException("Tag of type i should have signed decimal value");
        }
        // Accumulate into long to detect overflow past Integer range. If overflow occurs we
        // fall back to the Long path which handles the unsigned-32-bit case correctly.
        long acc = 0;
        for (; i < len; i++) {
            final char c = stringVal.charAt(i);
            if (c < '0' || c > '9') {
                throw new SAMFormatException("Tag of type i should have signed decimal value");
            }
            acc = acc * 10 + (c - '0');
            if (acc > Integer.MAX_VALUE + 1L) {
                // Out of signed int range -- defer to the broader spec path.
                return parseIntegerTagValueSlow(stringVal);
            }
        }
        final long signed = negative ? -acc : acc;
        if (signed >= Integer.MIN_VALUE && signed <= Integer.MAX_VALUE) {
            return (int) signed;
        }
        return parseIntegerTagValueSlow(stringVal);
    }

    private static Number parseIntegerTagValueSlow(final String stringVal) {
        final long lValue;
        try {
            lValue = Long.parseLong(stringVal);
        } catch (NumberFormatException e) {
            throw new SAMFormatException("Tag of type i should have signed decimal value");
        }
        if (lValue >= Integer.MIN_VALUE && lValue <= Integer.MAX_VALUE) {
            return (int) lValue;
        } else if (SAMUtils.isValidUnsignedIntegerAttribute(lValue)) {
            return lValue;
        } else {
            throw new SAMFormatException(
                    "Integer is out of range for both a 32-bit signed and unsigned integer: " + stringVal);
        }
    }

    private static Object convertStringToObject(final char type, final String stringVal) {
        switch (type) {
            case 'Z':
                return stringVal;
            case 'A':
                if (stringVal.length() != 1) {
                    throw new SAMFormatException("Tag of type A should have a single-character value");
                }
                return stringVal.charAt(0);
            case 'i':
                return parseIntegerTagValue(stringVal);
            case 'f':
                try {
                    return Float.parseFloat(stringVal);
                } catch (NumberFormatException e) {
                    throw new SAMFormatException("Tag of type f should have single-precision floating point value");
                }
            case 'H':
                try {
                    return StringUtil.hexStringToBytes(stringVal);
                } catch (NumberFormatException e) {
                    throw new SAMFormatException(
                            "Tag of type H should have valid hex string with even number of digits");
                }
            case 'B':
                return covertStringArrayToObject(stringVal);
            default:
                throw new SAMFormatException("Unrecognized tag type: " + type);
        }
    }

    private static Object covertStringArrayToObject(final String stringVal) {
        final String[] elementTypeAndValue = new String[2];

        final int numberOfTokens = StringUtil.splitConcatenateExcessTokens(stringVal, elementTypeAndValue, ',');

        if (elementTypeAndValue[0].length() != 1) {
            throw new SAMFormatException("Unrecognized element type for array tag value: " + elementTypeAndValue[0]);
        }

        final char elementType = elementTypeAndValue[0].charAt(0);

        final String[] stringValues =
                elementTypeAndValue[1] != null ? elementTypeAndValue[1].split(",") : EMPTY_STRING_ARRAY;
        if (elementType == 'f') {
            final float[] ret = new float[stringValues.length];
            for (int i = 0; i < stringValues.length; ++i) {
                try {
                    ret[i] = Float.parseFloat(stringValues[i]);
                } catch (NumberFormatException e) {
                    throw new SAMFormatException(
                            "Array tag of type f should have single-precision floating point value");
                }
            }
            return ret;
        }
        long mask = Long.MAX_VALUE;
        long minValue = Long.MAX_VALUE;
        long maxValue = Long.MIN_VALUE;
        final boolean isUnsigned = Character.isUpperCase(elementType);
        switch (Character.toLowerCase(elementType)) {
            case 'c':
                if (isUnsigned) {
                    mask = 0xffL;
                } else {
                    minValue = Byte.MIN_VALUE;
                    maxValue = Byte.MAX_VALUE;
                }
                break;
            case 's':
                if (isUnsigned) {
                    mask = 0xffffL;
                } else {
                    minValue = Short.MIN_VALUE;
                    maxValue = Short.MAX_VALUE;
                }
                break;
            case 'i':
                if (isUnsigned) {
                    mask = 0xffffffffL;
                } else {
                    minValue = Integer.MIN_VALUE;
                    maxValue = Integer.MAX_VALUE;
                }
                break;
            default:
                throw new SAMFormatException("Unrecognized array tag element type: " + elementType);
        }
        if (isUnsigned) {
            minValue = 0;
            maxValue = mask;
        }
        final long[] longValues = new long[stringValues.length];
        for (int i = 0; i < stringValues.length; ++i) {
            final long longValue;
            try {
                longValue = Long.parseLong(stringValues[i]);
            } catch (NumberFormatException e) {
                throw new SAMFormatException("Array tag of type " + elementType + " should have integral value");
            }
            if (longValue < minValue || longValue > maxValue) {
                throw new SAMFormatException("Value for element of array tag of type " + elementType
                        + " is out of allowed range: " + longValue);
            }
            longValues[i] = longValue;
        }

        switch (Character.toLowerCase(elementType)) {
            case 'c': {
                final byte[] array = new byte[longValues.length];
                for (int i = 0; i < longValues.length; ++i) array[i] = (byte) longValues[i];
                if (isUnsigned) return new TagValueAndUnsignedArrayFlag(array, true);
                else return array;
            }
            case 's': {
                final short[] array = new short[longValues.length];
                for (int i = 0; i < longValues.length; ++i) array[i] = (short) longValues[i];
                if (isUnsigned) return new TagValueAndUnsignedArrayFlag(array, true);
                else return array;
            }
            case 'i': {
                final int[] array = new int[longValues.length];
                for (int i = 0; i < longValues.length; ++i) array[i] = (int) longValues[i];
                if (isUnsigned) return new TagValueAndUnsignedArrayFlag(array, true);
                else return array;
            }
            default:
                throw new SAMFormatException("Unrecognized array tag element type: " + elementType);
        }
    }

    Iso8601Date decodeDate(final String dateStr) {
        try {
            return new Iso8601Date(dateStr);
        } catch (DateParser.InvalidDateException ex) {
            try {
                return new Iso8601Date(DateFormat.getDateTimeInstance().parse(dateStr));
            } catch (ParseException e) {
                try {
                    return new Iso8601Date(new Date(dateStr));
                } catch (Exception e1) {
                    throw new DateParser.InvalidDateException("Could not parse as date: " + dateStr, e);
                }
            }
        }
    }
}
