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

/**
 * Substitution matrix, used to represent base substitutions for reference-based CRAM
 * compression.
 *
 * The matrix is stored internally in two forms; the packed/encoded form used for serialization,
 * and an expanded in-memory form used for fast bi-directional interconversion between bases and
 * substitution codes during read and write.
 *
 * This implementation substitutes both upper and lower case versions of a give base with the
 * same (upper case) substitute base.
 */
public class SubstitutionMatrix {
    // List of bases subject to substitution. The upper and lower case arrays must have the bases
    // in the same order so that the index for a given base is the same for upper and lower case
    public static final byte[] SUBSTITUTION_BASES_UPPER = new byte[]{'A', 'C', 'G', 'T', 'N'};
    private static final byte[] SUBSTITUTION_BASES_LOWER = new byte[]{'a', 'c', 'g', 't', 'n'};
    private static int BASES_SIZE = SUBSTITUTION_BASES_UPPER.length;

    // Since bases are represented as bytes, there are theoretically 256 possible symbols in the
    // symbol space, though in reality we only care about 10 of these (5 upper and 5 lower case
    // reference bases).
    private static int SYMBOL_SPACE_SIZE = 256;

    // number of possible substitution codes per base
    private static int CODES_PER_BASE = BASES_SIZE - 1;

    // The order in which substitution codes are encoded in the serialized matrix, as prescribed
    // by the CRAM spec:
    private static final byte[] CODE_ORDER;
    static {
        CODE_ORDER = new byte[SYMBOL_SPACE_SIZE];
        Arrays.fill(CODE_ORDER, (byte) -1);
        CODE_ORDER['A'] = 0;
        CODE_ORDER['C'] = 1;
        CODE_ORDER['G'] = 2;
        CODE_ORDER['T'] = 3;
        CODE_ORDER['N'] = 4;
    }

    // The substitution "matrix" in serialized form, encoded in a 5 byte vector, one byte for
    // each base in the set of possible substitution bases { A, C, G, T and N }, in that order.
    // Each byte in turn represents a packed bit vector of substitution codes (values 0-3, encoded
    // in 2 bits each), one for each possible substitution of that base with another base from the
    // same set, in the same order. The allows the most frequent substitution codes(s) to have the
    // shortest prefix(es) when represented as ITF8 in the context of serialized read features.
    private byte[] encodedMatrixBytes = new byte[BASES_SIZE];

    // The expanded in-memory matrix of substitution codes. In order to enable quick inter-conversion
    // between bases and substitution codes, we use a full square but sparse matrix that covers
    // the entire symbol space in order to allow them to be directly indexed by (refBase, readBase)
    // pairs. Note that this array can be indexed using upper or lower case bases.
    private final byte[][] codeByBase = new byte[SYMBOL_SPACE_SIZE][SYMBOL_SPACE_SIZE];

    // The expanded in-memory matrix of substitute bases, indexed by (refBase, code) pairs, used when
    // reading cram records. Note that this array can be indexed using upper or lower case bases.
    private final byte[][] baseByCode = new byte[SYMBOL_SPACE_SIZE][SYMBOL_SPACE_SIZE];

