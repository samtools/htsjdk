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

import java.io.Serializable;
import java.util.Arrays;

/**
 * Holds a SAMRecord attribute and the tagname (in binary form) for that attribute.
 * SAMRecord stores tag name and value in this form, because much String creation is avoided this way.
 * See SAMTagUtil to convert the tag to String form.
 *
 * Values associated with attribute tags must be of a type that implements {@link Serializable} or else
 * serialization will fail. Accepted types are String, scalar types Short, Integer, Character, Float,
 * and Long (see below); array types byte[], short[], int[] and float[]. Cannot be null.
 *
 * Long valued attributes are constrained to the range [Integer.MIN_VALUE, BinaryCodec.MAX_UINT],
 * which includes the entire range of signed ints [Integer.MIN_VALUE, Integer.MAX_VALUE] and
 * the entire range of unsigned ints that can be stored per the BAM spec [0, (Integer.MAX_VALUE * 2) + 1].
 *
 * @author alecw@broadinstitute.org
 */
public class SAMBinaryTagAndValue implements Serializable {
    public static final long serialVersionUID = 1L;

    public final short tag;
    public final Object value;
    protected SAMBinaryTagAndValue next = null;

    /**
     * @param tag tagname (in binary form) for this attribute
     * @param value value for this attribute (must be of a type that implements {@link Serializable}
     *              or else serialization will fail). Cannot be null.
     */
    public SAMBinaryTagAndValue(final short tag, final Object value) {
        if (null == value) {
            throw new IllegalArgumentException("SAMBinaryTagAndValue value may not be null");
        }
        if (!isAllowedAttributeValue(value)) {
            throw new IllegalArgumentException("Attribute type " + value.getClass() + " not supported. Tag: " +
                    SAMTagUtil.getSingleton().makeStringTag(tag));
        }
        this.tag = tag;
        this.value = value;
    }

    // Inspect the proposed value to determine if it is an allowed value type,
    // and if the value is in range.
    protected static boolean isAllowedAttributeValue(final Object value) {
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

    @Override public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return typeSafeEquals((SAMBinaryTagAndValue) o);
    }

    /** Type safe equals method that recurses down the list looking for equality. */
    private boolean typeSafeEquals(final SAMBinaryTagAndValue that) {
        if (this.tag != that.tag) return false;
        if (this.valueEquals(that)) {
            if (this.next == null) return that.next == null;
            else return this.next.equals(that.next);
        }
        else {
            return false;
        }
    }

    private boolean valueEquals(SAMBinaryTagAndValue that) {
        if (this.value instanceof byte[]) {
            return that.value instanceof byte[] ?
                Arrays.equals((byte[])this.value, (byte[])that.value) : false;
        }
        else if (this.value instanceof short[]) {
            return that.value instanceof short[] ?
                    Arrays.equals((short[])this.value, (short[])that.value) : false;
        }
        else if (this.value instanceof int[]) {
            return that.value instanceof int[] ?
                    Arrays.equals((int[])this.value, (int[])that.value) : false;
        }
        else if (this.value instanceof float[]) {
            return that.value instanceof float[] ?
                    Arrays.equals((float[])this.value, (float[])that.value) : false;
        }
        else {
            // otherwise, the api limits the remaining possible value types to
            // immutable (String or boxed primitive) types
            return this.value.equals(that.value);
        }
    }

    @Override
    public int hashCode() {
        int valueHash;
        if (this.value instanceof byte[]) {
            valueHash = Arrays.hashCode((byte[])this.value);
        }
        else if (this.value instanceof short[]) {
            valueHash = Arrays.hashCode((short[])this.value);
        }
        else if (this.value instanceof int[]) {
            valueHash = Arrays.hashCode((int[])this.value);
        }
        else if (this.value instanceof float[]) {
            valueHash = Arrays.hashCode((float[])this.value);
        }
        else {
            // otherwise, the api limits the remaining possible value types to
            // immutable (String or boxed primitive) types
            valueHash = value.hashCode();
        }

        return 31 * tag + valueHash;
    }

    /** Creates and returns a shallow copy of the list of tag/values. */
    public SAMBinaryTagAndValue copy() {
        final SAMBinaryTagAndValue retval = new SAMBinaryTagAndValue(this.tag, this.value);
        if (next != null) {
            retval.next = next.copy();
        }
        return retval;
    }

    /** Creates and returns a deep copy of the list of tag/values. */
    public SAMBinaryTagAndValue deepCopy() {
        final SAMBinaryTagAndValue retval = new SAMBinaryTagAndValue(this.tag, cloneValue());
        if (next != null) {
            retval.next = next.deepCopy();
        }
        return retval;
    }

    /* Create and return a clone of value object */
    protected Object cloneValue() {
        Object valueClone;

        if (value instanceof byte[]) {
            valueClone = ((byte[]) value).clone();
        }
        else if (value instanceof short[]) {
            valueClone = ((short[]) value).clone();
        }
        else if (value instanceof int[]) {
            valueClone = ((int[]) value).clone();
        }
        else if (value instanceof float[]) {
            valueClone = ((float[]) value).clone();
        }
        else {
            // otherwise, the api limits the remaining possible value types to
            // immutable (String or boxed primitive) types
            valueClone = value;
        }
        return valueClone;
    }

    // The methods below are for implementing a light-weight, single-direction linked list

    public SAMBinaryTagAndValue getNext() { return this.next; }

    /** Inserts at item into the ordered list of attributes and returns the head of the list/sub-list */
    public SAMBinaryTagAndValue insert(final SAMBinaryTagAndValue attr) {
        if (attr == null) return this;
        if (attr.next != null) throw new IllegalStateException("Can only insert single tag/value combinations.");

        if (attr.tag < this.tag) {
            // attr joins the list ahead of this element
            attr.next = this;
            return attr;
        }
        else if (this.tag == attr.tag) {
            // attr replaces this in the list
            attr.next = this.next;
            return attr;
        }
        else if (this.next == null) {
            // attr gets stuck on the end
            this.next = attr;
            return this;
        }
        else {
            // attr gets inserted somewhere in the tail
            this.next = this.next.insert(attr);
            return this;
        }
    }

    /** Removes a tag from the list and returns the new head of the list/sub-list. */
    public SAMBinaryTagAndValue remove(final short tag) {
        if (this.tag == tag) return this.next;
        else {
            if (this.next != null) this.next = this.next.remove(tag);
            return this;
        }
    }

    /** Returns the SAMBinaryTagAndValue that contains the required tag, or null if not contained. */
    public SAMBinaryTagAndValue find(final short tag) {
        if (this.tag == tag) return this;
        else if (this.tag > tag || this.next == null) return null;
        else return this.next.find(tag); 
    }

    public boolean isUnsignedArray() {
        return false;
    }
}
