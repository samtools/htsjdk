/*
 * The MIT License
 *
 * Copyright (c) 2026 Fulcrum Genomics
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

import com.fulcrumgenomics.jlibdeflate.LibdeflateCompressor;

import java.util.zip.Deflater;

/**
 * A {@link Deflater} implementation backed by libdeflate via the jlibdeflate library.
 * Provides significantly faster DEFLATE compression than the JDK's built-in zlib.
 *
 * <p>This class supports the subset of the Deflater API used by BGZF block compression:
 * {@link #reset()}, {@link #setInput(byte[], int, int)}, {@link #finish()},
 * {@link #deflate(byte[], int, int)}, and {@link #finished()}.</p>
 */
class LibdeflateDeflater extends Deflater {

    private final LibdeflateCompressor compressor;
    private final boolean nowrap;

    private byte[] inputBuf;
    private int inputOff;
    private int inputLen;
    private boolean finishing;
    private boolean done;

    /**
     * Creates a new LibdeflateDeflater at the specified compression level.
     *
     * @param level   compression level (0-12 for libdeflate, 0-9 for standard compatibility)
     * @param nowrap  if true, produce raw DEFLATE (no zlib/gzip header); if false, produce zlib format
     */
    LibdeflateDeflater(final int level, final boolean nowrap) {
        // The super constructor allocates a native zlib stream we won't use.
        // We immediately free it since all compression goes through libdeflate.
        super(level, nowrap);
        super.end();

        this.nowrap = nowrap;
        this.compressor = new LibdeflateCompressor(level);
    }

    @Override
    public void setInput(final byte[] input, final int off, final int len) {
        this.inputBuf = input;
        this.inputOff = off;
        this.inputLen = len;
        this.done = false;
    }

    @Override
    public void finish() {
        this.finishing = true;
    }

    @Override
    public int deflate(final byte[] output, final int off, final int len) {
        if (inputBuf == null || inputLen == 0) {
            done = true;
            return 0;
        }

        final int compressed = nowrap
                ? compressor.deflateCompress(inputBuf, inputOff, inputLen, output, off, len)
                : compressor.zlibCompress(inputBuf, inputOff, inputLen, output, off, len);
        if (compressed == -1) {
            // Output buffer too small — caller will handle this (e.g. fall back to no-compression)
            done = false;
            return len; // fill the buffer to signal it didn't fit; finished() returns false
        }

        done = true;
        return compressed;
    }

    @Override
    public boolean finished() {
        return done;
    }

    @Override
    public void reset() {
        inputBuf  = null;
        inputOff  = 0;
        inputLen  = 0;
        finishing = false;
        done      = false;
    }

    @Override
    public void end() {
        compressor.close();
    }
}
