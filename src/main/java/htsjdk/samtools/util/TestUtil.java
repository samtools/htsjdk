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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestUtil {

    public static final int RANDOM_SEED = 42;

    /**
     * Base url where all test files for http tests are found
     */
    public static final String BASE_URL_FOR_HTTP_TESTS = "https://personal.broadinstitute.org/picard/testdata/";

    /**
     * Creates a new temporary directory that will be deleted on JVM exit.
     *
     * @param prefix the prefix for the temporary directory name
     * @param suffix the suffix appended to the prefix for the temporary directory name
     * @return the {@link Path} of the newly created temporary directory
     */
    public static Path getTempDirectoryAsPath(final String prefix, final String suffix) {
        try {
            final Path tempDirectory = Files.createTempDirectory(prefix + suffix);
            tempDirectory.toFile().deleteOnExit();
            return tempDirectory;
        } catch (IOException e) {
            throw new SAMException("Failed to create temporary directory.", e);
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
    public static <T extends Serializable> T serializeAndDeserialize(T input)
            throws IOException, ClassNotFoundException {
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

    /**
     * Little test utility to help tests that create multiple levels of subdirectories
     * clean up after themselves.
     *
     * @param directory The directory to be deleted (along with its subdirectories)
     */
    public static void recursiveDelete(final Path directory) {
        try {
            IOUtil.recursiveDelete(directory);
        } catch (RuntimeIOException e) {
            // bury exception
        }
    }
}
