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

import htsjdk.samtools.util.ListByteBufferOutputStream;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Type;
import htsjdk.variant.bcf2.BCF2Utils;
import htsjdk.variant.bcf2.BCFVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * See #BCFWriter for documentation on this classes role in encoding BCF2 files
 *
 * @author Mark DePristo
 * @since 06/12
 */
public abstract class BCF2Encoder {
    // TODO -- increase default size?
    public static final int WRITE_BUFFER_INITIAL_SIZE = 16_384;
    protected final ListByteBufferOutputStream encodeStream = new ListByteBufferOutputStream(WRITE_BUFFER_INITIAL_SIZE);

    public static BCF2Encoder getEncoder(final BCFVersion version) {
        switch (version.getMinorVersion()) {
            case 1:
                return new BCF2_1Encoder();
            case 2:
                return new BCF2_2Encoder();
            default:
                throw new TribbleException("BCF2Codec can only process BCF2 files with minor version <= " + 2 + " but this file has minor version " + version.getMinorVersion());
        }
    }


    // --------------------------------------------------------------------------------
    //
    // Functions to return the data being encoded here
    //
    // --------------------------------------------------------------------------------

    /**
     * This allocates a new array and copies the stream's contents over so it
     * should not be used in the actual encoder, but may be useful for testing
     */
    public byte[] getRecordBytes() {
        final byte[] bytes = encodeStream.toByteArray();
        encodeStream.reset();
        return bytes;
    }

    public final int getSize() {
        return encodeStream.size();
    }

    public final void write(final OutputStream out) throws IOException {
        encodeStream.writeTo(out);
        encodeStream.reset();
    }


    // --------------------------------------------------------------------------------
    //
    // Writing typed values (writes out typing byte(s) first)
    //
    // --------------------------------------------------------------------------------

    public final void encodeTypedMissing(final BCF2Type type) throws IOException {
        encodeType(0, type);
    }

    public final void encodeTyped(final Object value, final BCF2Type type) throws IOException {
        if (value == null)
            encodeTypedMissing(type);
        else {
            switch (type) {
                case INT8:
                case INT16:
                case INT32:
                    encodeTypedInt((Integer) value, type);
                    break;
                case FLOAT:
                    encodeTypedFloat((Double) value);
                    break;
                case CHAR:
                    encodeTypedString((String) value);
                    break;
                default:
                    throw new IllegalArgumentException("Illegal type encountered " + type);
            }
        }
    }

    public final void encodeTypedInt(final int v) throws IOException {
        final BCF2Type type = BCF2Utils.determineIntegerType(v);
        encodeTypedInt(v, type);
    }

    public final void encodeTypedInt(final int v, final BCF2Type type) throws IOException {
        encodeType(1, type);
        encodeRawInt(v, type);
    }

    public final void encodeTypedFloat(final double v) throws IOException {
        encodeType(1, BCF2Type.FLOAT);
        encodeRawFloat(v);
    }

    public final void encodeTypedString(final String s) throws IOException {
        encodeTypedString(s.getBytes(StandardCharsets.UTF_8));
    }

    public final void encodeTypedString(final byte[] s) throws IOException {
        encodeType(s.length, BCF2Type.CHAR);
        encodeStream.write(s);
    }

    public final void encodeTypedVecInt(final int[] vs) throws IOException {
        final int size = vs.length;
        final BCF2Type type = BCF2Utils.determineIntegerType(vs);
        encodeType(size, type);
        encodeRawVecInt(vs, size, type);
    }


    public final void encodeTypedVecInt(final int[] vs, final int paddedSize) throws IOException {
        final BCF2Type type = BCF2Utils.determineIntegerType(vs);
        encodeType(paddedSize, type);
        encodeRawVecInt(vs, paddedSize, type);
    }

    // TODO only used in testing, should remove and update tests
    public final void encodeTyped(final List<?> v, final BCF2Type type) throws IOException {
        if (type == BCF2Type.CHAR && !v.isEmpty()) {
            encodeTypedString(compactStrings((List<String>) v));
        } else {
            encodeType(v.size(), type);
            encodeRawValues(v, type);
        }
    }


    // --------------------------------------------------------------------------------
    //
    // Writing raw values (does not write out typing byte(s))
    //
    // --------------------------------------------------------------------------------

    public final <T> void encodeRawValues(final Collection<T> v, final BCF2Type type) throws IOException {
        for (final T v1 : v) {
            encodeRawValue(v1, type);
        }
    }

    public final <T> void encodeRawValue(final T value, final BCF2Type type) throws IOException {
        try {
            if (value == type.getMissingJavaValue())
                encodeRawMissingValue(type);
            else {
                switch (type) {
                    case INT8:
                    case INT16:
                    case INT32:
                        encodeRawBytes((Integer) value, type);
                        break;
                    case FLOAT:
                        encodeRawFloat((Double) value);
                        break;
                    case CHAR:
                        encodeRawChar((Byte) value);
                        break;
                    default:
                        throw new IllegalArgumentException("Illegal type encountered " + type);
                }
            }
        } catch (final ClassCastException e) {
            throw new ClassCastException("BUG: invalid type cast to " + type + " from " + value);
        }
    }

    public final void encodeRawMissingValue(final BCF2Type type) throws IOException {
        encodeRawBytes(type.getMissingBytes(), type);
    }


    // --------------------------------------------------------------------------------
    //
    // Low-level encoders
    //
    // --------------------------------------------------------------------------------

