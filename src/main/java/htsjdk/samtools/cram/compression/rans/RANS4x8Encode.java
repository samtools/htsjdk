package htsjdk.samtools.cram.compression.rans;

import htsjdk.samtools.cram.CRAMException;

/**
 * Encoder for the CRAM 3.0 rANS 4x8 codec. Supports Order-0 and Order-1 encoding
 * with 4-way interleaved rANS states. Encoding proceeds backwards through the input
 * to produce a stream that can be decoded forwards.
 */
public class RANS4x8Encode extends RANSEncode<RANS4x8Params> {

    private static final int MINIMUM_ORDER_1_SIZE = 4;

    /**
     * Compress a byte array using the rANS 4x8 codec. Inputs shorter than
     * {@code MINIMUM_ORDER_1_SIZE} are always compressed with Order-0 regardless
     * of the requested order.
     *
     * @param input the data to compress
     * @param params encoding parameters specifying Order-0 or Order-1
     * @return the compressed byte stream including header, frequency table, and encoded data
     */
    @Override
    public byte[] compress(final byte[] input, final RANS4x8Params params) {
        if (input.length == 0) {
            return new byte[0];
        }
        if (input.length < MINIMUM_ORDER_1_SIZE) {
            return compressOrder0Way4(input);
        }
        switch (params.getOrder()) {
            case ZERO:
                return compressOrder0Way4(input);
            case ONE:
                return compressOrder1Way4(input);
            default:
                throw new CRAMException("Unknown rANS order: " + params.getOrder());
        }
    }

    private byte[] compressOrder0Way4(final byte[] in) {
        final int inputSize = in.length;
        final int[] F = calcFrequenciesOrder0(in);
        buildSymsOrder0(F);

        // Write frequency table
        final byte[] freqTable = new byte[1024];
        final int[] freqPos = {0};
        writeFrequenciesOrder0(freqTable, freqPos, F);
        final int frequencyTableSize = freqPos[0];

        // Encode backwards
        final RANSEncodingSymbol[] syms = getEncodingSymbols()[0];
        long rans0 = Constants.RANS_4x8_LOWER_BOUND;
        long rans1 = Constants.RANS_4x8_LOWER_BOUND;
        long rans2 = Constants.RANS_4x8_LOWER_BOUND;
        long rans3 = Constants.RANS_4x8_LOWER_BOUND;

        final int maxCompressedSize = inputSize + inputSize / 4 + 64;
        final byte[] compressedData = new byte[maxCompressedSize];
        final int[] writePos = {maxCompressedSize};

        // Remainder
        int i;
        switch (i = inputSize & 3) {
            case 3:
                rans2 = syms[in[inputSize - (i - 2)] & 0xFF].putSymbol4x8(rans2, compressedData, writePos);
            case 2:
                rans1 = syms[in[inputSize - (i - 1)] & 0xFF].putSymbol4x8(rans1, compressedData, writePos);
            case 1:
                rans0 = syms[in[inputSize - i] & 0xFF].putSymbol4x8(rans0, compressedData, writePos);
            case 0:
                break;
        }

        // Main loop
        for (i = inputSize & ~3; i > 0; i -= 4) {
            rans3 = syms[in[i - 1] & 0xFF].putSymbol4x8(rans3, compressedData, writePos);
            rans2 = syms[in[i - 2] & 0xFF].putSymbol4x8(rans2, compressedData, writePos);
            rans1 = syms[in[i - 3] & 0xFF].putSymbol4x8(rans1, compressedData, writePos);
            rans0 = syms[in[i - 4] & 0xFF].putSymbol4x8(rans0, compressedData, writePos);
        }

        // Flush states: rans3 first (highest addr), rans0 last (lowest addr) = LE
        flushState4x8(rans3, compressedData, writePos);
        flushState4x8(rans2, compressedData, writePos);
        flushState4x8(rans1, compressedData, writePos);
        flushState4x8(rans0, compressedData, writePos);

        final int compressedSize = maxCompressedSize - writePos[0];
        return assembleOutput(
                RANSParams.ORDER.ZERO,
                inputSize,
                freqTable,
                frequencyTableSize,
                compressedData,
                writePos[0],
                compressedSize);
    }

