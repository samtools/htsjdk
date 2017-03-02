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

package htsjdk.tribble.bed;

import htsjdk.tribble.Feature;
import htsjdk.tribble.writer.FeatureEncoder;
import htsjdk.tribble.index.tabix.TabixFormat;

import java.io.IOException;

/**
 * Encode any kind of feature into a simple BED format.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class BEDSimpleEncoder implements FeatureEncoder<Feature> {

    // default separator for BED files written by this encoder
    private static final char BED_SEPARATOR = '\t';

    /**
     * Writes as a BED feature. Note: BED coordinates are 0-based, so the start position returned by
     * {@link Feature#getStart()} may be different thant the output start position.
     */
    @Override
    public void write(final Appendable out, final Feature feature) throws IOException {
        out.append(feature.getContig())
                // The BED format uses a first-base-is-zero convention, Tribble features use 1 => subtract 1.
                .append(BED_SEPARATOR).append(Integer.toString(feature.getStart() - 1))
                .append(BED_SEPARATOR).append(Integer.toString(feature.getEnd()));
    }

    /** Simple BED encoder does not write any header. */
    @Override
    public void writeHeader(final Appendable out, final Object header) throws IOException {
        // does not write anything
    }

    @Override
    public TabixFormat getTabixFormat() {
        return TabixFormat.BED;
    }
}
