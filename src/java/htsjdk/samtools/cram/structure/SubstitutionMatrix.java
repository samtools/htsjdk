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

    public SubstitutionMatrix(long[][] freqs) {
        for (byte BASE : BASES) {
            bytes[ORDER[BASE]] = rank(BASE, freqs[BASE]);
        }
        for (byte[] base : bases) Arrays.fill(base, (byte) 'N');

        for (int i = 0; i < BASES.length; i++) {
            byte r = BASES[i];
            for (byte b : BASES) {
                if (r == b)
                    continue;
                bases[r][codes[r][b]] = b;
                bases[BASES_LC[i]][codes[r][b]] = b;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (byte r : "ACGTN".getBytes()) {
            sb.append((char) r);
            sb.append(":");
            for (int i = 0; i < 4; i++) {
                sb.append((char) bases[r][i]);
            }
            sb.append("\t");
        }
        return sb.toString();
    }

    public SubstitutionMatrix(byte[] matrix) {
        this.bytes = matrix;

        for (byte[] base : bases) Arrays.fill(base, (byte) 'N');

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

        for (byte refBase : BASES) {
            for (byte code = 0; code < 4; code++)
                codes[refBase][bases[refBase][code]] = code;
        }
    }

    public byte[] getEncodedMatrix() {
        return bytes;
    }

    private static class SubCode {
        final byte ref;
        final byte base;
        long freq;
        byte rank;

        public SubCode(byte ref, byte base, long freq) {
            super();
            this.ref = ref;
            this.base = base;
            this.freq = freq;
        }

    }

    private static final Comparator<SubCode> comparator = new Comparator<SubstitutionMatrix.SubCode>() {

        @Override
        public int compare(SubCode o1, SubCode o2) {
            if (o1.freq != o2.freq)
                return (int) (o2.freq - o1.freq);
            return ORDER[o1.base] - ORDER[o2.base];
        }
    };

    private byte rank(byte refBase, long[] freqs) {
        // in alphabetical order:
        SubCode[] subCodes = new SubCode[4];
        {
            int i = 0;
            for (byte base : BASES) {
                if (refBase == base)
                    continue;
                subCodes[i++] = new SubCode(refBase, base, freqs[base]);
            }
        }

        Arrays.sort(subCodes, comparator);

        for (byte i = 0; i < subCodes.length; i++)
            subCodes[i].rank = i;

        for (SubCode subCode1 : subCodes) subCode1.freq = 0;

        Arrays.sort(subCodes, comparator);

        byte rank = 0;
        for (SubCode subCode : subCodes) {
            rank <<= 2;
            rank |= subCode.rank;
        }

        for (SubCode s : subCodes)
            codes[refBase][s.base] = s.rank;

        return rank;
    }

    public byte code(byte refBase, byte readBase) {
        return codes[refBase][readBase];
    }

    public byte base(byte refBase, byte code) {
        return bases[refBase][code];
    }
}
