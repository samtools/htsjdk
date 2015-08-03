/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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
package htsjdk.samtools.util;

import htsjdk.samtools.SAMException;

import java.io.*;

public class TestUtil {

    public static File getTempDirectory(final String prefix, final String suffix) {
        final File tempDirectory;
        try {
            tempDirectory = File.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw new SAMException("Failed to create temporary file.", e);
        }
        if (!tempDirectory.delete())
            throw new SAMException("Failed to delete file: " + tempDirectory);
        if (!tempDirectory.mkdir())
            throw new SAMException("Failed to make directory: " + tempDirectory);
        tempDirectory.deleteOnExit();
        return tempDirectory;
    }

    /**
     * @deprecated Use properly spelled method.
     */
    public static File getTempDirecory(final String prefix, final String suffix) {
        return getTempDirectory(prefix, suffix);
    }

        /**
         * Little test utility to help tests that create multiple levels of subdirectories
         * clean up after themselves.
         *
         * @param directory The directory to be deleted (along with its subdirectories)
         */
    public static void recursiveDelete(final File directory) {
        for (final File f : directory.listFiles()) {
            if (f.isDirectory()) {
                recursiveDelete(f);
            }
            f.delete();
        }
    }

    /**
     * Serialize and Deserialize an object
     * Useful for testing if serialization is correctly handled for a class.
     * @param input an object to serialize and then deserialize
     * @param <T> any Serializable type
     * @return a copy of the initial object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static <T extends Serializable> T serializeAndDeserialize(T input) throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(byteArrayStream);

        out.writeObject(input);
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteArrayStream.toByteArray()));

        @SuppressWarnings("unchecked")
        final T result = (T) in.readObject();

        out.close();
        in.close();
        return result;
    }
}
