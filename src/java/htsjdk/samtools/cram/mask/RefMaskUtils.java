/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.mask;

import java.util.Arrays;

public class RefMaskUtils {
    public static final byte[] bases = new byte[]{'A', 'C', 'G', 'T'};
    public static final byte[] base2index = new byte[256];

    static {
        Arrays.fill(base2index, (byte) -1);
        base2index['A'] = 0;
        base2index['a'] = 0;
        base2index['C'] = 1;
        base2index['c'] = 1;
        base2index['G'] = 2;
        base2index['g'] = 2;
        base2index['T'] = 3;
        base2index['t'] = 3;
    }

    public static int minHits = 2;

    public static final int getBaseCount(byte mask, byte base) {
        return 3 & (mask >>> (2 * base2index[base]));
    }

    public static final byte addReadBase(byte mask, byte readBase, byte refBase) {
        byte baseMask = 0;
        switch (readBase) {
            case 'A':
            case 'a':
                // 00000011
                baseMask = 3;
                break;
            case 'C':
            case 'c':
                // 00001100
                baseMask = 12;
                break;
            case 'G':
            case 'g':
                // 00110000
                baseMask = 48;
                break;
            case 'T':
            case 't':
                // 11000000
                baseMask = ~63;
                break;

            default:
                // throw new IllegalArgumentException("Unkown base: " + readBase);
                return mask;
        }

        // byte count = (byte) ((baseMask & mask) >>> (2 * base2index[readBase])
        // & 3);
        int count = getBaseCount(mask, readBase);
        if (count < 3) {
            count++;
            mask &= ~baseMask;
            mask |= count << (2 * base2index[readBase]);
        }
        return mask;
    }

    public static final boolean shouldStore(byte mask, byte refBase) {
        for (byte base : bases) {
            if (base == refBase)
                continue;
            if (getBaseCount(mask, base) >= minHits)
                return true;
        }
        return false;
    }

    public static final int getBaseCount(short mask, byte base) {
        return 15 & (mask >>> (4 * base2index[base]));
    }

    public static final short addReadBase(short mask, byte readBase, byte refBase) {
        short baseMask = 0;
        switch (readBase) {
            case 'A':
            case 'a':
                // 00000000 00001111
                baseMask = 15;
                break;
            case 'C':
            case 'c':
                // 00000000 11110000
                baseMask = 240;
                break;
            case 'G':
            case 'g':
                // 00001111 00000000
                baseMask = 3840;
                break;
            case 'T':
            case 't':
                // 11110000 00000000
                baseMask = ~4095;
                break;

            default:
                // throw new IllegalArgumentException("Unkown base: " + readBase);
                return mask;
        }

        int count = getBaseCount(mask, readBase);
        if (count < 15) {
            count++;
            mask &= ~baseMask;
            mask |= count << (4 * base2index[readBase]);
        }
        return mask;
    }

    private static final int minCoverageEstimate(short mask) {
        int coverage = 15 & mask;
        coverage += 15 & (mask >>> 4);
        coverage += 15 & (mask >>> 8);
        coverage += 15 & (mask >>> 12);

        return coverage;
    }

    public static final boolean shouldStore(short mask, byte refBase) {
        if (minCoverageEstimate(mask) > 10)
            return false;
        int[] counts = new int[3];
        int i = 0;
        for (byte base : bases) {
            if (base == refBase)
                continue;
            counts[i] = getBaseCount(mask, base);
            if (counts[i] >= minHits)
                return true;
            i++;
        }

        // Arrays.sort(counts);
        // int depth = minCoverageEstimate(mask);
        // if (depth > 10 && ((float) counts[1] + counts[2]) / counts[0] > 0.3)
        // return true;

        return false;
    }

    public static class RefMask {
        private short[] mask;
        private int minHits;

        public RefMask(int len, int minHits) {
            super();
            this.mask = new short[len];
            Arrays.fill(mask, (short) 0);
            this.minHits = minHits;
        }

        public void addReadBase(int pos, byte readBase, byte refBase) {
            mask[pos] = RefMaskUtils.addReadBase(mask[pos], readBase, refBase);
        }

        public boolean shouldStore(int pos, byte refBase) {
            short maskAtPos = mask[pos];
            if (maskAtPos == 0)
                return false;

            if (minCoverageEstimate(maskAtPos) > 10)
                return false;
            int[] counts = new int[3];
            int i = 0;
            for (byte base : bases) {
                if (base == refBase)
                    continue;
                counts[i] = getBaseCount(maskAtPos, base);
                if (counts[i] >= minHits)
                    return true;
                i++;
            }

            return false;
        }

        public int getMinHits() {
            return minHits;
        }

        public void setMinHits(int minHits) {
            this.minHits = minHits;
        }

    }
}
