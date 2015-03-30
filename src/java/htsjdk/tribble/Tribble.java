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

import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.util.ParsingUtils;

import java.io.File;

/**
 * Common, tribble wide constants and static functions
 */
public class Tribble {
    private Tribble() { } // can't be instantiated

    public final static String STANDARD_INDEX_EXTENSION = ".idx";

    /**
     * Return the name of the index file for the provided {@code filename}
     * Does not actually create an index
     * @param filename
     * @return
     */
    public static String indexFile(String filename) {
        return ParsingUtils.appendToPath(filename, STANDARD_INDEX_EXTENSION);
    }

    /**
     * Return the File of the index file for the provided {@code file}
     * Does not actually create an index
     * @param file
     * @return
     */
    public static File indexFile(File file) {
        return IOUtil.getFile(file.getAbsoluteFile() + STANDARD_INDEX_EXTENSION);
    }

}
