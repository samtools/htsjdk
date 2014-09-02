/*
 * The MIT License
 *
 * Copyright (c) 2014 Genome Research Limited
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

package htsjdk.samtools.util;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

/**
 * Projects strings into a hash space and uses that to make a decision 
 */
public class HashDecisionProjector {
    private final HashFunction hf;
    private final double prob;
    private final int space_length = Integer.MAX_VALUE;

    public HashDecisionProjector(final int space_warp, final double prob) {
        this.hf = Hashing.murmur3_32(space_warp);
        this.prob = prob;
    }

    public boolean decide(final String input) {
        HashCode hash = this.hf.hashUnencodedChars(input);
        if ( hash.asInt()/space_length < prob) {
            return false;
        } else {
            return true;
        }
    }
}
