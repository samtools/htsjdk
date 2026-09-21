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

package htsjdk.variant.variantcontext.writer;

import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCompoundHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFPercentEncodedTextTransformer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * See #BCFWriter for documentation on this classes role in encoding BCF2 files
 *
 * @author Mark DePristo
 * @since 06/12
 */
public abstract class BCF2FieldEncoder {
    /**
     * The header line describing the field we will encode values of
     */
    final VCFCompoundHeaderLine headerLine;

    /**
     * The BCF2 type we'll use to encoder this field, if it can be determined statically.
     * If not, this variable must be null
     */
    final BCF2Type staticType;

    /**
     * The integer offset into the strings map of the BCF2 file corresponding to this
     * field.
     */
    final int dictionaryOffset;

    /**
     * The integer type we use to encode our dictionary offset in the BCF2 file
     */
    final BCF2Type dictionaryOffsetType;

    /** Whether shorter vectors are padded with one MISSING then END_OF_VECTOR (2.2) or all MISSING (2.1). */
    final boolean useEndOfVector;

    /** Whether string values are percent-encoded (VCF >= 4.3). */
    final boolean percentEncode;

    /** Whether string lists are written without a leading comma (BCF 2.2 / htslib form). */
    final boolean htslibStringLists;

    // ----------------------------------------------------------------------
    //
    // Constructor
    //
    // ----------------------------------------------------------------------

    private BCF2FieldEncoder(
            final VCFCompoundHeaderLine headerLine,
            final Map<String, Integer> dict,
            final BCF2Type staticType,
            final boolean useEndOfVector,
            final boolean percentEncode,
            final boolean htslibStringLists) {
        this.headerLine = headerLine;
        this.staticType = staticType;
        this.useEndOfVector = useEndOfVector;
        this.percentEncode = percentEncode;
        this.htslibStringLists = htslibStringLists;

        final Integer offset = dict.get(getField());
        if (offset == null)
            throw new IllegalStateException(
                    "Format error: could not find string " + getField() + " in header as required by BCF");
        this.dictionaryOffset = offset;
        dictionaryOffsetType = BCF2Utils.determineIntegerType(offset);
    }

    // ----------------------------------------------------------------------
    //
    // Basic accessors
    //
    // ----------------------------------------------------------------------

    public final String getField() {
        return headerLine.getID();
    }

    /**
     * Write the field key (dictionary offset and type) into the BCF2Encoder stream
     *
     * @param encoder where we write our dictionary offset
     * @throws IOException
     */
    public final void writeFieldKey(final BCF2Encoder encoder) throws IOException {
        encoder.encodeTypedInt(dictionaryOffset, dictionaryOffsetType);
    }

    @Override
    public String toString() {
        return "BCF2FieldEncoder for " + getField() + " with count " + getCountType() + " encoded with "
                + getClass().getSimpleName();
    }

    // ----------------------------------------------------------------------
    //
    // methods to determine the number of encoded elements
    //
    // ----------------------------------------------------------------------

    protected final VCFHeaderLineCount getCountType() {
        return headerLine.getCountType();
    }

    /**
     * @return True if this field has a constant, fixed number of elements (such as 1 for an atomic integer)
     */
    public boolean hasConstantNumElements() {
        return getCountType() == VCFHeaderLineCount.INTEGER;
    }

    /**
     * @return True if the only way to determine how many elements this field contains is by
     * inspecting the actual value directly, such as when the number of elements
     * is a variable length list per site or per genotype, or depends on each sample's GT or LAA.
     */
    public boolean hasValueDeterminedNumElements() {
        return getCountType() == VCFHeaderLineCount.UNBOUNDED || getCountType().variesBySample();
    }

    /**
     * @return True if this field has a non-fixed number of elements that depends only on the properties
     * of the current VariantContext, such as one value per Allele or per genotype configuration.
     */
    public boolean hasContextDeterminedNumElements() {
        return !hasConstantNumElements() && !hasValueDeterminedNumElements();
    }

    /**
     * @return the number of elements, assuming this field has a constant number of elements.
     */
    public int numElements() {
        return headerLine.getCount();
    }

    /**
     * @return the number of elements by looking at the actual value provided
     */
    public int numElements(final Object value) {
        return numElementsFromValue(value);
    }

    /**
     * @return the number of elements, assuming this field has context-determined number of elements.
     */
    public int numElements(final VariantContext vc) {
        return headerLine.getCount(vc);
    }

