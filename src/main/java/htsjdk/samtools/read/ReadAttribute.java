/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.read;

import htsjdk.samtools.SAMUtils;

/**
 * Attribute for a {@link Read}.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public interface ReadAttribute<T> {

    /**
     * Gets the read attribute tag.
     *
     * <p>Subclasses should return a value that returns {@code true} by {@link #isValidTag(String)}.
     *
     * @return the tag.
     */
    String getTag();

    /**
     * Gets the read attribute value.
     *
     * <p>Subclasses should return a value on which {@link #isAllowedAttributeValue(Object)} returns {@code true}.
     *
     * @return the value associated with the tag. Never {@code null}.
     */
    T getValue();


    /**
     * Checks if the tag value is valid. A valid tag should have exactly 2-characters.
     *
     * @param tag the tag to check.
     *
     * @return {@code true} if the tag is valid; {@code false} otherwise.
     */
    public static boolean isValidTag(final String tag) {
        return tag != null && tag.length() == 2;
    }

    /**
     * Checks if the value is allowed as an attribute value.
     *
     * @param value the value to be checked.
     *
     * @return {@code true} if the value is valid; {@code false} otherwise.
     */
    // TODO: SAMBinaryTagAndValue.isAllowedAttributeValue should be deprecated in favor of this method
    public static boolean isAllowedAttributeValue(final Object value) {
        if (value instanceof Byte ||
                value instanceof Short ||
                value instanceof Integer ||
                value instanceof String ||
                value instanceof Character ||
                value instanceof Float ||
                value instanceof byte[] ||
                value instanceof short[] ||
                value instanceof int[] ||
                value instanceof float[]) {
            return true;
        }

        // A special case for Longs: we require Long values to fit into either a uint32_t or an int32_t,
        // as that is what the BAM spec allows.
        if (value instanceof Long) {
            return SAMUtils.isValidUnsignedIntegerAttribute((Long) value)
                    || ((Long) value >= Integer.MIN_VALUE && (Long) value <= Integer.MAX_VALUE);
        }
        return false;
    }
}
