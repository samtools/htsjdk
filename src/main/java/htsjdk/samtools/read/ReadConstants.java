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

import htsjdk.samtools.GenomicIndexUtil;

/**
 * Constants for implementations of the {@link Read} interface.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public final class ReadConstants {

    // cannot be instantiated
    private ReadConstants() {}

    /**
     * Alignment score for a good alignment, but where computing a Phred-score is not feasible.
     */
    public static final int UNKNOWN_MAPPING_QUALITY = 255;

    /**
     * Alignment score for an unaligned read.
     */
    public static final int NO_MAPPING_QUALITY = 0;

    /**
     * Unset reference name for unaligned reads.
     *
     * <p>Note: not all unaligned reads have this reference name.
     */
    public static final String NO_ALIGNMENT_REFERENCE_NAME = "*";

    /**
     * Unset reference index for unaligned reads.
     *
     * <p>Note: not all unaligned reads have this reference index.
     */
    public static final int NO_ALIGNMENT_REFERENCE_INDEX = -1;

    /**
     * Cigar string for an unaligned read.
     */
    public static final String NO_ALIGNMENT_CIGAR = "*";

    /**
     * Unaligned position.
     *
     * <p>If a read has {@link #NO_ALIGNMENT_REFERENCE_NAME}, it will have this value for position.
     */
    public static final int NO_ALIGNMENT_START = GenomicIndexUtil.UNSET_GENOMIC_LOCATION;

    /**
     * Unset value for the read sequence.
     *
     * <p>This should rarely be used, since a read with no sequence doesn't make much sense.
     */
    public static final byte[] NULL_SEQUENCE = new byte[0];

    /**
     * Unset value for the read sequence.
     *
     * <p>This should rarely be used, since a read with no sequence doesn't make much sense.
     */
    public static final String NULL_SEQUENCE_STRING = "*";

    /**
     * Unset value for the read quality.
     */
    public static final byte[] NULL_QUALS = new byte[0];

    /**
     * Unset value for the read quality.
     */
    public static final String NULL_QUALS_STRING = "*";
}