    /**
     * A convenience access for the number of elements.
     * @param vc
     * @param value
     * @return the number of encoded elements, either from the fixed number
     * it has, from the VC, or from the value itself.
     */
    public final int numElements(final VariantContext vc, final Object value) {
        if (hasConstantNumElements()) return numElements();
        else if (hasContextDeterminedNumElements()) return numElements(vc);
        else return numElements(value);
    }

    /**
     * Given a value, return the number of elements we will encode for it.
     *
     * Assumes the value is encoded as a List
     *
     * @param value
     * @return the number of elements we will encode for {@param value}.
     */
    protected int numElementsFromValue(final Object value) {
        if (value == null) return 0;
        else if (value instanceof List) return ((List) value).size();
        else return 1;
    }

    // ----------------------------------------------------------------------
    //
    // methods to determine the BCF2 type of the encoded values
    //
    // ----------------------------------------------------------------------

    /**
     * Is the BCF2 type of this field static, or does it have to be determine from
     * the actual field value itself?
     * @return true if the field is static
     */
    public final boolean isStaticallyTyped() {
        return !isDynamicallyTyped();
    }

    /**
     * Is the BCF2 type of this field static, or does it have to be determine from
     * the actual field value itself?
     * @return true if the field is not static
     */
    public final boolean isDynamicallyTyped() {
        return staticType == null;
    }

    /**
     * Get the BCF2 type for this field, either from the static type of the
     * field itself or by inspecting the value itself.
     *
     * @return the BCF2 type for this field
     */
    public final BCF2Type getType(final Object value) {
        return isDynamicallyTyped() ? getDynamicType(value) : getStaticType();
    }

    public final BCF2Type getStaticType() {
        return staticType;
    }

    public BCF2Type getDynamicType(final Object value) {
        throw new IllegalStateException("BUG: cannot get dynamic type for statically typed BCF2 field " + getField());
    }

    // ----------------------------------------------------------------------
    //
    // methods to encode values, including the key abstract method
    //
    // ----------------------------------------------------------------------

    /**
     * Key abstract method that should encode a value of the given type into the encoder.
     *
     * Value will be of a type appropriate to the underlying encoder.  If the genotype field is represented as
     * an int[], this will be value, and the encoder needs to handle encoding all of the values in the int[].
     *
     * The argument should be used, not the getType() method in the superclass as an outer loop might have
     * decided a more general type (int16) to use, even through this encoder could have been done with int8.
     *
     * If minValues &gt; 0, then encodeValue must write in at least minValues items from value.  If value is atomic,
     * this means that minValues - 1 MISSING values should be added to the encoder.  If minValues is a collection
     * type (int[]) then minValues - values.length should be added.  This argument is intended to handle padding
     * of values in genotype fields.
     *
     * @param encoder
     * @param value
     * @param type
     * @param minValues
     * @throws IOException
     */
    public abstract void encodeValue(
            final BCF2Encoder encoder, final Object value, final BCF2Type type, final int minValues) throws IOException;

    /**
     * Pad from {@code count} to {@code minValues} with either all MISSING (2.1) or one MISSING then
     * END_OF_VECTOR (2.2), matching htslib's padding rule.
     */
    final void pad(final BCF2Encoder encoder, int count, final int minValues, final BCF2Type type) throws IOException {
        if (useEndOfVector) {
            if (count < minValues) {
                encoder.encodeRawMissingValue(type);
                count++;
            }
            for (; count < minValues; count++) {
                encoder.encodeRawEndOfVector(type);
            }
        } else {
            for (; count < minValues; count++) {
                encoder.encodeRawMissingValue(type);
            }
        }
    }

    // ----------------------------------------------------------------------
    //
    // Subclass to encode Strings
    //
    // ----------------------------------------------------------------------

    public static class StringOrCharacter extends BCF2FieldEncoder {
        public StringOrCharacter(
                final VCFCompoundHeaderLine headerLine,
                final Map<String, Integer> dict,
                final boolean useEndOfVector,
                final boolean percentEncode,
                final boolean htslibStringLists) {
            super(headerLine, dict, BCF2Type.CHAR, useEndOfVector, percentEncode, htslibStringLists);
        }

        @Override
        public void encodeValue(final BCF2Encoder encoder, final Object value, final BCF2Type type, final int minValues)
                throws IOException {
            final String s = javaStringToBCF2String(value);
            // a missing FORMAT string is "." + NUL padding in 2.2, all NUL in 2.1
            if (s.isEmpty() && useEndOfVector) {
                encoder.encodeRawString(".", Math.max(1, minValues));
            } else {
                encoder.encodeRawString(s, Math.max(utf8ByteLength(s), minValues));
            }
        }

