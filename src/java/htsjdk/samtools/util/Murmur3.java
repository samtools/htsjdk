/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * MurmurHash3 was written by Austin Appleby, and is placed in the public
 * domain. The author hereby disclaims copyright to this source code.
 *
 * Source:
 * http://code.google.com/p/smhasher/source/browse/trunk/MurmurHash3.cpp
 * (Modified to adapt to Guava coding conventions and to use the HashFunction interface)
 *
 * Modified to remove stuff Clojure doesn't need, placed under clojure.lang namespace,
 * all fns made static, added hashOrdered/Unordered
 *
 * Modified again by Tim Fennell to remove code not needed by HTSJDK, to make methods non-static (so that different uses can
 * supply different seed values without colliding) and to comform to HTSJDK coding conventions where possible.
 *
 * @author Austin Appleby
 * @author Dimitris Andreou
 * @author Kurt Alfred Kluever
 */
package htsjdk.samtools.util;

import java.io.Serializable;

/**
 * Provides an implementation of the Murmur3_32 hash algorithm that has desirable properties in terms of randomness
 * and uniformity of the distribution of output values that make it a useful hashing algorithm for downsampling.
 */
public final class Murmur3 implements Serializable{
    private static final long serialVersionUID = 1L;

    private final int seed ;

    /** Constructs a Murmur3 hash with the given seed. */
    public Murmur3(final int seed) {
        this.seed = seed;
    }

    /** Hashes a character stream to an int using Murmur3. */
    public int hashUnencodedChars(CharSequence input){
        int h1 = this.seed;

        // step through the CharSequence 2 chars at a time
        final int length = input.length();
        for(int i = 1; i < length; i += 2)  {
            int k1 = input.charAt(i - 1) | (input.charAt(i) << 16);
            k1 = mixK1(k1);
            h1 = mixH1(h1, k1);
        }

        // deal with any remaining characters
        if((length & 1) == 1) {
            int k1 = input.charAt(length - 1);
            k1 = mixK1(k1);
            h1 ^= k1;
        }

        return fmix(h1, 2 * length);
    }

    private int hashInt(int input){
        if(input == 0) return 0;
        int k1 = mixK1(input);
        int h1 = mixH1(this.seed, k1);

        return fmix(h1, 4);
    }

    private int hashLong(long input){
        if(input == 0) return 0;
        int low = (int) input;
        int high = (int) (input >>> 32);

        int k1 = mixK1(low);
        int h1 = mixH1(this.seed, k1);

        k1 = mixK1(high);
        h1 = mixH1(h1, k1);

        return fmix(h1, 8);
    }

    private static int mixK1(int k1){
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        k1 *= c1;
        k1 = Integer.rotateLeft(k1, 15);
        k1 *= c2;
        return k1;
    }

    private static int mixH1(int h1, int k1){
        h1 ^= k1;
        h1 = Integer.rotateLeft(h1, 13);
        h1 = h1 * 5 + 0xe6546b64;
        return h1;
    }

    // Finalization mix - force all bits of a hash block to avalanche
    private static int fmix(int h1, int length){
        h1 ^= length;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }
}