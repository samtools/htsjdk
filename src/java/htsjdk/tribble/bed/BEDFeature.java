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
package htsjdk.tribble.bed;

import htsjdk.tribble.Feature;
import htsjdk.tribble.annotation.Strand;

import java.awt.*;

/**
 * @author jrobinso
 * @date Dec 24, 2009
 *
 * BED feature start and end positions must adhere to the Feature interval specifications.
 * This is different than the 0-based representation in a BED file.  This conversion is handled by {@link BEDCodec}.
 * Anyone writing a bed file should be aware of this difference.
 */
public interface BEDFeature extends Feature {
    Strand getStrand();

    String getType();

    Color getColor();

    String getDescription();

    java.util.List<FullBEDFeature.Exon> getExons();

    String getName();

    float getScore();

    String getLink();
}