        //
        // Regardless of what the header says, BCF2 strings and characters are always encoded
        // as arrays of CHAR type, which has a variable number of elements depending on the
        // exact string being encoded
        //
        @Override
        public boolean hasConstantNumElements() {
            return false;
        }

        @Override
        public boolean hasContextDeterminedNumElements() {
            return false;
        }

        @Override
        public boolean hasValueDeterminedNumElements() {
            return true;
        }

        @Override
        protected int numElementsFromValue(final Object value) {
            if (value == null) {
                return useEndOfVector ? 1 : 0; // "." for 2.2, empty for 2.1
            }
            return utf8ByteLength(javaStringToBCF2String(value));
        }

        /**
         * Recode the incoming object to a String, compacting it into a
         * BCF2 string if the value is a list.
         *
         * @param value a String or List<String> to encode, or null
         * @return a non-null string to encode
         */
        private String javaStringToBCF2String(final Object value) {
            if (value == null) return "";
            if (value instanceof List) {
                final List<String> l =
                        percentEncode ? percentEncodeElements((List<String>) value) : (List<String>) value;
                return BCF2Utils.collapseStringList(l, !htslibStringLists);
            } else if (value.getClass().isArray()) {
                final List<String> l = new ArrayList<String>();
                Collections.addAll(l, (String[]) value);
                final List<String> encoded = percentEncode ? percentEncodeElements(l) : l;
                return BCF2Utils.collapseStringList(encoded, !htslibStringLists);
            } else {
                final String s = (String) value;
                return percentEncode ? VCFPercentEncodedTextTransformer.percentEncode(s) : s;
            }
        }

        /** Percent-encode each element individually so that list-delimiter commas are not escaped. */
        private static List<String> percentEncodeElements(final List<String> elements) {
            List<String> result = elements;
            for (int i = 0; i < elements.size(); i++) {
                final String s = elements.get(i);
                if (s != null) {
                    final String encoded = VCFPercentEncodedTextTransformer.percentEncode(s);
                    if (encoded != s && result == elements) {
                        result = new ArrayList<>(elements);
                    }
                    if (result != elements) {
                        result.set(i, encoded);
                    }
                }
            }
            return result;
        }

        /** The number of bytes the string occupies when encoded as UTF-8, without allocating a byte array. */
        static int utf8ByteLength(final String s) {
            int bytes = 0;
            for (int i = 0; i < s.length(); i++) {
                final char c = s.charAt(i);
                if (c < 0x80) {
                    bytes++;
                } else if (c < 0x800) {
                    bytes += 2;
                } else if (Character.isHighSurrogate(c)
                        && i + 1 < s.length()
                        && Character.isLowSurrogate(s.charAt(i + 1))) {
                    bytes += 4;
                    i++; // skip the low surrogate
                } else {
                    bytes += 3;
                }
            }
            return bytes;
        }
    }

    // ----------------------------------------------------------------------
    //
    // Subclass to encode FLAG
    //
    // ----------------------------------------------------------------------

    public static class Flag extends BCF2FieldEncoder {
        public Flag(
                final VCFCompoundHeaderLine headerLine,
                final Map<String, Integer> dict,
                final boolean useEndOfVector,
                final boolean percentEncode,
                final boolean htslibStringLists) {
            super(headerLine, dict, BCF2Type.INT8, useEndOfVector, percentEncode, htslibStringLists);
            if (!headerLine.isFixedCount() || headerLine.getCount() != 0)
                throw new IllegalStateException("Flag encoder requires a fixed count of 0 for field " + getField());
        }

        @Override
        public int numElements() {
            return 0;
        }

        /** Flags are encoded as BCF_BT_NULL size 0 (byte 0x00), matching htslib and the spec. */
        @Override
        public void encodeValue(final BCF2Encoder encoder, final Object value, final BCF2Type type, final int minValues)
                throws IOException {
            // nothing to write: the type descriptor 0x00 (NULL, size 0) was already written by the caller
        }
    }

    // ----------------------------------------------------------------------
    //
    // Subclass to encode FLOAT
    //
    // ----------------------------------------------------------------------

    public static class Float extends BCF2FieldEncoder {
        final boolean isAtomic;

