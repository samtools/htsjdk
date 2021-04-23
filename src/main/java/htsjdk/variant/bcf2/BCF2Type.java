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

package htsjdk.variant.bcf2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;

/**
 * BCF2 types and associated information
 *
 * @author depristo
 * @since 05/12
 */
public enum BCF2Type {
    // the actual values themselves
    MISSING(0, 0, 0x00) {
        @Override
        public int read(final InputStream in) throws IOException {
            throw new IllegalArgumentException("Cannot read MISSING type");
        }

        @Override
        public void write(final int value, final OutputStream out) throws IOException {
            throw new IllegalArgumentException("Cannot write MISSING type");
        }
    },

    INT8(1, 1, 0xFFFFFF80, 0xFFFFFF81, -120, 127) {
        @Override
        public int read(final InputStream in) throws IOException {
            // This cast to byte then implicit cast back to int is needed so that negative
            // integers are sign extended to their proper 32 bit representation.
            // The integer read from the stream before truncating to byte is an 8 bit integer
            // with the 3 high bytes 0, and the widening conversion performs sign extension,
            // the same applies for the read method of INT16.
            return (byte) in.read();
        }

        @Override
        public void write(final int value, final OutputStream out) throws IOException {
            // Do not need to mask off higher bytes because Java's OutputStream contract is to
            // only write the bottom byte of the passed in int, the same applies to the write
            // methods of the larger int sizes below.
            out.write(value);
        }
    },

    INT16(2, 2, 0xFFFF8000, 0xFFFF8001, -32760, 32767) {
        @Override
        public int read(final InputStream in) throws IOException {
            final int b2 = in.read();
            final int b1 = in.read();
            return (short) ((b1 << 8) | b2);
        }

        @Override
        public void write(final int value, final OutputStream out) throws IOException {
            // TODO -- optimization -- should we put this in a local buffer?
            out.write(value);
            out.write(value >> 8);
        }
    },

    INT32(3, 4, 0x80000000, 0x80000001, -2147483640, 2147483647) {
        @Override
        public int read(final InputStream in) throws IOException {
            final int b4 = in.read();
            final int b3 = in.read();
            final int b2 = in.read();
            final int b1 = in.read();
            return b1 << 24 | b2 << 16 | b3 << 8 | b4;
        }

        @Override
        public void write(final int value, final OutputStream out) throws IOException {
            out.write(value);
            out.write(value >> 8);
            out.write(value >> 16);
            out.write(value >> 24);
        }
    },

    FLOAT(5, 4, 0x7F800001, 0x7F800002, 0, 0) {
        @Override
        public int read(final InputStream in) throws IOException {
            return INT32.read(in);
        }

        @Override
        public void write(final int value, final OutputStream out) throws IOException {
            INT32.write(value, out);
        }
    },

    // CHAR isn't given a MISSING or EOV value in the spec, but for the purposes of
    // padding strings (i.e. variable length vectors of chars), it is treated as if
    // '\0' or NULL is both the MISSING and EOV value of CHAR
    CHAR(7, 1, 0x00000000) {
        @Override
        public int read(final InputStream in) throws IOException {
            return INT8.read(in);
        }

        @Override
        public void write(final int value, final OutputStream out) throws IOException {
            INT8.write(value, out);
        }
    };

    private final int id;
    private final Object missingJavaValue;

    /*
    Note that the values for these fields for INT8 and IN16 differ from those given in the spec
    The values given here are as if they have been sign-extended to 32 bits from their native
    integer width (meaning they have all bits above that width set, as the missing and EOV
    values all have their highest bit set in their native width)

    This is so that that they compare equal to the values returned by the various
    integer types' read methods, which must also sign-extend their return values so
    we can return a uniformly sized 32-bit int
     */
    private final int missingBytes;
    private final int EOVBytes;
    private final int sizeInBytes;

    private final long minValue, maxValue;

    BCF2Type(final int id, final int sizeInBytes, final int missingBytes) {
        this(id, sizeInBytes, missingBytes, 0, 0, 0);
    }

    BCF2Type(final int id, final int sizeInBytes, final int missingBytes, final int EOVBytes, final long minValue, final long maxValue) {
        this.id = id;
        this.sizeInBytes = sizeInBytes;
        this.missingJavaValue = null;
        this.missingBytes = missingBytes;
        this.EOVBytes = EOVBytes;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /**
     * How many bytes are used to represent this type on disk?
     *
     * @return
     */
    public int getSizeInBytes() {
        return sizeInBytes;
    }

    /**
     * The ID according to the BCF2 specification
     *
     * @return
     */
    public int getID() {
        return id;
    }

    /**
     * Can we encode value v in this type, according to its declared range.
     * <p>
     * Only makes sense for integer values
     *
     * @param v
     * @return
     */
    public final boolean withinRange(final long v) {
        return v <= maxValue && v >= minValue;
    }

    /**
     * Return the java object (aka null) that is used to represent a missing value for this
     * type in Java
     *
     * @return
     */
    public Object getMissingJavaValue() {
        return missingJavaValue;
    }

    /**
     * The bytes (encoded as an int) that are used to represent a missing value
     * for this type in BCF2
     *
     * @return
     */
    public int getMissingBytes() {
        return missingBytes;
    }

    /**
     * The bytes (encoded as an int) that are used to represent an end of vector value
     * for this type in BCF2
     *
     * @return
     */
    public int getEOVBytes() {
        return EOVBytes;
    }

    /**
     * An enum set of the types that might represent Integer values
     */
    private final static EnumSet<BCF2Type> INTEGERS = EnumSet.of(INT8, INT16, INT32);

    /**
     * @return true if this BCF2Type corresponds to the magic "MISSING" type (0x00)
     */
    public boolean isMissingType() {
        return this == MISSING;
    }

    public boolean isIntegerType() {
        return INTEGERS.contains(this);
    }

    /**
     * Read a value from in stream of this BCF2 type as an int [32 bit] collection of bits
     * <p>
     * For intX and char values this is just the int / byte value of the underlying data represented as a 32 bit int
     * For a char the result must be converted to a char by (char)(byte)(0x0F &amp; value)
     * For doubles it's necessary to convert subsequently this value to a double via Double.bitsToDouble()
     *
     * @param in
     * @return
     * @throws IOException
     */
    public int read(final InputStream in) throws IOException {
        throw new IllegalArgumentException("Not implemented");
    }

    public void write(final int value, final OutputStream out) throws IOException {
        throw new IllegalArgumentException("Not implemented");
    }

    private enum Special {
        MISSING,
        EOV,
    }

    /**
     * @return a unique End Of Vector object used by the low level decoder
     */
    public static Object EOVValue() {
        return Special.EOV;
    }
}
