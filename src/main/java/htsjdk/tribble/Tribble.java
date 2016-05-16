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

import htsjdk.tribble.util.ParsingUtils;
import htsjdk.tribble.util.TabixUtils;

import java.io.File;

/**
 * Common, tribble wide constants and static functions
 */
public class Tribble {
    private Tribble() { } // can't be instantiated

    public final static String STANDARD_INDEX_EXTENSION = ".idx";

    /**
     * Return the name of the index file for the provided vcf {@code filename}
     * Does not actually create an index
     * @param filename  name of the vcf file
     * @return non-null String representing the index filename
     */
    public static String indexFile(final String filename) {
        return indexFile(filename, STANDARD_INDEX_EXTENSION);
    }

    /**
     * Return the File of the index file for the provided vcf {@code file}
     * Does not actually create an index
     * @param file  the vcf file
     * @return a non-null File representing the index
     */
    public static File indexFile(final File file) {
        return indexFile(file.getAbsoluteFile(), STANDARD_INDEX_EXTENSION);
    }

    /**
     * Return the name of the tabix index file for the provided vcf {@code filename}
     * Does not actually create an index
     * @param filename  name of the vcf file
     * @return non-null String representing the index filename
     */
    public static String tabixIndexFile(final String filename) {
        return indexFile(filename, TabixUtils.STANDARD_INDEX_EXTENSION);
    }

    /**
     * Return the File of the tabix index file for the provided vcf {@code file}
     * Does not actually create an index
     * @param file  the vcf file
     * @return a non-null File representing the index
     */
    public static File tabixIndexFile(final File file) {
        return indexFile(file.getAbsoluteFile(), TabixUtils.STANDARD_INDEX_EXTENSION);
    }

    /**
     * Return the name of the index file for the provided vcf {@code filename} and {@code extension}
     * Does not actually create an index
     * @param filename  name of the vcf file
     * @param extension the extension to use for the index
     * @return non-null String representing the index filename
     */
    private static String indexFile(final String filename, final String extension) {
        return ParsingUtils.appendToPath(filename, extension);
    }

    /**
     * Return the File of the index file for the provided vcf {@code file} and {@code extension}
     * Does not actually create an index
     * @param file  the vcf file
     * @param extension the extension to use for the index
     * @return a non-null File representing the index
     */
    private static File indexFile(final File file, final String extension) {
        return new File(file.getAbsoluteFile() + extension);
    }
}