    public final void encodeType(final int size, final BCF2Type type) throws IOException {
        if (size <= BCF2Utils.MAX_INLINE_ELEMENTS) {
            final int typeByte = BCF2Utils.encodeTypeDescriptor(size, type);
            encodeStream.write(typeByte);
        } else {
            final int typeByte = BCF2Utils.encodeTypeDescriptor(BCF2Utils.OVERFLOW_ELEMENT_MARKER, type);
            encodeStream.write(typeByte);
            // write in the overflow size
            encodeTypedInt(size);
        }
    }

    public final void encodeRawBytes(final int v, final BCF2Type type) throws IOException {
        type.write(v, encodeStream);
    }

    public final void encodeRawInt(final int v, final BCF2Type type) throws IOException {
        type.write(v, encodeStream);
    }

    public final void encodeRawFloat(final double v) throws IOException {
        encodeRawBytes(Float.floatToIntBits((float) v), BCF2Type.FLOAT);
    }

    public final void encodeRawChar(final byte c) {
        encodeStream.write(c);
    }

    public final void encodeRawString(final byte[] s, final int paddedSize) {
        encodeStream.write(s);
        final int padding = paddedSize - s.length;
        if (padding > 0) {
            // Pad with zeros, see https://github.com/samtools/hts-specs/issues/232
            encodeStream.write((byte) 0, padding);
        }
    }

    public final void encodeRawVecInt(final int[] vs, final int paddedSize, final BCF2Type type) throws IOException {
        for (final int v : vs) {
            type.write(v, encodeStream);
        }
        encodePaddingValues(paddedSize - vs.length, type);
    }

    public final void encodeRawVecInt(final List<Integer> vs, final BCF2Type type) throws IOException {
        for (final Integer v : vs) {
            if (v == null) {
                type.write(type.getMissingBytes(), encodeStream);
            } else {
                type.write(v, encodeStream);
            }
        }
    }

    public final void encodeRawVecInt(final List<Integer> vs, final int paddedSize, final BCF2Type type) throws IOException {
        encodeRawVecInt(vs, type);
        encodePaddingValues(paddedSize - vs.size(), type);
    }

    public final void encodeRawVecFLoat(final double[] vs, final int paddedSize) throws IOException {
        for (final double v : vs) {
            encodeRawFloat(v);
        }
        encodePaddingValues(paddedSize - vs.length, BCF2Type.FLOAT);
    }

    public final void encodeRawVecFLoat(final List<Double> vs) throws IOException {
        for (final Double v : vs) {
            if (v == null) {
                encodeRawMissingValue(BCF2Type.FLOAT);
            } else {
                encodeRawFloat(v);
            }
        }
    }

    public final void encodeRawVecFLoat(final List<Double> vs, final int paddedSize) throws IOException {
        encodeRawVecFLoat(vs);
        encodePaddingValues(paddedSize - vs.size(), BCF2Type.FLOAT);
    }

    public final void encodePaddingValues(final int size, final BCF2Type type) throws IOException {
        for (int i = 0; i < size; i++) {
            encodePaddingValue(type);
        }
    }

    public abstract void encodePaddingValue(final BCF2Type type) throws IOException;

    // --------------------------------------------------------------------------------
    //
    // Utility Functions
    //
    // --------------------------------------------------------------------------------

    public final byte[] compactStrings(final String[] strings) {
        return compactStrings(Arrays.asList(strings));
    }

    public abstract byte[] compactStrings(final List<String> strings);


    // --------------------------------------------------------------------------------
    //
    // Version specific behavior
    //
    // --------------------------------------------------------------------------------

    public static class BCF2_1Encoder extends BCF2Encoder {

        @Override
        public void encodePaddingValue(final BCF2Type type) throws IOException {
            type.write(type.getMissingBytes(), encodeStream);
        }

        @Override
        public byte[] compactStrings(final List<String> strings) {
            if (strings.isEmpty()) return new byte[0];

            // 1 comma for each string, then add on individual string lengths
            int size = strings.size();
            final byte[][] bytes = new byte[strings.size()][];
            int i = 0;
            for (final String s : strings) {
                final byte[] b = s.getBytes(StandardCharsets.UTF_8);
                size += b.length;
                bytes[i++] = b;
            }
            final ByteBuffer buff = ByteBuffer.allocate(size);
            for (final byte[] bs : bytes) {
                buff.put((byte) ',');
                buff.put(bs);
            }

            return buff.array();
        }
    }

    public static class BCF2_2Encoder extends BCF2Encoder {

        @Override
        public void encodePaddingValue(final BCF2Type type) throws IOException {
            type.write(type.getEOVBytes(), encodeStream);
        }

        @Override
        public byte[] compactStrings(final List<String> strings) {
            if (strings.isEmpty()) return new byte[0];

            // 1 comma for each string except the first, then add on individual string lengths
            int size = strings.size() - 1;
            final byte[][] bytes = new byte[strings.size()][];
            int i = 0;
            for (final String s : strings) {
                final byte[] b = s.getBytes(StandardCharsets.UTF_8);
                size += b.length;
                bytes[i++] = b;
            }
            final ByteBuffer buff = ByteBuffer.allocate(size);
            buff.put(bytes[0]);
            for (int j = 1; j < strings.size(); j++) {
                buff.put((byte) ',');
                buff.put(bytes[j]);
            }

            return buff.array();
        }
    }
}
