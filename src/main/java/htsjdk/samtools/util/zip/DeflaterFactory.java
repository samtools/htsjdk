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
package htsjdk.samtools.util.zip;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import java.util.zip.Deflater;

/**
 * Factory for {@link Deflater} objects used by {@link BlockCompressedOutputStream}.
 * This class may be extended to provide alternative deflaters (e.g., for improved performance).
 */
public class DeflaterFactory {

    public DeflaterFactory() {
        //Note: made explicit constructor to make searching for references easier
    }

    /**
     * Returns a deflater object that will be used when writing BAM files.
     * Subclasses may override to provide their own deflater implementation.
     * @param compressionLevel the compression level (0-9)
     * @param nowrap if true then use GZIP compatible compression
     */
    public Deflater makeDeflater(final int compressionLevel, final boolean nowrap) {
        return new Deflater(compressionLevel, nowrap);
    }
}
