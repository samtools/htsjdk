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

import htsjdk.tribble.Feature;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Feature writer class, which uses codecs to write out Tribble file formats.
 *
 * @param <F> feature type.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
class FeatureWriterImpl<F extends Feature> implements FeatureWriter<F> {

    // encoder for the feature and header
    private final FeatureEncoder<F> encoder;

    /*
    * The FeatureWriterImpl writer uses an internal Writer, based by the ByteArrayOutputStream lineBuffer,
    * to temp. buffer the header and per-site output before flushing the per line output in one go.
    * This results in high-performance, proper encoding, and allows us to avoid flushing explicitly
    * the output stream getOutputStream, which allows us to properly compress in gz format without
    * breaking indexing on the fly for uncompressed streams.
    */
    private static final int INITIAL_BUFFER_SIZE = 1024 * 16;
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE);
    /* Wrapping in a {@link BufferedWriter} avoids frequent conversions with individual writes to OutputStreamWriter. */
    private final Writer writer = new BufferedWriter(new OutputStreamWriter(lineBuffer));

    // output stream to write it; keep it here for retrieve in IndexingFeatureWriter
    private final OutputStream outputStream;

    /**
     * Constructor.
     *
     * @param encoder      encoder for the feature.
     * @param outputStream underlying output stream.
     */
    FeatureWriterImpl(final FeatureEncoder<F> encoder,
            final OutputStream outputStream) {
        this.encoder = encoder;
        this.outputStream = outputStream;
    }

    /** Gets the underlying output stream. Used for indexing on the fly. */
    OutputStream getOutputStream() {
        return outputStream;
    }

    /** Writes the header using the encoder. */
    @Override
    public void writeHeader(final Object header) throws IOException {
        encoder.writeHeader(writer, header);
    }

    /** Adds and writes the feature using the encoder. */
    @Override
    public void add(final F feature) throws IOException {
        encoder.write(writer, feature);
        writer.append("\n");
        writeAndResetBuffer();
    }

    /** Closes the writer. */
    @Override
    public void close() throws IOException {
        // write the rest of the buffer
        writeAndResetBuffer();
        // close the writer and the output stream after it
        writer.close();
        outputStream.close();
    }

    /**
     * Actually write the line buffer contents to the destination output stream. After calling this
     * function the line buffer is reset so the contents of the buffer can be reused
     */
    private void writeAndResetBuffer() throws IOException {
        writer.flush();
        outputStream.write(lineBuffer.toByteArray());
        lineBuffer.reset();
    }
}
