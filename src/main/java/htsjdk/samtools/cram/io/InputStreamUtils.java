/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.io;

import htsjdk.samtools.util.IOUtil;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Convenience methods to read from {@link java.io.InputStream}.
 */
public class InputStreamUtils {

    /**
     * Read the {@link InputStream} until the end into a new byte array.
     *
     * @param input the input stream to read
     * @return a new byte array containing data from the input stream
     */
    public static byte[] readFully(final InputStream input) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        IOUtil.copyStream(input, output);
        return output.toByteArray();
    }

    /**
     * Read the specified number of bytes from the {@link InputStream} into a new byte array. The length of the array is less or equal to
     * length.
     *
     * @param inputStream  the input stream to read from
     * @param length the number of bytes to read
     * @return a new byte array containing data from the input stream
     * @throws IOException  as per java IO contract
     * @throws EOFException if there is less than length bytes in the stream
     */
    public static byte[] readFully(final InputStream inputStream, final int length) throws IOException {
        final byte[] b = new byte[length];
        readFully(inputStream, b, 0, length);
        return b;
    }

    /**
     * Read the specified number of bytes from the {@link InputStream} into the byte array starting from the specified position. The length
     * of the array is less or equal to length.
     *
     * @param inputStream  the input stream to read from
     * @param b   the byte array to read into
     * @param off offset in the byte array
     * @param length the number of bytes to read
     * @throws IOException  as per java IO contract
     * @throws EOFException if there is less than length bytes in the stream
     */
    public static void readFully(final InputStream inputStream, final byte[] b, final int off, final int length) throws IOException {
        if (length < 0) throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < length) {
            final int count = inputStream.read(b, off + n, length - n);
            if (count < 0) throw new EOFException();
            n += count;
        }
    }
}
