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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TestUtil {

    public static final int RANDOM_SEED = 42;

    private TestUtil(){};

    /**
     * Base url where all test files for http tests are found
     */
    public static final String BASE_URL_FOR_HTTP_TESTS = "https://personal.broadinstitute.org/picard/testdata/";

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
     * @deprecated Use properly spelled method. {@link #getTempDirectory}
     */
    @Deprecated
    public static File getTempDirecory(final String prefix, final String suffix) {
        return getTempDirectory(prefix, suffix);
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

    /**
     * This method creates a temporary VCF or Bam file and its appropriately named index file, and will delete them on exit.
     *
     * @param prefix - The prefix string to be used in generating the file's name; must be at least three characters long
     * @param suffix - The suffix string to be used in generating the file's name; may be null, in which case the suffix ".tmp" will be used
     * @return A File object referencing the newly created temporary file
     * @throws IOException - if a file could not be created.
     */
    public static File createTemporaryIndexedFile(final String prefix, final String suffix) throws IOException {
        final File out = File.createTempFile(prefix, suffix);
        out.deleteOnExit();
        String indexFileExtension = null;
        if (suffix.endsWith(FileExtensions.COMPRESSED_VCF)) {
            indexFileExtension = FileExtensions.TABIX_INDEX;
        } else if (suffix.endsWith(FileExtensions.VCF)) {
            indexFileExtension = FileExtensions.VCF_INDEX;
        } else if (suffix.endsWith(FileExtensions.BAM)) {
            indexFileExtension = FileExtensions.BAM_INDEX;
        } else if (suffix.endsWith(FileExtensions.CRAM)) {
            indexFileExtension = FileExtensions.CRAM_INDEX;
        }

        if (indexFileExtension != null) {
            final File indexOut = new File(out.getAbsolutePath() + indexFileExtension);
            indexOut.deleteOnExit();
        }
        return out;
    }

    /**
     * Little test utility to help tests that create multiple levels of subdirectories
     * clean up after themselves.
     *
     * @param directory The directory to be deleted (along with its subdirectories)
     * @deprecated Since 3/19, prefer {@link IOUtil#recursiveDelete(Path)}
     */
    @Deprecated
    public static void recursiveDelete(final File directory) {
        try {
            IOUtil.recursiveDelete(directory.toPath());
        } catch (RuntimeIOException e) {
            // bury exception
        }
    }
}