    private byte[] compressOrder1Way4(final byte[] in) {
        final int inSize = in.length;
        final int[][] F = calcFrequenciesOrder1(in);
        buildSymsOrder1(F);

        // Write frequency table
        final byte[] freqTable = new byte[257 * 256 * 3 + 256];
        final int[] freqPos = {0};
        writeFrequenciesOrder1(freqTable, freqPos, F);
        final int frequencyTableSize = freqPos[0];

        // Encode backwards
        final RANSEncodingSymbol[][] syms = getEncodingSymbols();
        long rans0 = Constants.RANS_4x8_LOWER_BOUND;
        long rans1 = Constants.RANS_4x8_LOWER_BOUND;
        long rans2 = Constants.RANS_4x8_LOWER_BOUND;
        long rans3 = Constants.RANS_4x8_LOWER_BOUND;

        final int maxCompressedSize = inSize + inSize / 4 + 64;
        final byte[] compressedData = new byte[maxCompressedSize];
        final int[] writePos = {maxCompressedSize};

        final int isz4 = inSize >> 2;
        int i0 = isz4 - 2;
        int i1 = 2 * isz4 - 2;
        int i2 = 3 * isz4 - 2;
        int i3;

        byte l0 = (i0 + 1 >= 0) ? in[i0 + 1] : 0;
        byte l1 = (i1 + 1 >= 0) ? in[i1 + 1] : 0;
        byte l2 = (i2 + 1 >= 0) ? in[i2 + 1] : 0;
        byte l3 = in[inSize - 1];

        // Remainder
        for (i3 = inSize - 2; i3 > 4 * isz4 - 2 && i3 >= 0; i3--) {
            final byte c3 = in[i3];
            rans3 = syms[c3 & 0xFF][l3 & 0xFF].putSymbol4x8(rans3, compressedData, writePos);
            l3 = c3;
        }

        // Main loop
        for (; i0 >= 0; i0--, i1--, i2--, i3--) {
            rans3 = syms[in[i3] & 0xFF][l3 & 0xFF].putSymbol4x8(rans3, compressedData, writePos);
            rans2 = syms[in[i2] & 0xFF][l2 & 0xFF].putSymbol4x8(rans2, compressedData, writePos);
            rans1 = syms[in[i1] & 0xFF][l1 & 0xFF].putSymbol4x8(rans1, compressedData, writePos);
            rans0 = syms[in[i0] & 0xFF][l0 & 0xFF].putSymbol4x8(rans0, compressedData, writePos);
            l0 = in[i0];
            l1 = in[i1];
            l2 = in[i2];
            l3 = in[i3];
        }

        // Final context=0 symbols
        rans3 = syms[0][l3 & 0xFF].putSymbol4x8(rans3, compressedData, writePos);
        rans2 = syms[0][l2 & 0xFF].putSymbol4x8(rans2, compressedData, writePos);
        rans1 = syms[0][l1 & 0xFF].putSymbol4x8(rans1, compressedData, writePos);
        rans0 = syms[0][l0 & 0xFF].putSymbol4x8(rans0, compressedData, writePos);

        // Flush states
        flushState4x8(rans3, compressedData, writePos);
        flushState4x8(rans2, compressedData, writePos);
        flushState4x8(rans1, compressedData, writePos);
        flushState4x8(rans0, compressedData, writePos);

        final int compressedSize = maxCompressedSize - writePos[0];
        return assembleOutput(
                RANSParams.ORDER.ONE,
                inSize,
                freqTable,
                frequencyTableSize,
                compressedData,
                writePos[0],
                compressedSize);
    }

    /** Write a 4-byte LE state backwards into the compressed data array. */
    private static void flushState4x8(final long rans, final byte[] out, final int[] writePos) {
        final int state = (int) rans;
        out[--writePos[0]] = (byte) ((state >> 24) & 0xFF);
        out[--writePos[0]] = (byte) ((state >> 16) & 0xFF);
        out[--writePos[0]] = (byte) ((state >> 8) & 0xFF);
        out[--writePos[0]] = (byte) (state & 0xFF);
    }

    /** Assemble the final output: [order(1)] [compressedLen(4)] [uncompressedLen(4)] [freqTable] [compressedData] */
    private static byte[] assembleOutput(
            final RANSParams.ORDER order,
            final int uncompressedSize,
            final byte[] freqTable,
            final int freqTableSize,
            final byte[] compressedData,
            final int compDataOffset,
            final int compDataSize) {
        final int totalCompressed = freqTableSize + compDataSize;
        final byte[] result = new byte[Constants.RANS_4x8_PREFIX_BYTE_LENGTH + totalCompressed];

        // Prefix: order(1) + compressedLen(4 LE) + uncompressedLen(4 LE)
        result[0] = (byte) (order == RANSParams.ORDER.ZERO ? 0 : 1);
        writeLittleEndianInt(result, Constants.RANS_4x8_ORDER_BYTE_LENGTH, totalCompressed);
        writeLittleEndianInt(
                result,
                Constants.RANS_4x8_ORDER_BYTE_LENGTH + Constants.RANS_4x8_COMPRESSED_BYTE_LENGTH,
                uncompressedSize);

        // Frequency table + compressed data
        System.arraycopy(freqTable, 0, result, Constants.RANS_4x8_PREFIX_BYTE_LENGTH, freqTableSize);
        System.arraycopy(
                compressedData,
                compDataOffset,
                result,
                Constants.RANS_4x8_PREFIX_BYTE_LENGTH + freqTableSize,
                compDataSize);
        return result;
    }

