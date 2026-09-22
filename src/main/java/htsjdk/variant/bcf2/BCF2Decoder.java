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

import htsjdk.tribble.TribbleException;
import htsjdk.variant.utils.GeneralUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Decodes the typed values of one BCF record block. A decoder holds the bytes of the block it is reading and nothing
 * else, so each record (and each lazy genotype decode) gets its own. Reading uses a position counter into the byte
 * array directly, avoiding the {@code synchronized} overhead of {@code ByteArrayInputStream} that the JIT cannot
 * always coarsen. A truncated or corrupt record whose fields overrun the block throws
 * {@code ArrayIndexOutOfBoundsException}; callers translate this to {@link TribbleException} at the decode boundary.
 */
public final class BCF2Decoder {
    byte[] recordBytes = null;
    int pos = 0;

    public BCF2Decoder() {
        // nothing to do
    }

    /**
     * Create a new decoder ready to read BCF2 data from the byte[] recordBytes
     *
     * @param recordBytes
     */
    protected BCF2Decoder(final byte[] recordBytes) {
        setRecordBytes(recordBytes);
    }

    // ----------------------------------------------------------------------
    //
    // Routines to load, set, skip blocks of underlying data we are decoding
    //
    // ----------------------------------------------------------------------

    /**
     * Reads the next record from input stream and prepare this decoder to decode values from it
     *
     * @param stream
     */
    public void readNextBlock(final int blockSizeInBytes, final InputStream stream) {
        if (blockSizeInBytes < 0) throw new TribbleException("Invalid block size " + blockSizeInBytes);
        setRecordBytes(readRecordBytes(blockSizeInBytes, stream));
    }

    /**
     * Skips the next record from input stream, invalidating current block data
     *
     * @param stream
     */
    public void skipNextBlock(final int blockSizeInBytes, final InputStream stream) {
        try {
            final int bytesRead = (int) stream.skip(blockSizeInBytes);
            validateReadBytes(bytesRead, 1, blockSizeInBytes);
        } catch (IOException e) {
            throw new TribbleException("I/O error while reading BCF2 file", e);
        }
        this.recordBytes = null;
        this.pos = 0;
    }

    /**
     * Returns the byte[] for the block of data we are currently decoding
     * @return
     */
    public byte[] getRecordBytes() {
        return recordBytes;
    }

    /**
     * The size of the current block in bytes
     *
     * @return
     */
    public int getBlockSize() {
        return recordBytes.length;
    }

    public boolean blockIsFullyDecoded() {
        return pos >= recordBytes.length;
    }

    /**
     * Use the recordBytes[] to read BCF2 records from now on
     *
     * @param recordBytes
     */
    public void setRecordBytes(final byte[] recordBytes) {
        this.recordBytes = recordBytes;
        this.pos = 0;
    }

    /** Reads one byte from the record, advancing the position. */
    private int readRecordByte() {
        return recordBytes[pos++] & 0xFF;
    }

    /** Reads one signed byte from the record, advancing the position. */
    private byte readRecordSignedByte() {
        return recordBytes[pos++];
    }

    // ----------------------------------------------------------------------
    //
    // High-level decoder
    //
    // ----------------------------------------------------------------------

    public final Object decodeTypedValue() throws IOException {
        final byte typeDescriptor = readTypeDescriptor();
        return decodeTypedValue(typeDescriptor);
    }

    public final Object decodeTypedValue(final byte typeDescriptor) throws IOException {
        final int size = decodeNumberOfElements(typeDescriptor);
        return decodeTypedValue(typeDescriptor, size);
    }

    /**
     * Decodes a typed value: null for size 0, a String (or a List of Strings for an htsjdk-style collapsed list)
     * for CHAR, a single Integer or Double for size 1, and otherwise a List of Integers or Doubles. In a list a
     * MISSING value is a null element and END_OF_VECTOR ends the list; nulls at the end of the list are dropped, and
     * a list left empty is returned as null.
     */
    public final Object decodeTypedValue(final byte typeDescriptor, final int size) throws IOException {
        if (size == 0) {
            return null;
        }
        final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
        if (type == BCF2Type.CHAR) {
            return decodeLiteralString(size);
        } else if (size == 1) {
            return decodeSingleValue(type);
        }
        if (!type.isIntegerType() && type != BCF2Type.FLOAT) {
            throw new TribbleException("BCF2 codec doesn't know how to decode type " + type);
        }
        final boolean isFloat = type == BCF2Type.FLOAT;
        final int missing = type.getMissingBytes();
        final ArrayList<Object> values = new ArrayList<Object>(size);
        int lastValue = 0; // one past the last non-missing element
        for (int i = 0; i < size; i++) {
            final int raw = decodeInt(type);
            if (isSentinel(raw, missing)) {
                if (raw != missing) {
                    // END_OF_VECTOR: the rest of the vector is padding
                    skipInts(type, size - i - 1);
                    break;
                }
                values.add(null);
            } else {
                values.add(isFloat ? (Object) rawFloatToFloat(raw) : (Object) raw);
                lastValue = values.size();
            }
        }
        if (lastValue == 0) return null;
        if (lastValue < values.size()) values.subList(lastValue, values.size()).clear();
        return values;
    }

