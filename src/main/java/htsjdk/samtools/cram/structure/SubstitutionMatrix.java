/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.structure;

import java.util.Arrays;
import java.util.Comparator;

public class SubstitutionMatrix {
    public static final byte[] BASES = new byte[]{'A', 'C', 'G', 'T', 'N'};
    private static final byte[] BASES_LC = new byte[]{'a', 'c', 'g', 't', 'n'};
    private static final byte[] ORDER;

    static {
        ORDER = new byte[255];
        Arrays.fill(ORDER, (byte) -1);
        ORDER['A'] = 0;
        ORDER['C'] = 1;
        ORDER['G'] = 2;
        ORDER['T'] = 3;
        ORDER['N'] = 4;
    }

    private byte[] bytes = new byte[5];
    private final byte[][] codes = new byte[255][255];
    private final byte[][] bases = new byte[255][255];

    public SubstitutionMatrix(final long[][] frequencies) {
        for (final byte base : BASES) {
            bytes[ORDER[base]] = rank(base, frequencies[base]);
        }
        for (final byte[] base : bases) Arrays.fill(base, (byte) 'N');

        for (int i = 0; i < BASES.length; i++) {
            final byte r = BASES[i];
            for (final byte b : BASES) {
                if (r == b)
                    continue;
                bases[r][codes[r][b]] = b;
                bases[BASES_LC[i]][codes[r][b]] = b;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final byte r : "ACGTN".getBytes()) {
            stringBuilder.append((char) r);
            stringBuilder.append(':');
            for (int i = 0; i < 4; i++) {
                stringBuilder.append((char) bases[r][i]);
            }
            stringBuilder.append('\t');
        }
        return stringBuilder.toString();
    }

    public SubstitutionMatrix(final byte[] matrix) {
        this.bytes = matrix;

        for (final byte[] base : bases) Arrays.fill(base, (byte) 'N');

        bases['A'][(bytes[0] >> 6) & 3] = 'C';
        bases['A'][(bytes[0] >> 4) & 3] = 'G';
        bases['A'][(bytes[0] >> 2) & 3] = 'T';
        bases['A'][(bytes[0]) & 3] = 'N';
        System.arraycopy(bases['A'], 0, bases['a'], 0, 4);

        bases['C'][(bytes[1] >> 6) & 3] = 'A';
        bases['C'][(bytes[1] >> 4) & 3] = 'G';
        bases['C'][(bytes[1] >> 2) & 3] = 'T';
        bases['C'][(bytes[1]) & 3] = 'N';
        System.arraycopy(bases['C'], 0, bases['c'], 0, 4);

        bases['G'][(bytes[2] >> 6) & 3] = 'A';
        bases['G'][(bytes[2] >> 4) & 3] = 'C';
        bases['G'][(bytes[2] >> 2) & 3] = 'T';
        bases['G'][(bytes[2]) & 3] = 'N';
        System.arraycopy(bases['G'], 0, bases['g'], 0, 4);

        bases['T'][(bytes[3] >> 6) & 3] = 'A';
        bases['T'][(bytes[3] >> 4) & 3] = 'C';
        bases['T'][(bytes[3] >> 2) & 3] = 'G';
        bases['T'][(bytes[3]) & 3] = 'N';
        System.arraycopy(bases['T'], 0, bases['t'], 0, 4);

        bases['N'][(bytes[4] >> 6) & 3] = 'A';
        bases['N'][(bytes[4] >> 4) & 3] = 'C';
        bases['N'][(bytes[4] >> 2) & 3] = 'G';
        bases['N'][(bytes[4]) & 3] = 'T';

        for (final byte refBase : BASES) {
            for (byte code = 0; code < 4; code++)
                codes[refBase][bases[refBase][code]] = code;
        }
    }

    public byte[] getEncodedMatrix() {
        return bytes;
    }

    private static class SubCode {
        final byte base;
        long freq;
        byte rank;

        public SubCode(final byte base, final long freq) {
            this.base = base;
            this.freq = freq;
        }

    }

    private static final Comparator<SubCode> comparator = new Comparator<SubstitutionMatrix.SubCode>() {

        @Override
        public int compare(final SubCode o1, final SubCode o2) {
            if (o1.freq != o2.freq)
                return (int) (o2.freq - o1.freq);
            return ORDER[o1.base] - ORDER[o2.base];
        }
    };

    private byte rank(final byte refBase, final long[] frequencies) {
        // in alphabetical order:
        final SubCode[] subCodes = new SubCode[4];
        {
            int i = 0;
            for (final byte base : BASES) {
                if (refBase == base)
                    continue;
                subCodes[i++] = new SubCode(base, frequencies[base]);
            }
        }

        Arrays.sort(subCodes, comparator);

        for (byte i = 0; i < subCodes.length; i++)
            subCodes[i].rank = i;

        for (final SubCode subCode1 : subCodes) subCode1.freq = 0;

        Arrays.sort(subCodes, comparator);

        byte rank = 0;
        for (final SubCode subCode : subCodes) {
            rank <<= 2;
            rank |= subCode.rank;
        }

        for (final SubCode s : subCodes)
            codes[refBase][s.base] = s.rank;

        return rank;
    }

    public byte code(final byte refBase, final byte readBase) {
        return codes[refBase][readBase];
    }

    public byte base(final byte refBase, final byte code) {
        return bases[refBase][code];
    }
}