    private static void writeLittleEndianInt(final byte[] out, final int offset, final int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    // ---- Frequency calculation and writing (byte[]) ----

    private static int[] calcFrequenciesOrder0(final byte[] in) {
        final int T = in.length;
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        for (final byte b : in) F[b & 0xFF]++;

        int m = 0, M = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (m < F[j]) {
                m = F[j];
                M = j;
            }
        }

        final long tr = ((long) Constants.TOTAL_FREQ << 31) / T + (1 << 30) / T;
        int fsum = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] == 0) continue;
            if ((F[j] = (int) ((F[j] * tr) >> 31)) == 0) F[j] = 1;
            fsum += F[j];
        }
        if (fsum < Constants.TOTAL_FREQ) F[M] += Constants.TOTAL_FREQ - fsum;
        else F[M] -= fsum - Constants.TOTAL_FREQ;
        return F;
    }

    private static int[][] calcFrequenciesOrder1(final byte[] in) {
        final int in_size = in.length;
        final int[][] F = new int[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        final int[] T = new int[Constants.NUMBER_OF_SYMBOLS];
        int last_i = 0;
        for (int i = 0; i < in_size; i++) {
            int c = in[i] & 0xFF;
            F[last_i][c]++;
            T[last_i]++;
            last_i = c;
        }
        F[0][in[in_size >> 2] & 0xFF]++;
        F[0][in[2 * (in_size >> 2)] & 0xFF]++;
        F[0][in[3 * (in_size >> 2)] & 0xFF]++;
        T[0] += 3;

        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (T[i] == 0) continue;
            final double p = ((double) Constants.TOTAL_FREQ) / T[i];
            int t2 = 0, m = 0, M = 0;
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                if (F[i][j] == 0) continue;
                if (m < F[i][j]) {
                    m = F[i][j];
                    M = j;
                }
                if ((F[i][j] *= p) == 0) F[i][j] = 1;
                t2 += F[i][j];
            }
            if (t2 < Constants.TOTAL_FREQ) F[i][M] += Constants.TOTAL_FREQ - t2;
            else F[i][M] -= t2 - Constants.TOTAL_FREQ;
        }
        return F;
    }

    private static void writeFrequenciesOrder0(final byte[] out, final int[] pos, final int[] F) {
        int rle = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
                if (rle != 0) {
                    rle--;
                } else {
                    out[pos[0]++] = (byte) j;
                    if (j != 0 && F[j - 1] != 0) {
                        for (rle = j + 1; rle < Constants.NUMBER_OF_SYMBOLS && F[rle] != 0; rle++)
                            ;
                        rle -= j + 1;
                        out[pos[0]++] = (byte) rle;
                    }
                }
                if (F[j] < 128) {
                    out[pos[0]++] = (byte) F[j];
                } else {
                    out[pos[0]++] = (byte) (128 | (F[j] >> 8));
                    out[pos[0]++] = (byte) (F[j] & 0xFF);
                }
            }
        }
        out[pos[0]++] = 0;
    }

    private static void writeFrequenciesOrder1(final byte[] out, final int[] pos, final int[][] F) {
        final int[] T = new int[Constants.NUMBER_OF_SYMBOLS];
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++)
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) T[i] += F[i][j];

        int rle_i = 0;
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (T[i] == 0) continue;
            if (rle_i != 0) {
                rle_i--;
            } else {
                out[pos[0]++] = (byte) i;
                if (i != 0 && T[i - 1] != 0) {
                    for (rle_i = i + 1; rle_i < Constants.NUMBER_OF_SYMBOLS && T[rle_i] != 0; rle_i++)
                        ;
                    rle_i -= i + 1;
                    out[pos[0]++] = (byte) rle_i;
                }
            }

            final int[] F_i = F[i];
            int rle_j = 0;
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                if (F_i[j] != 0) {
                    if (rle_j != 0) {
                        rle_j--;
                    } else {
                        out[pos[0]++] = (byte) j;
                        if (j != 0 && F_i[j - 1] != 0) {
                            for (rle_j = j + 1; rle_j < Constants.NUMBER_OF_SYMBOLS && F_i[rle_j] != 0; rle_j++)
                                ;
                            rle_j -= j + 1;
                            out[pos[0]++] = (byte) rle_j;
                        }
                    }
                    if (F_i[j] < 128) {
                        out[pos[0]++] = (byte) F_i[j];
                    } else {
                        out[pos[0]++] = (byte) (128 | (F_i[j] >> 8));
                        out[pos[0]++] = (byte) (F_i[j] & 0xFF);
                    }
                }
            }
            out[pos[0]++] = 0;
        }
        out[pos[0]++] = 0;
    }
}