    /**
     * Create a SubstitutionMatrix given a set of base substitution frequencies
     * @param frequencies array of base substitution frequencies
     */
    public SubstitutionMatrix(final long[][] frequencies) {
        // get the substitution code vector for each base
        for (final byte base : SUBSTITUTION_BASES_UPPER) {
            // substitutionCodeVector has a side effect of updating codeByBase
            encodedMatrixBytes[CODE_ORDER[base]] = substitutionCodeVector(base, frequencies[base]);
        }

        for (final byte[] base : baseByCode) {
            Arrays.fill(base, (byte) 'N');
        }
        for (int i = 0; i < SUBSTITUTION_BASES_UPPER.length; i++) {
            final byte r = SUBSTITUTION_BASES_UPPER[i];
            for (final byte b : SUBSTITUTION_BASES_UPPER) {
                if (r == b) {
                    continue;
                }
                baseByCode[r][codeByBase[r][b]] = b;
                // propagate the same code for lower case bases
                baseByCode[SUBSTITUTION_BASES_LOWER[i]][codeByBase[r][b]] = b;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final byte r : SUBSTITUTION_BASES_UPPER) {
            stringBuilder.append((char) r);
            stringBuilder.append(':');
            for (int i = 0; i < CODES_PER_BASE; i++) {
                stringBuilder.append((char) baseByCode[r][i]);
            }
            stringBuilder.append('\t');
        }
        return stringBuilder.toString();
    }

    /**
     * Create a SubstitutionMatrix from a serialized byte array
     * @param matrix serialized substitution matrix from a CRAM stream
     */
    public SubstitutionMatrix(final byte[] matrix) {
        this.encodedMatrixBytes = matrix;
        for (final byte[] base : baseByCode) {
            Arrays.fill(base, (byte) 'N');
        }

        // unpack the substitution code vectors and populate the base substitutions lookup
        // matrix using the unpacked substitution codes
        baseByCode['A'][(encodedMatrixBytes[0] >> 6) & 3] = 'C';
        baseByCode['A'][(encodedMatrixBytes[0] >> 4) & 3] = 'G';
        baseByCode['A'][(encodedMatrixBytes[0] >> 2) & 3] = 'T';
        baseByCode['A'][(encodedMatrixBytes[0]) & 3] = 'N';
        // propagate to lower case 'a'
        System.arraycopy(baseByCode['A'], 0, baseByCode['a'], 0, 4);

        baseByCode['C'][(encodedMatrixBytes[1] >> 6) & 3] = 'A';
        baseByCode['C'][(encodedMatrixBytes[1] >> 4) & 3] = 'G';
        baseByCode['C'][(encodedMatrixBytes[1] >> 2) & 3] = 'T';
        baseByCode['C'][(encodedMatrixBytes[1]) & 3] = 'N';
        // propagate to lower case 'c'
        System.arraycopy(baseByCode['C'], 0, baseByCode['c'], 0, 4);

        baseByCode['G'][(encodedMatrixBytes[2] >> 6) & 3] = 'A';
        baseByCode['G'][(encodedMatrixBytes[2] >> 4) & 3] = 'C';
        baseByCode['G'][(encodedMatrixBytes[2] >> 2) & 3] = 'T';
        baseByCode['G'][(encodedMatrixBytes[2]) & 3] = 'N';
        // propagate to lower case 'g'
        System.arraycopy(baseByCode['G'], 0, baseByCode['g'], 0, 4);

        baseByCode['T'][(encodedMatrixBytes[3] >> 6) & 3] = 'A';
        baseByCode['T'][(encodedMatrixBytes[3] >> 4) & 3] = 'C';
        baseByCode['T'][(encodedMatrixBytes[3] >> 2) & 3] = 'G';
        baseByCode['T'][(encodedMatrixBytes[3]) & 3] = 'N';
        // propagate to lower case 't'
        System.arraycopy(baseByCode['T'], 0, baseByCode['t'], 0, 4);

        baseByCode['N'][(encodedMatrixBytes[4] >> 6) & 3] = 'A';
        baseByCode['N'][(encodedMatrixBytes[4] >> 4) & 3] = 'C';
        baseByCode['N'][(encodedMatrixBytes[4] >> 2) & 3] = 'G';
        baseByCode['N'][(encodedMatrixBytes[4]) & 3] = 'T';

        for (final byte refBase : SUBSTITUTION_BASES_UPPER) {
            for (byte code = 0; code < CODES_PER_BASE; code++) {
                codeByBase[refBase][baseByCode[refBase][code]] = code;
            }
        }
    }

    /**
     * Return this substitution matrix as a byte array in a form suitable for serialization.
     * @return
     */
    public byte[] getEncodedMatrix() {
        return encodedMatrixBytes;
    }

    private static class SubstitutionFrequency {
        final byte base;
        long freq;
        byte rank;

        public SubstitutionFrequency(final byte base, final long freq) {
            this.base = base;
            this.freq = freq;
        }
    }

    private static final Comparator<SubstitutionFrequency> comparator = new Comparator<SubstitutionFrequency>() {

        @Override
        public int compare(final SubstitutionFrequency o1, final SubstitutionFrequency o2) {
            // primary sort by frequency
            if (o1.freq != o2.freq) {
                return (int) (o2.freq - o1.freq);
            }
            // same frequency; compare based on spec tie-breaking rule (use base order prescribed by the spec)
            return CODE_ORDER[o1.base] - CODE_ORDER[o2.base];
        }
    };

    // For the given base, return a packed substitution vector containing the possible
    // substitution codes given the set of substitution frequencies for that base.
    //
    // NOTE: this has a side effect in that is also populates the codeByBase matrix for this base.
    private byte substitutionCodeVector(final byte refBase, final long[] frequencies) {
        // there are 5 possible bases, so there are 4 possible substitutions for each base
        final SubstitutionFrequency[] subCodes = new SubstitutionFrequency[CODES_PER_BASE];
        int i = 0;
        for (final byte base : SUBSTITUTION_BASES_UPPER) {
            if (refBase == base) {
                continue;
            }
            subCodes[i++] = new SubstitutionFrequency(base, frequencies[base]);
        }

        // sort the codes for this base based on substitution frequency
        Arrays.sort(subCodes, comparator);

        // set each SubstitutionFrequency to it's relative rank now that we know it, and reset the frequencies
        // so we can then re-sort, without frequency bias, back to the original (and prescribed)
        // order in which we want to emit the codes
        for (byte j = 0; j < subCodes.length; j++) {
            subCodes[j].rank = j;
        }

        for (final SubstitutionFrequency subCode1 : subCodes) {
            subCode1.freq = 0;
        }

        // re-sort back to the fixed order prescribed by the spec so we can store the substitution
        // codes in the matrix in the prescribed order
        Arrays.sort(subCodes, comparator);

        byte codeVector = 0;
        for (final SubstitutionFrequency subCode : subCodes) {
            codeVector <<= 2;
            codeVector |= subCode.rank;
        }

        for (final SubstitutionFrequency s : subCodes) {
            codeByBase[refBase][s.base] = s.rank;
        }

        return codeVector;
    }

    /**
     * Given a reference base and a read base, find the corresponding substitution code
     * @param refBase reference base being substituted
     * @param readBase read base to substitute for the reference base
     * @return code to be used for this refBase/readBase pair
     */
    public byte code(final byte refBase, final byte readBase) {
        return codeByBase[refBase][readBase];
    }

    /**
     * Given a reference base and a substitution code, return the corresponding substitution base.
     * @param refBase reference base being substituted
     * @param code substitution code
     * @return base to be substituted for this (refBase, code) pair
     */
    public byte base(final byte refBase, final byte code) {
        return baseByCode[refBase][code];
    }
}
