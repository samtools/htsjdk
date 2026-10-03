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
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.index.tabix.TabixFormat;

import java.io.IOException;

/**
 * Encoder interface for features.
 * <p/>
 * FeatureEncoder have to implement two key methods:
 * <p/>
 * {@link #writeHeader(Appendable, Object)} - Encodes and writes the header to the output.
 * {@link #write(Appendable, F)} - Encodes and writes a feature to the output.
 * <p/>
 *
 * @param <F> feature type.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public interface FeatureEncoder<F extends Feature> {

    /**
     * Encodes the feature and add it to the output.
     *
     * @param out     the output to append the feature in.
     * @param feature object to be encoded.
     *
     * @throws IOException if a IO error occurs.
     * @throws TribbleException if an encoding error occurs.
     */
    public void write(final Appendable out, final F feature) throws IOException;

    /**
     * Encodes the header and add it to the output.
     *
     * @param out    the output to append the feature in.
     * @param header object to be encoded. May have restrictions depending on the implementation.
     *
     * @throws IOException if a IO error occurs.
     * @throws TribbleException if an encoding error occurs.
     */
    public void writeHeader(final Appendable out, final Object header) throws IOException;

    /**
     * Defines the tabix format for the feature, used for indexing.
     *
     * @return the format to use with tabix.
     *
     * @throws TribbleException if the format is not defined.
     */
    public default TabixFormat getTabixFormat() {
        throw new TribbleException(
                this.getClass().getSimpleName() + " does not have defined tabix format");
    }
}