    public final Object decodeSingleValue(final BCF2Type type) throws IOException {
        final int value = decodeInt(type);

        if (isSentinel(value, type.getMissingBytes())) return null;
        switch (type) {
            case INT8:
            case INT16:
            case INT32:
                return value;
            case FLOAT:
                return rawFloatToFloat(value);
            case CHAR:
                return value & 0xFF;
            default:
                throw new TribbleException("BCF2 codec doesn't know how to decode type " + type);
        }
    }

    /**
     * Whether a raw value is one of a type's two sentinels. MISSING and END_OF_VECTOR are adjacent bit patterns
     * (M and M + 1, see {@link BCF2Type}), so one unsigned range test on {@code raw - M} covers both.
     */
    private static boolean isSentinel(final int raw, final int missing) {
        return (raw - missing) >>> 1 == 0;
    }

    // ----------------------------------------------------------------------
    //
    // Decode raw primitive data types (ints, floats, and strings)
    //
    // ----------------------------------------------------------------------

    /**
     * Decodes a CHAR vector as a String, up to its first NUL. An htsjdk-style collapsed list ({@code ,a,b}) is
     * exploded into a List of Strings; any other string, commas included, is returned whole.
     */
    private final Object decodeLiteralString(final int size) {
        assert size > 0;

        int goodLength = 0;
        for (int i = 0; i < size; i++) {
            if (recordBytes[pos + i] == 0) break;
            goodLength++;
        }

        if (goodLength == 0) {
            pos += size;
            return null;
        }
        final String s = new String(recordBytes, pos, goodLength, StandardCharsets.UTF_8);
        pos += size;
        return BCF2Utils.isCollapsedString(s) ? BCF2Utils.explodeStringList(s) : s;
    }

    public final int decodeNumberOfElements(final byte typeDescriptor) throws IOException {
        if (BCF2Utils.sizeIsOverflow(typeDescriptor))
            // -1 ensures we explode immediately with a bad size if the result is missing
            return decodeInt(readTypeDescriptor(), -1);
        else
            // the size is inline, so just decode it
            return BCF2Utils.decodeSize(typeDescriptor);
    }

    /**
     * Decode an int from the stream.  If the value in the stream is missing (or END_OF_VECTOR),
     * returns missingValue.  Requires the typeDescriptor indicate an inline
     * single element event of an integer type
     *
     * @param typeDescriptor
     * @return
     */
    public final int decodeInt(final byte typeDescriptor, final int missingValue) throws IOException {
        final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
        final int i = decodeInt(type);
        // for an integer type the two sentinels are its two lowest values, so one compare finds both
        return i <= type.getVectorEndBytes() ? missingValue : i;
    }

    /**
     * Reads one integer of the given type directly from the record byte array, avoiding the virtual dispatch and
     * {@code synchronized} overhead of {@code ByteArrayInputStream}.
     */
    public final int decodeInt(final BCF2Type type) throws IOException {
        switch (type) {
            case INT8:
            case CHAR:
                return readRecordSignedByte();
            case INT16:
                final int lo16 = readRecordByte();
                final int hi16 = readRecordByte();
                return (short) ((hi16 << 8) | lo16);
            case INT32:
            case FLOAT:
                final int b1 = readRecordByte();
                final int b2 = readRecordByte();
                final int b3 = readRecordByte();
                final int b4 = readRecordByte();
                return (b4 << 24 | b3 << 16 | b2 << 8 | b1);
            default:
                throw new TribbleException("Cannot decode integer from type " + type);
        }
    }

    private void skipInts(final BCF2Type type, final int count) throws IOException {
        for (int i = 0; i < count; i++) decodeInt(type);
    }

