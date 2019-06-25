/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.tribble;

import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.util.ParsingUtils;
import htsjdk.tribble.util.TabixUtils;

import java.io.File;
import java.nio.file.Path;

/**
 * Common, tribble wide constants and static functions
 */
public class Tribble {
    private Tribble() { } // can't be instantiated

    /**
     * @deprecated since June 2019 Use {@link FileExtensions#TRIBBLE_INDEX} instead.
     */
    @Deprecated
    public final static String STANDARD_INDEX_EXTENSION = FileExtensions.TRIBBLE_INDEX;

    /**
     * Return the name of the index file for the provided {@code filename}
     * Does not actually create an index
     * @param filename  name of the file
     * @return non-null String representing the index filename
     */
    public static String indexFile(final String filename) {
        return indexFile(filename, FileExtensions.TRIBBLE_INDEX);
    }

    /**
     * Return the File of the index file for the provided {@code file}
     * Does not actually create an index
     * @param file  the file
     * @return a non-null File representing the index
     */
    public static File indexFile(final File file) {
        return indexFile(file.getAbsoluteFile(), FileExtensions.TRIBBLE_INDEX);
    }

    /**
     * Return the name of the index file for the provided {@code path}
     * Does not actually create an index
     * @param path the path
     * @return Path representing the index filename
     */
    public static Path indexPath(final Path path) {
        return path.getFileSystem().getPath(indexFile(path.toAbsolutePath().toString()));
    }

    /**
     * Return the name of the tabix index file for the provided {@code filename}
     * Does not actually create an index
     * @param filename  name of the file
     * @return non-null String representing the index filename
     */
    public static String tabixIndexFile(final String filename) {
        return indexFile(filename, FileExtensions.TABIX_INDEX);
    }

    /**
     * Return the File of the tabix index file for the provided {@code file}
     * Does not actually create an index
     * @param file  the file
     * @return a non-null File representing the index
     */
    public static File tabixIndexFile(final File file) {
        return indexFile(file.getAbsoluteFile(), FileExtensions.TABIX_INDEX);
    }

    /**
     * Return the name of the tabix index file for the provided {@code path}
     * Does not actually create an index
     * @param path the path
     * @return Path representing the index filename
     */
    public static Path tabixIndexPath(final Path path) {
        return path.getFileSystem().getPath(tabixIndexFile(path.toAbsolutePath().toString()));
    }

    /**
     * Return the name of the index file for the provided {@code filename} and {@code extension}
     * Does not actually create an index
     * @param filename  name of the file
     * @param extension the extension to use for the index
     * @return non-null String representing the index filename
     */
    private static String indexFile(final String filename, final String extension) {
        return ParsingUtils.appendToPath(filename, extension);
    }

    /**
     * Return the File of the index file for the provided {@code file} and {@code extension}
     * Does not actually create an index
     * @param file  the file
     * @param extension the extension to use for the index
     * @return a non-null File representing the index
     */
    private static File indexFile(final File file, final String extension) {
        return new File(file.getAbsoluteFile() + extension);
    }
}
