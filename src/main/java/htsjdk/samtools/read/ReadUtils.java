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

package htsjdk.samtools.read;

import htsjdk.samtools.SAMUtils;

/**
 * Static utilities for working with {@link Read}.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public final class ReadUtils {

    // cannot be instantiated - utility class
    private ReadUtils() {}

    /**
     * Gets the alignment start (1-based, inclusive) adjusted for clipped bases.
     * Invalid to call on an unmapped read.
     *
     * <p>For example if the read has an alignment start of 100 but the first 4 bases were clipped
     * (hard or soft clipped) then this method will return 96.
     *
     * @return the un-clipped alignment start (1-based, inclusive).
     */
    public static int getUnclippedStart(final Read read) {
        return SAMUtils.getUnclippedStart(read.getStart(), read.getCigar());
    }

    /**
     * Gets the alignment end (1-based, inclusive) adjusted for clipped bases.
     * Invalid to call on an unmapped read.
     *
     * <p>For example if the read has an alignment end of 100 but the last 7 bases were clipped
     * (hard or soft clipped) then this method will return 107.
     *
     * @return the un-clipped alignment end (1-based, inclusive).
     */
    public static int getUnclippedEnd(final Read read) {
        return SAMUtils.getUnclippedEnd(read.getEnd(), read.getCigar());
    }
}
