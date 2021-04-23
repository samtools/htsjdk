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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BCF2Decoder {
    protected byte[] recordBytes = null;
    protected ByteArrayInputStream recordStream = null;

    private BCF2Decoder() {
        // nothing to do
    }

    public static BCF2Decoder getDecoder(final BCFVersion version) {
        switch (version.getMinorVersion()) {
            case 1:
                return new BCF2Decoder.BCF2_1Decoder();
            case 2:
                return new BCF2Decoder.BCF2_2Decoder();
            default:
                throw new TribbleException("BCF2Codec can only process BCF2 files with minor version <= " + BCF2Codec.ALLOWED_MINOR_VERSION + " but this file has minor version " + version.getMinorVersion());
        }
    }

    public static BCF2Decoder getDecoder(final BCFVersion version, final byte[] recordBytes) {
        final BCF2Decoder decoder = BCF2Decoder.getDecoder(version);
        decoder.setRecordBytes(recordBytes);
        return decoder;
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
        } catch (final IOException e) {
            throw new TribbleException("I/O error while reading BCF2 file", e);
        }
        this.recordBytes = null;
        this.recordStream = null;
    }

    /**
     * Returns the byte[] for the block of data we are currently decoding
     *
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
        return recordStream.available() == 0;
    }

    /**
     * Use the recordBytes[] to read BCF2 records from now on
     *
     * @param recordBytes
     */
    public void setRecordBytes(final byte[] recordBytes) {
        this.recordBytes = recordBytes;
        this.recordStream = new ByteArrayInputStream(recordBytes);
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

    public final Object decodeTypedValue(final byte typeDescriptor, final int size) throws IOException {
        if (size == 0) {
            // missing value => null in java
            return null;
        } else {
            final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
            if (type == BCF2Type.CHAR) { // special case string decoding for efficiency
                final List<String> strings = decodeExplodedStrings(size);
                if (strings.isEmpty()) {
                    return null;
                } else if (strings.size() == 1) {
                    return strings.get(0);
                } else {
                    return strings;
                }
            } else if (size == 1) {
                final Object o = decodeSingleValue(type);
                return o == BCF2Type.EOVValue() ? null : o;
            } else {
                final ArrayList<Object> ints = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    final Object val = decodeSingleValue(type);
                    if (val == BCF2Type.EOVValue()) continue;
                    ints.add(val);
                }
                return ints.isEmpty() ? null : ints;
            }
        }
    }

    public final Object decodeSingleValue(final BCF2Type type) throws IOException {
        final int value = decodeInt(type);

        if (value == type.getMissingBytes()) {
            return null;
        } else if (value == type.getEOVBytes()) {
            return BCF2Type.EOVValue();
        } else {
            switch (type) {
                case INT8:
                case INT16:
                case INT32:
                    return value;
                case FLOAT:
                    return rawFloatToFloat(value);
                case CHAR:
                    return value & 0xFF; // TODO -- I cannot imagine why we'd get here, as string needs to be special cased
                default:
                    throw new TribbleException("BCF2 codec doesn't know how to decode type " + type);
            }
        }
    }

    // ----------------------------------------------------------------------
    //
    // Decode raw primitive data types (ints, floats, and strings)
    //
    // ----------------------------------------------------------------------

    public final int decodeNumberOfElements(final byte typeDescriptor) throws IOException {
        if (BCF2Utils.sizeIsOverflow(typeDescriptor))
            // -1 ensures we explode immediately with a bad size if the result is missing
            return decodeInt(readTypeDescriptor(), -1);
        else
            // the size is inline, so just decode it
            return BCF2Utils.decodeSize(typeDescriptor);
    }

    /**
     * Decode an int from the stream.  If the value in the stream is missing,
     * returns missingValue.  Requires the typeDescriptor indicate an inline
     * single element event
     *
     * @param typeDescriptor
     * @return
     */
    public final int decodeInt(final byte typeDescriptor, final int missingValue) throws IOException {
        final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
        final int i = decodeInt(type);
        return i == type.getMissingBytes() ? missingValue : i;
    }

    public final int decodeInt(final BCF2Type type) throws IOException {
        return type.read(recordStream);
    }

    /**
     * Low-level reader for int[]
     * <p>
     * Requires a typeDescriptor so the function knows how many elements to read,
     * and how they are encoded.
     * <p>
     * Note that this method is only suitable for reading arrays which are known
     * to not contain any internal MISSING values (e.g. filter or GT,
     * in the case of GT in BCF 2.1, the vector may be MISSING padded if the
     * sample ploidy is less than the maximum, but these missing values are
     * not considered to be part of the array, and will not be returned).
     * Parts of the decoder that require missing values to be preserved should
     * use decodeTyped
     * <p>
     * If size == 0 =&gt; result is null
     * If size &gt; 0 =&gt; result depends on the actual values in the stream
     * -- If the first element read is MISSING, result is null (all values are missing)
     * -- Else result = int[N] where N is the first N non-missing values decoded
     *
     * @param maybeDest if not null we'll not allocate space for the vector, but instead use
     *                  the externally allocated array of ints to store values.  If the
     *                  size of this vector is &lt; the actual size of the elements, we'll be
     *                  forced to use freshly allocated arrays.  Also note that padded
     *                  int elements are still forced to do a fresh allocation as well.
     * @return see description
     */
    public int[] decodeIntArray(final int size, final BCF2Type type, int[] maybeDest) throws IOException {
        if (size == 0) {
            return null;
        } else {
            if (maybeDest != null && maybeDest.length < size)
                maybeDest = null; // by nulling this out we ensure that we do fresh allocations as maybeDest is too small

            final int val1 = decodeInt(type);
            if (val1 == getPaddingValue(type)) {
                // Fast path for first element being padding, meaning the whole array is empty
                final int bytesToDrop = type.getSizeInBytes() * (size - 1);
                // Skip the rest of the padding values
                recordStream.skip(bytesToDrop);
                return null;
            } else {
                // we know we will have at least 1 element, so making the int[] is worth it
                final int[] ints = maybeDest == null ? new int[size] : maybeDest;
                ints[0] = val1;
                for (int i = 1; i < size; i++) {
                    ints[i] = decodeInt(type);
                    if (ints[i] == getPaddingValue(type)) {
                        final int bytesToDrop = type.getSizeInBytes() * (size - (i + 1));
                        // Skip the rest of the padding values
                        recordStream.skip(bytesToDrop);
                        // deal with auto-pruning by returning an int[] containing
                        // only the non-padding values.  We do this by copying the first
                        // i elements, as i itself is missing
                        return Arrays.copyOf(ints, i);
                    }
                }
                return ints; // all of the elements were non-padding
            }
        }
    }

    public byte[] decodeRawBytes(final int size) throws IOException {
        final byte[] bytes = new byte[size];
        recordStream.read(bytes);
        return bytes;
    }

    /**
     * Decode a single ASCII encoded string which may be padded with NULL bytes.
     * Multiple strings which were encoded as a single comma separated string are
     * returned unexploded.
     * <p>
     * Reads directly from underlying byte buffer to avoid unnecessary array copies.
     *
     * @param size
     * @return
     */
    public String decodeUnexplodedString(final int size) {
        // Get our current position in the buffer so we can index directly into it
        final int currentBufferPosition = recordBytes.length - recordStream.available();

        // Jump over all bytes, including NULL padding
        recordStream.skip(size);

        // Scan for first NULL padding byte
        int realLength = 0;
        for (; realLength < size; realLength++)
            if (recordBytes[currentBufferPosition + realLength] == '\0') break;

        return new String(recordBytes, currentBufferPosition, realLength);
    }

    public String decodeUnexplodedString() throws IOException {
        final byte typeDescriptor = readTypeDescriptor();
        final int size = decodeNumberOfElements(typeDescriptor);

        return decodeUnexplodedString(size);
    }

    /**
     * Decode a list of ASCII encoded strings.
     * Multiple strings which were encoded as a single comma separated string are
     * exploded. If only a single string was encoded with no commas, returns a
     * list of length 1.
     * <p>
     * Reads directly from underlying byte buffer to avoid unnecessary array copies.
     *
     * @param size
     * @return
     */
    public List<String> decodeExplodedStrings(final int size) {
        // Get our current position in the buffer so we can index directly into it
        final int currentBufferPosition = recordBytes.length - recordStream.available();

        // Jump over all bytes
        recordStream.skip(size);

        if (size == 0 || recordBytes[currentBufferPosition] == '\0') return Collections.emptyList();

        int numStrings = 1;
        // Start at offset 1 to avoid counting optional leading comma
        // Real length may be shorter than provided one because of NULL padding
        int realLength = 1;
        for (; realLength < size; realLength++) {
            final byte currentByte = recordBytes[currentBufferPosition + realLength];
            if (currentByte == ',') numStrings++;
            else if (currentByte == '\0') break;
        }

        final List<String> strings = new ArrayList<>(numStrings);
        int currentStringStart = recordBytes[currentBufferPosition] == ',' ? 1 : 0;
        for (int i = 1; i < realLength; i++) {
            if (recordBytes[currentBufferPosition + i] == ',') {
                strings.add(new String(recordBytes, currentBufferPosition + currentStringStart, i - currentStringStart));
                currentStringStart = i + 1;
            }
        }
        // Add final string
        strings.add(new String(recordBytes, currentBufferPosition + currentStringStart, realLength - currentStringStart));

        return strings;
    }

    public final int[] decodeIntArray(final byte typeDescriptor, final int size) throws IOException {
        final BCF2Type type = BCF2Utils.decodeType(typeDescriptor);
        return decodeIntArray(size, type, null);
    }

    private double rawFloatToFloat(final int rawFloat) {
        return Float.intBitsToFloat(rawFloat);
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
     * <p>
     * Is smart about reading from the stream multiple times to fill the buffer, if necessary
     *
     * @param blockSizeInBytes number of bytes to read
     * @param inputStream      the stream to read from
     * @return a non-null byte[] containing exactly blockSizeInBytes bytes from the inputStream
     */
    private static byte[] readRecordBytes(final int blockSizeInBytes, final InputStream inputStream) {
        assert blockSizeInBytes >= 0;

        final byte[] record = new byte[blockSizeInBytes];
        try {
            int bytesRead = 0;
            final int nReadAttempts = 0; // keep track of how many times we've read

            // because we might not read enough bytes from the file in a single go, do it in a loop until we get EOF
            while (bytesRead < blockSizeInBytes) {
                final int read1 = inputStream.read(record, bytesRead, blockSizeInBytes - bytesRead);
                if (read1 == -1)
                    validateReadBytes(bytesRead, nReadAttempts, blockSizeInBytes);
                else
                    bytesRead += read1;
            }

            if (GeneralUtils.DEBUG_MODE_ENABLED && nReadAttempts > 1) { // TODO -- remove me
                System.err.println("Required multiple read attempts to actually get the entire BCF2 block, unexpected behavior");
            }

            validateReadBytes(bytesRead, nReadAttempts, blockSizeInBytes);
        } catch (final IOException e) {
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
            throw new TribbleException(
                String.format("Failed to read next complete record: expected %d bytes but read only %d after %d iterations",
                    expected, actuallyRead, nReadAttempts));
        }
    }

    public final byte readTypeDescriptor() throws IOException {
        return (byte) recordStream.read();
    }


    // ----------------------------------------------------------------------
    //
    // Version specific behavior
    //
    // ----------------------------------------------------------------------


    public abstract int getPaddingValue(final BCF2Type type);

    public static class BCF2_1Decoder extends BCF2Decoder {

        @Override
        public int getPaddingValue(final BCF2Type type) {
            return type.getMissingBytes();
        }
    }

    public static class BCF2_2Decoder extends BCF2Decoder {

        @Override
        public int getPaddingValue(final BCF2Type type) {
            return type.getEOVBytes();
        }
    }
}