        public Float(
                final VCFCompoundHeaderLine headerLine,
                final Map<String, Integer> dict,
                final boolean useEndOfVector,
                final boolean percentEncode,
                final boolean htslibStringLists) {
            super(headerLine, dict, BCF2Type.FLOAT, useEndOfVector, percentEncode, htslibStringLists);
            isAtomic = hasConstantNumElements() && numElements() == 1;
        }

        @Override
        public void encodeValue(final BCF2Encoder encoder, final Object value, final BCF2Type type, final int minValues)
                throws IOException {
            int count = 0;
            // TODO -- can be restructured to avoid toList operation
            if (isAtomic) {
                // fast path for fields with 1 fixed float value
                if (value != null) {
                    encoder.encodeRawFloat((Double) value);
                    count++;
                }
            } else {
                // handle generic case
                final List<Double> doubles = BCF2Utils.toList(Double.class, value);
                for (final Double d : doubles) {
                    if (d != null) {
                        encoder.encodeRawFloat(d);
                    } else {
                        encoder.encodeRawMissingValue(type);
                    }
                    count++;
                }
            }
            pad(encoder, count, minValues, type);
        }
    }

    // ----------------------------------------------------------------------
    //
    // Subclass to encode int[]
    //
    // ----------------------------------------------------------------------

    public static class IntArray extends BCF2FieldEncoder {
        public IntArray(
                final VCFCompoundHeaderLine headerLine,
                final Map<String, Integer> dict,
                final boolean useEndOfVector,
                final boolean percentEncode,
                final boolean htslibStringLists) {
            super(headerLine, dict, null, useEndOfVector, percentEncode, htslibStringLists);
        }

        @Override
        protected int numElementsFromValue(final Object value) {
            return value == null ? 0 : ((int[]) value).length;
        }

        @Override
        public BCF2Type getDynamicType(final Object value) {
            return value == null ? BCF2Type.INT8 : BCF2Utils.determineIntegerType((int[]) value);
        }

        @Override
        public void encodeValue(final BCF2Encoder encoder, final Object value, final BCF2Type type, final int minValues)
                throws IOException {
            int count = 0;
            if (value != null) {
                for (final int i : (int[]) value) {
                    encoder.encodeRawInt(i, type);
                    count++;
                }
            }
            pad(encoder, count, minValues, type);
        }
    }

    // ----------------------------------------------------------------------
    //
    // Subclass to encode List<Integer>
    //
    // ----------------------------------------------------------------------

    /**
     * Specialized int encoder for atomic (non-list) integers
     */
    public static class AtomicInt extends BCF2FieldEncoder {
        public AtomicInt(
                final VCFCompoundHeaderLine headerLine,
                final Map<String, Integer> dict,
                final boolean useEndOfVector,
                final boolean percentEncode,
                final boolean htslibStringLists) {
            super(headerLine, dict, null, useEndOfVector, percentEncode, htslibStringLists);
        }

        @Override
        public BCF2Type getDynamicType(final Object value) {
            return value == null ? BCF2Type.INT8 : BCF2Utils.determineIntegerType((Integer) value);
        }

        @Override
        public void encodeValue(final BCF2Encoder encoder, final Object value, final BCF2Type type, final int minValues)
                throws IOException {
            int count = 0;
            if (value != null) {
                encoder.encodeRawInt((Integer) value, type);
                count++;
            }
            pad(encoder, count, minValues, type);
        }
    }

    public static class GenericInts extends BCF2FieldEncoder {
        public GenericInts(
                final VCFCompoundHeaderLine headerLine,
                final Map<String, Integer> dict,
                final boolean useEndOfVector,
                final boolean percentEncode,
                final boolean htslibStringLists) {
            super(headerLine, dict, null, useEndOfVector, percentEncode, htslibStringLists);
        }

        @Override
        public BCF2Type getDynamicType(final Object value) {
            return value == null
                    ? BCF2Type.INT8
                    : BCF2Utils.determineIntegerType(BCF2Utils.toList(Integer.class, value));
        }

        @Override
        public void encodeValue(final BCF2Encoder encoder, final Object value, final BCF2Type type, final int minValues)
                throws IOException {
            int count = 0;
            for (final Integer i : BCF2Utils.toList(Integer.class, value)) {
                if (i != null) {
                    encoder.encodeRawInt(i, type);
                } else {
                    encoder.encodeRawMissingValue(type);
                }
                count++;
            }
            pad(encoder, count, minValues, type);
        }
    }
}
