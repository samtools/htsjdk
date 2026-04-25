/*
 * The MIT License
 *
 * Copyright (c) 2026 Tim Fennell
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

import com.fulcrumgenomics.jlibdeflate.LibdeflateDecompressor;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * An {@link Inflater} implementation backed by libdeflate via the jlibdeflate library.
 * Provides significantly faster DEFLATE decompression than the JDK's built-in zlib.
 *
 * <p>This class supports the subset of the Inflater API used by BGZF block decompression:
 * {@link #reset()}, {@link #setInput(byte[], int, int)}, and
 * {@link #inflate(byte[], int, int)}.</p>
 *
 * <p>The libdeflate decompressor requires the exact uncompressed size to be known. In BGZF,
 * this is always the case since the uncompressed size is stored in the block footer and
 * passed as the {@code len} parameter to {@link #inflate(byte[], int, int)}.</p>
 */
class LibdeflateInflater extends Inflater {

    private final LibdeflateDecompressor decompressor;
    private final boolean nowrap;

    private byte[] inputBuf;
    private int inputOff;
    private int inputLen;

    LibdeflateInflater(final boolean nowrap) {
        // The super constructor allocates a native zlib stream we won't use.
        // We immediately free it since all decompression goes through libdeflate.
        super(nowrap);
        super.end();

        this.nowrap = nowrap;
        this.decompressor = new LibdeflateDecompressor();
    }

    @Override
    public void setInput(final byte[] input, final int off, final int len) {
        this.inputBuf = input;
        this.inputOff = off;
        this.inputLen = len;
    }

    @Override
    public void setInput(final ByteBuffer input) {
        final int len = input.remaining();
        if (input.hasArray()) {
            setInput(input.array(), input.arrayOffset() + input.position(), len);
            input.position(input.limit());
        } else {
            final byte[] bytes = new byte[len];
            input.get(bytes);
            setInput(bytes, 0, len);
        }
    }

    @Override
    public int inflate(final ByteBuffer output) throws DataFormatException {
        if (!output.hasArray()) {
            throw new UnsupportedOperationException("LibdeflateInflater requires a heap-backed ByteBuffer for output");
        }
        final int n = inflate(output.array(), output.arrayOffset() + output.position(), output.remaining());
        output.position(output.position() + n);
        return n;
    }

    @Override
    public int inflate(final byte[] output, final int off, final int len) throws DataFormatException {
        if (inputBuf == null || inputLen == 0 || len == 0) {
            return 0;
        }

        try {
            if (nowrap) {
                decompressor.deflateDecompress(inputBuf, inputOff, inputLen, output, off, len);
            } else {
                decompressor.zlibDecompress(inputBuf, inputOff, inputLen, output, off, len);
            }
            return len;
        } catch (final Exception e) {
            throw new DataFormatException(e.getMessage());
        }
    }

    @Override
    public void reset() {
        inputBuf = null;
        inputOff = 0;
        inputLen = 0;
    }

    @Override
    public void end() {
        decompressor.close();
    }
}
