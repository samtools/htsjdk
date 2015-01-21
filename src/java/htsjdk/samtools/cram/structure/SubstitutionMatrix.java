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

import htsjdk.samtools.cram.io.BitwiseUtils;
import htsjdk.samtools.util.Log;

import java.util.Arrays;
import java.util.Comparator;

public class SubstitutionMatrix {
    public static final byte[] BASES = new byte[]{'A', 'C', 'G', 'T', 'N'};
    public static final byte[] BASES_LC = new byte[]{'a', 'c', 'g', 't', 'n'};
    public static final byte[] ORDER;

    static {
        ORDER = new byte[255];
        Arrays.fill(ORDER, (byte) -1);
        ORDER['A'] = 0;
        ORDER['C'] = 1;
        ORDER['G'] = 2;
        ORDER['T'] = 3;
        ORDER['N'] = 4;
    }

    private static Log log = Log.getInstance(SubstitutionMatrix.class);

    private byte[] bytes = new byte[5];
    private byte[][] codes = new byte[255][255];
    private byte[][] bases = new byte[255][255];

    public SubstitutionMatrix(long[][] freqs) {
        for (int i = 0; i < BASES.length; i++) {
            bytes[ORDER[BASES[i]]] = rank(BASES[i], freqs[BASES[i]]);
        }
        for (int i = 0; i < bases.length; i++)
            Arrays.fill(bases[i], (byte) 'N');

        for (int i = 0; i < BASES.length; i++) {
            byte r = BASES[i];
            for (byte b : BASES) {
                if (r == b)
                    continue;
                bases[r][codes[r][b]] = b;
                bases[BASES_LC[i]][codes[r][b]] = b;
            }
        }

        dump();
    }

    public void dump() {
        log.debug("Subs matrix: " + Arrays.toString(bytes) + ": " + BitwiseUtils.toBitString(bytes));

        StringBuffer sb = new StringBuffer("Subs matrix decoded: ");
        for (byte r : "ACGTN".getBytes()) {
            sb.append((char) r);
            sb.append(":");
            for (int i = 0; i < 4; i++) {
                sb.append((char) bases[r][i]);
            }
            sb.append("\t");
        }
        log.debug(sb.toString());
    }

    public SubstitutionMatrix(byte[] matrix) {
        this.bytes = matrix;

        for (int i = 0; i < bases.length; i++)
            Arrays.fill(bases[i], (byte) 'N');

        bases['A'][(bytes[0] >> 6) & 3] = 'C';
        bases['A'][(bytes[0] >> 4) & 3] = 'G';
        bases['A'][(bytes[0] >> 2) & 3] = 'T';
        bases['A'][(bytes[0] >> 0) & 3] = 'N';
        for (int i = 0; i < 4; i++)
            bases['a'][i] = bases['A'][i];

        bases['C'][(bytes[1] >> 6) & 3] = 'A';
        bases['C'][(bytes[1] >> 4) & 3] = 'G';
        bases['C'][(bytes[1] >> 2) & 3] = 'T';
        bases['C'][(bytes[1] >> 0) & 3] = 'N';
        for (int i = 0; i < 4; i++)
            bases['c'][i] = bases['C'][i];

        bases['G'][(bytes[2] >> 6) & 3] = 'A';
        bases['G'][(bytes[2] >> 4) & 3] = 'C';
        bases['G'][(bytes[2] >> 2) & 3] = 'T';
        bases['G'][(bytes[2] >> 0) & 3] = 'N';
        for (int i = 0; i < 4; i++)
            bases['g'][i] = bases['G'][i];

        bases['T'][(bytes[3] >> 6) & 3] = 'A';
        bases['T'][(bytes[3] >> 4) & 3] = 'C';
        bases['T'][(bytes[3] >> 2) & 3] = 'G';
        bases['T'][(bytes[3] >> 0) & 3] = 'N';
        for (int i = 0; i < 4; i++)
            bases['t'][i] = bases['T'][i];

        bases['N'][(bytes[4] >> 6) & 3] = 'A';
        bases['N'][(bytes[4] >> 4) & 3] = 'C';
        bases['N'][(bytes[4] >> 2) & 3] = 'G';
        bases['N'][(bytes[4] >> 0) & 3] = 'T';

        for (byte refBase : BASES) {
            for (byte code = 0; code < 4; code++)
                codes[refBase][bases[refBase][code]] = code;
        }

        dump();
    }

    public byte[] getEncodedMatrix() {
        return bytes;
    }

    private static class SubCode {
        byte ref, base;
        long freq;
        byte rank;

        public SubCode(byte ref, byte base, long freq) {
            super();
            this.ref = ref;
            this.base = base;
            this.freq = freq;
        }

        byte code;
    }

    private static Comparator<SubCode> comparator = new Comparator<SubstitutionMatrix.SubCode>() {

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

        for (byte i = 0; i < subCodes.length; i++)
            subCodes[i].freq = 0;

        Arrays.sort(subCodes, comparator);

        byte rank = 0;
        for (byte i = 0; i < subCodes.length; i++) {
            rank <<= 2;
            rank |= subCodes[i].rank;

            // SubCode s = subCodes[i];
            // System.out
            // .printf("i=%d, BASES[i]=%d, ORDER[BASES[i]]=%d, s.ref=%d, s.base=%d, s.code=%d, s.rank=%d\n",
            // i, BASES[i], ORDER[BASES[i]], s.ref, s.base,
            // s.code, s.rank);
        }

        for (SubCode s : subCodes)
            codes[refBase][s.base] = s.rank;

        return rank;
    }

    public byte code(byte refBase, byte readBase) {
        return codes[refBase][readBase];
    }

    public byte base(byte refBase, byte code) {
        byte base = bases[refBase][code];
        return base;
    }

    public static void main(String[] args) {
        SubstitutionMatrix m = new SubstitutionMatrix(new byte[]{27,
                (byte) 228, 27, 27, 27});

        for (byte refBase : BASES) {
            for (byte base : BASES) {
                if (refBase == base)
                    continue;
                System.out.printf("Ref=%c, base=%c, code=%d\n", (char) refBase,
                        (char) base, m.code(refBase, base));
            }
        }
        System.out.println(Arrays.toString(m.bytes));
        System.out.println("===============================================");

        long[][] freqs = new long[255][255];
        for (int r = 0; r < BASES.length; r++) {
            for (int b = 0; b < BASES.length; b++) {
                if (r == b)
                    continue;
                freqs[BASES[r]][BASES[b]] = b;
            }
        }

        m = new SubstitutionMatrix(freqs);
        for (byte refBase : BASES) {
            for (byte base : BASES) {
                if (refBase == base)
                    continue;
                System.out.printf("Ref=%c, base=%c, code=%d\n", (char) refBase,
                        (char) base, m.code(refBase, base));
            }
        }
        System.out.println(Arrays.toString(m.bytes));
    }
}
