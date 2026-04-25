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

import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.Log;
import java.util.zip.Deflater;

/**
 * Factory for {@link Deflater} objects used by {@link BlockCompressedOutputStream}.
 * This class may be extended to provide alternative deflaters (e.g., for improved performance).
 *
 * <p>By default, if {@link Defaults#USE_LIBDEFLATE} is true, this factory will attempt to
 * create a {@link LibdeflateDeflater} backed by the libdeflate native library.  If the native
 * library is not available, it falls back to the JDK {@link Deflater}.</p>
 */
public class DeflaterFactory {
    private static final Log log = Log.getInstance(DeflaterFactory.class);

    /** Cached result of whether libdeflate is available; null means not yet tested. */
    private static volatile Boolean libdeflateAvailable;

    public DeflaterFactory() {
        // Note: made explicit constructor to make searching for references easier
    }

    /**
     * Returns a deflater object that will be used when writing BAM files.
     * Subclasses may override to provide their own deflater implementation.
     * @param compressionLevel the compression level (0-9)
     * @param gzipCompatible if true then use GZIP compatible compression
     */
    public Deflater makeDeflater(final int compressionLevel, final boolean gzipCompatible) {
        if (Defaults.USE_LIBDEFLATE && isLibdeflateAvailable()) {
            return new LibdeflateDeflater(compressionLevel, gzipCompatible);
        }
        return new Deflater(compressionLevel, gzipCompatible);
    }

    /** Returns true if the libdeflate native library can be loaded. */
    static boolean isLibdeflateAvailable() {
        if (libdeflateAvailable == null) {
            synchronized (DeflaterFactory.class) {
                if (libdeflateAvailable == null) {
                    libdeflateAvailable = testLibdeflate();
                }
            }
        }
        return libdeflateAvailable;
    }

    private static boolean testLibdeflate() {
        try {
            final LibdeflateDeflater deflater = new LibdeflateDeflater(1, true);
            try {
                deflater.setInput(new byte[] {0}, 0, 1);
                deflater.finish();
                deflater.deflate(new byte[16], 0, 16);
            } finally {
                deflater.end();
            }
            log.info("libdeflate is available; using libdeflate for DEFLATE compression.");
            return true;
        } catch (final Throwable t) {
            log.info(t, "libdeflate is not available; falling back to JDK deflater.");
            return false;
        }
    }
}