    /**
     * Low-level reader for int[]
     *
     * Requires a typeDescriptor so the function knows how many elements to read,
     * and how they are encoded, which must be an integer type.
     *
     * If size == 0 =&gt; result is null
     * If size &gt; 0 =&gt; result depends on the actual values in the stream
     *      -- If the first element read is MISSING or END_OF_VECTOR, result is null
     *      -- An END_OF_VECTOR ends the values: htslib pads a vector shorter than the declared count with it
     *      -- A MISSING followed only by MISSING ends the values: htsjdk pads a shorter vector with it
     *      -- Any other MISSING is an explicit missing value among the values, which an int[] cannot hold, so the
     *         result is null, as it is when the VCF text reader meets {@code 10,.,5}
     *
     * @param maybeDest if not null we'll not allocate space for the vector, but instead use
     *                  the externally allocated array of ints to store values.  If the
     *                  size of this vector is &lt; the actual size of the elements, we'll be
     *                  forced to use freshly allocated arrays.  Also note that padded
     *                  int elements are still forced to do a fresh allocation as well.
     * @return see description
     */
    public final int[] decodeIntArray(final int size, final BCF2Type type, int[] maybeDest) throws IOException {
        if (size == 0) {
            return null;
        }
        if (maybeDest != null && maybeDest.length < size) maybeDest = null;

        // for an integer type the two sentinels are its two lowest values, so one compare finds both
        final int missing = type.getMissingBytes();
        final int endOfVector = type.getVectorEndBytes();
        final int val1 = decodeInt(type);
        if (val1 <= endOfVector) {
            skipInts(type, size - 1);
            return null;
        }
        final int[] ints = maybeDest == null ? new int[size] : maybeDest;
        ints[0] = val1;
        for (int i = 1; i < size; i++) {
            final int v = decodeInt(type);
            if (v <= endOfVector) {
                if (v == missing) {
                    // padding if nothing but MISSING follows, otherwise a missing value among the values
                    for (int j = i + 1; j < size; j++) {
                        if (decodeInt(type) != missing) {
                            skipInts(type, size - j - 1);
                            return null;
                        }
                    }
                } else {
                    skipInts(type, size - i - 1);
                }
                return Arrays.copyOf(ints, i);
            }
            ints[i] = v;
        }
        return ints;
    }

    public final int[] decodeIntArray(final byte typeDescriptor, final int size) throws IOException {
        final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
        return decodeIntArray(size, type, null);
    }

    private double rawFloatToFloat(final int rawFloat) {
        return (double) Float.intBitsToFloat(rawFloat);
    }

    // ----------------------------------------------------------------------
    //
    // Utility functions
    //
    // ----------------------------------------------------------------------

    /**
     * Read the size of the next block from inputStream
     *
     * @param inputStream
     * @return
     */
    public final int readBlockSize(final InputStream inputStream) throws IOException {
        return BCF2Type.INT32.read(inputStream);
    }

    /**
     * Read all bytes for a BCF record block into a byte[], and return it
     *
     * Is smart about reading from the stream multiple times to fill the buffer, if necessary
     *
     * @param blockSizeInBytes number of bytes to read
     * @param inputStream the stream to read from
     * @return a non-null byte[] containing exactly blockSizeInBytes bytes from the inputStream
     */
    private static byte[] readRecordBytes(final int blockSizeInBytes, final InputStream inputStream) {
        assert blockSizeInBytes >= 0;

        final byte[] record = new byte[blockSizeInBytes];
        try {
            int bytesRead = 0;
            int nReadAttempts = 0;

            while (bytesRead < blockSizeInBytes) {
                nReadAttempts++;
                final int read1 = inputStream.read(record, bytesRead, blockSizeInBytes - bytesRead);
                if (read1 == -1) validateReadBytes(bytesRead, nReadAttempts, blockSizeInBytes);
                else bytesRead += read1;
            }

            if (GeneralUtils.DEBUG_MODE_ENABLED && nReadAttempts > 1) {
                System.err.println(
                        "Required multiple read attempts to actually get the entire BCF2 block, unexpected behavior");
            }

            validateReadBytes(bytesRead, nReadAttempts, blockSizeInBytes);
        } catch (IOException e) {
            throw new TribbleException("I/O error while reading BCF2 file", e);
        }

        return record;
    }

    /**
     * Make sure we read the right number of bytes, or throw an error
     *
     * @param actuallyRead
     * @param nReadAttempts
     * @param expected
     */
    private static void validateReadBytes(final int actuallyRead, final int nReadAttempts, final int expected) {
        assert expected >= 0;

        if (actuallyRead < expected) {
            throw new TribbleException(String.format(
                    "Failed to read next complete record: expected %d bytes but read only %d after %d iterations",
                    expected, actuallyRead, nReadAttempts));
        }
    }

    public final byte readTypeDescriptor() throws IOException {
        return readRecordSignedByte();
    }
}
