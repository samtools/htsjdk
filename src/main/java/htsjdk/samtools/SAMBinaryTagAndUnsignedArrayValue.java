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

/**
 * Simple extension to SAMBinaryTagAndValue in order to distinguish unsigned array values, because
 * signedness cannot be determined by introspection of value. Must be array of byte, short, or int.
 *
 * @author alecw@broadinstitute.org
 */
public class SAMBinaryTagAndUnsignedArrayValue extends SAMBinaryTagAndValue {
    public SAMBinaryTagAndUnsignedArrayValue(final short tag, final Object value) {
        super(tag, value);
        if (!value.getClass().isArray() || value instanceof float[]) {
            throw new IllegalArgumentException("Attribute type " + value.getClass() +
                    " cannot be encoded as an unsigned array. Tag: " +
                    SAMTagUtil.getSingleton().makeStringTag(tag));
        }
    }

    /** Creates and returns a shallow copy of the list of tag/values. */
    @Override
    public SAMBinaryTagAndValue copy() {
        final SAMBinaryTagAndValue retval = new SAMBinaryTagAndUnsignedArrayValue(this.tag, this.value);
        if (next != null) retval.next = next.copy();
        return retval;
    }

    /** Creates and returns a deep copy of the list of tag/values. */
    @Override
    public SAMBinaryTagAndValue deepCopy() {
        final SAMBinaryTagAndValue retval = new SAMBinaryTagAndUnsignedArrayValue(this.tag, cloneValue());
        if (next != null) {
            retval.next = next.deepCopy();
        }
        return retval;
    }


    @Override
    public boolean isUnsignedArray() {
        return true;
    }
}
