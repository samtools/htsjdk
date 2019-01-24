/*
 * The MIT License
 *
 * Copyright (c) 2019 The Broad Institute
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

/**
 * A set of constants defined in the sam-spec (https://github.com/samtools/hts-specs) that need
 * to be referenced in code.
 */
 public final class SamConstants {

    //No need to instantiate this class since all the constants should be static
    private SamConstants(){};

    /**
     * The recommended separator to use when specifying multiple barcodes together in the same tag.
     */
    public static final String BARCODE_SEQUENCE_DELIMITER = "-";

    /**
     * The recommend separator to use when specifying multiple barcode quality scores together in the same tag.
     */
    public static final String BARCODE_QUALITY_DELIMITER = " ";
}
