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

import java.io.Closeable;
import java.io.IOException;

/**
 * Writer interface for features.
 *
 * @param <F> feature type.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public interface FeatureWriter<F extends Feature> extends Closeable {

    /**
     * Writes the header.
     *
     * @param header object to be written. May have restrictions depending on the implementation.
     *
     * @throws IOException                     if an IO error occurs.
     * @throws htsjdk.tribble.TribbleException if an encoding error occurs.
     */
    public void writeHeader(final Object header) throws IOException;

    /**
     * Adds a feature into the writer.
     *
     * @throws IOException                     if an IO error occurs
     * @throws htsjdk.tribble.TribbleException if an encoding error occurs.
     */
    public void add(final F feature) throws IOException;
}
