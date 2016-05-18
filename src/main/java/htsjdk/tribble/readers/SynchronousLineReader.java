/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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
package htsjdk.tribble.readers;

import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Implementation of {@link LineReader} that reads lines directly from the underlying stream or reader.
 */
public final class SynchronousLineReader implements LineReader{
    private final LongLineBufferedReader longLineBufferedReader;

    public SynchronousLineReader(final InputStream stream){
        this(new InputStreamReader(stream));
    }

    public SynchronousLineReader(final Reader reader){
        this.longLineBufferedReader = new LongLineBufferedReader(reader);
    }

    @Override
    public String readLine() {
        try {
            return longLineBufferedReader.readLine();
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public void close() {
        CloserUtil.close(longLineBufferedReader);
    }
}