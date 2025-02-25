/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.tribble.writer;

import htsjdk.samtools.util.AbstractAsyncWriter;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.Feature;

import java.io.IOException;

/**
 * Asynchronous wrapper for {@link FeatureWriter}.
 *
 * @param <F> feature type.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
final class AsyncFeatureWriter<F extends Feature> extends AbstractAsyncWriter<F> implements FeatureWriter<F> {

    // the underlying writer
    private final FeatureWriter<F> underlyingWriter;

    /**
     * Wraps a feature writer with the {@link #DEFAULT_QUEUE_SIZE}.
     */
    AsyncFeatureWriter(final FeatureWriter<F> writer) {
        this(writer, DEFAULT_QUEUE_SIZE);
    }

    /**
     * Wraps a feature writer with a custom queue size.
     */
    AsyncFeatureWriter(final FeatureWriter<F> writer, final int queueSize) {
        super(queueSize);
        this.underlyingWriter = writer;
    }

    /**
     * Writes the header in an synchronous way.
     *
     * Note: it is unsafe to write a header if a feature was already added.
     */
    @Override
    public void writeHeader(final Object header) throws IOException {
        this.underlyingWriter.writeHeader(header);
    }

    /** Adds the feature in an asynchronous way. */
    @Override
    public void add(final F feature) throws IOException {
        write(feature);
    }

    @Override
    protected String getThreadNamePrefix() {
        return "FeatureWriterThread-";
    }

    /** Adds the feature to the underlying writer. */
    @Override
    protected void synchronouslyWrite(F item) {
        try {
            underlyingWriter.add(item);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /** Closes the underlying writer. */
    @Override
    protected void synchronouslyClose() {
        try {
            underlyingWriter.close();
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
