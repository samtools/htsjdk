package htsjdk.samtools.cram.compression.rans;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.RANSDecodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.util.Arrays;

/**
 * Decoder for the CRAM 3.0 rANS 4x8 codec. Supports Order-0 and Order-1 decoding
 * with 4-way interleaved rANS states. Each state processes one quarter of the output,
 * enabling instruction-level parallelism.
 */
public class RANS4x8Decode extends RANSDecode {

    private static final int RAW_BYTE_LENGTH = 4;

    /**
     * Uncompress a rANS 4x8 encoded byte stream. The first byte of the input
     * indicates the order (0 or 1), followed by compressed and uncompressed lengths,
     * the frequency table, and the encoded data.
     *
     * @param input the compressed byte stream
     * @return the uncompressed data
     */
    @Override
    public byte[] uncompress(final byte[] input) {
        if (input.length == 0) {
            return new byte[0];
        }

        final int[] inPos = {0};
        final RANSParams.ORDER order = RANSParams.ORDER.fromInt(input[inPos[0]++]);

        // compressed bytes length (LE int32)
        final int inSize = readLittleEndianInt(input, inPos);
        if (inSize != input.length - inPos[0] - RAW_BYTE_LENGTH) {
            throw new CRAMException("Invalid input length detected in a CRAM rans 4x8 input stream.");
        }

        // uncompressed bytes length (LE int32)
        final int outSize = readLittleEndianInt(input, inPos);
        final byte[] out = new byte[outSize];
        resetDecoderState();

        switch (order) {
            case ZERO:
                uncompressOrder0Way4(input, inPos, out, outSize);
                return out;
            case ONE:
                uncompressOrder1Way4(input, inPos, out, outSize);
                return out;
            default:
                throw new CRAMException("Unknown rANS order: " + order);
        }
    }

    private void uncompressOrder0Way4(final byte[] in, final int[] inPos, final byte[] out, final int outSize) {
        readStatsOrder0(in, inPos);

        long rans0 = readLittleEndianInt(in, inPos);
        long rans1 = readLittleEndianInt(in, inPos);
        long rans2 = readLittleEndianInt(in, inPos);
        long rans3 = readLittleEndianInt(in, inPos);

        final int out_end = outSize & ~3;
        final byte[] revLookup0 = getReverseLookup()[0];
        final RANSDecodingSymbol[] syms = getDecodingSymbols()[0];

        for (int i = 0; i < out_end; i += 4) {
            final byte c0 = revLookup0[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
            final byte c1 = revLookup0[Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
            final byte c2 = revLookup0[Utils.RANSGetCumulativeFrequency(rans2, Constants.TOTAL_FREQ_SHIFT)];
            final byte c3 = revLookup0[Utils.RANSGetCumulativeFrequency(rans3, Constants.TOTAL_FREQ_SHIFT)];

            out[i] = c0;
            out[i + 1] = c1;
            out[i + 2] = c2;
            out[i + 3] = c3;

            rans0 = syms[0xFF & c0].advanceSymbolStep(rans0, Constants.TOTAL_FREQ_SHIFT);
            rans1 = syms[0xFF & c1].advanceSymbolStep(rans1, Constants.TOTAL_FREQ_SHIFT);
            rans2 = syms[0xFF & c2].advanceSymbolStep(rans2, Constants.TOTAL_FREQ_SHIFT);
            rans3 = syms[0xFF & c3].advanceSymbolStep(rans3, Constants.TOTAL_FREQ_SHIFT);

            rans0 = Utils.RANSDecodeRenormalize4x8(rans0, in, inPos);
            rans1 = Utils.RANSDecodeRenormalize4x8(rans1, in, inPos);
            rans2 = Utils.RANSDecodeRenormalize4x8(rans2, in, inPos);
            rans3 = Utils.RANSDecodeRenormalize4x8(rans3, in, inPos);
        }

        int outIdx = out_end;
        switch (outSize & 3) {
            case 3:
                out[outIdx++] = revLookup0[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
                out[outIdx++] = revLookup0[Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
                out[outIdx++] = revLookup0[Utils.RANSGetCumulativeFrequency(rans2, Constants.TOTAL_FREQ_SHIFT)];
                break;
            case 2:
                out[outIdx++] = revLookup0[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
                out[outIdx++] = revLookup0[Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
                break;
            case 1:
                out[outIdx++] = revLookup0[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
                break;
        }
    }

    private void uncompressOrder1Way4(final byte[] in, final int[] inPos, final byte[] out, final int outSize) {
        readStatsOrder1(in, inPos);

        long rans0 = readLittleEndianInt(in, inPos);
        long rans1 = readLittleEndianInt(in, inPos);
        long rans2 = readLittleEndianInt(in, inPos);
        long rans7 = readLittleEndianInt(in, inPos);

        final int isz4 = outSize >> 2;
        int i0 = 0, i1 = isz4, i2 = 2 * isz4, i7 = 3 * isz4;
        byte l0 = 0, l1 = 0, l2 = 0, l7 = 0;
        final byte[][] revLookup = getReverseLookup();
        final RANSDecodingSymbol[][] syms = getDecodingSymbols();

        for (; i0 < isz4; i0++, i1++, i2++, i7++) {
            final byte c0 = revLookup[l0 & 0xFF][Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
            final byte c1 = revLookup[l1 & 0xFF][Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
            final byte c2 = revLookup[l2 & 0xFF][Utils.RANSGetCumulativeFrequency(rans2, Constants.TOTAL_FREQ_SHIFT)];
            final byte c7 = revLookup[l7 & 0xFF][Utils.RANSGetCumulativeFrequency(rans7, Constants.TOTAL_FREQ_SHIFT)];

            out[i0] = c0;
            out[i1] = c1;
            out[i2] = c2;
            out[i7] = c7;

            rans0 = syms[l0 & 0xFF][c0 & 0xFF].advanceSymbolStep(rans0, Constants.TOTAL_FREQ_SHIFT);
            rans1 = syms[l1 & 0xFF][c1 & 0xFF].advanceSymbolStep(rans1, Constants.TOTAL_FREQ_SHIFT);
            rans2 = syms[l2 & 0xFF][c2 & 0xFF].advanceSymbolStep(rans2, Constants.TOTAL_FREQ_SHIFT);
            rans7 = syms[l7 & 0xFF][c7 & 0xFF].advanceSymbolStep(rans7, Constants.TOTAL_FREQ_SHIFT);

            rans0 = Utils.RANSDecodeRenormalize4x8(rans0, in, inPos);
            rans1 = Utils.RANSDecodeRenormalize4x8(rans1, in, inPos);
            rans2 = Utils.RANSDecodeRenormalize4x8(rans2, in, inPos);
            rans7 = Utils.RANSDecodeRenormalize4x8(rans7, in, inPos);

            l0 = c0; l1 = c1; l2 = c2; l7 = c7;
        }

        // Remainder
        for (; i7 < outSize; i7++) {
            final byte c7 = revLookup[l7 & 0xFF][Utils.RANSGetCumulativeFrequency(rans7, Constants.TOTAL_FREQ_SHIFT)];
            out[i7] = c7;
            rans7 = syms[l7 & 0xFF][c7 & 0xFF].advanceSymbolStep(rans7, Constants.TOTAL_FREQ_SHIFT);
            rans7 = Utils.RANSDecodeRenormalize4x8(rans7, in, inPos);
            l7 = c7;
        }
    }

    private void readStatsOrder0(final byte[] in, final int[] inPos) {
        markRowUsed(0);
        final int[] freq = getFrequencies()[0];
        final byte[] revLookup = getReverseLookup()[0];
        final RANSDecodingSymbol[] decodingSymbols = getDecodingSymbols()[0];
        int rle = 0;
        int cumulativeFrequency = 0;
        int symbol = in[inPos[0]++] & 0xFF;
        do {
            if ((freq[symbol] = (in[inPos[0]++] & 0xFF)) >= 0x80) {
                freq[symbol] &= ~0x80;
                freq[symbol] = ((freq[symbol] & 0x7F) << 8) | (in[inPos[0]++] & 0xFF);
            }
            decodingSymbols[symbol].set(cumulativeFrequency, freq[symbol]);
            Arrays.fill(revLookup, cumulativeFrequency, cumulativeFrequency + freq[symbol], (byte) symbol);
            cumulativeFrequency += freq[symbol];

            if (rle == 0 && symbol + 1 == (in[inPos[0]] & 0xFF)) {
                symbol = in[inPos[0]++] & 0xFF;
                rle = in[inPos[0]++] & 0xFF;
            } else if (rle != 0) {
                rle--;
                symbol++;
            } else {
                symbol = in[inPos[0]++] & 0xFF;
            }
        } while (symbol != 0);
    }

    private void readStatsOrder1(final byte[] in, final int[] inPos) {
        final int[][] freq = getFrequencies();
        final byte[][] revLookup = getReverseLookup();
        final RANSDecodingSymbol[][] decodingSymbols = getDecodingSymbols();
        int rle_i = 0;
        int i = in[inPos[0]++] & 0xFF;
        do {
            markRowUsed(i);
            int rle_j = 0;
            int cumulativeFrequency = 0;
            int j = in[inPos[0]++] & 0xFF;
            do {
                if ((freq[i][j] = (in[inPos[0]++] & 0xFF)) >= 0x80) {
                    freq[i][j] &= ~0x80;
                    freq[i][j] = ((freq[i][j] & 0x7F) << 8) | (in[inPos[0]++] & 0xFF);
                }
                if (freq[i][j] == 0) {
                    freq[i][j] = Constants.TOTAL_FREQ;
                }
                decodingSymbols[i][j].set(cumulativeFrequency, freq[i][j]);
                Arrays.fill(revLookup[i], cumulativeFrequency, cumulativeFrequency + freq[i][j], (byte) j);
                cumulativeFrequency += freq[i][j];

                if (rle_j == 0 && j + 1 == (in[inPos[0]] & 0xFF)) {
                    j = in[inPos[0]++] & 0xFF;
                    rle_j = in[inPos[0]++] & 0xFF;
                } else if (rle_j != 0) {
                    rle_j--;
                    j++;
                } else {
                    j = in[inPos[0]++] & 0xFF;
                }
            } while (j != 0);

            if (rle_i == 0 && i + 1 == (in[inPos[0]] & 0xFF)) {
                i = in[inPos[0]++] & 0xFF;
                rle_i = in[inPos[0]++] & 0xFF;
            } else if (rle_i != 0) {
                rle_i--;
                i++;
            } else {
                i = in[inPos[0]++] & 0xFF;
            }
        } while (i != 0);
    }

    private static int readLittleEndianInt(final byte[] in, final int[] inPos) {
        int pos = inPos[0];
        final int value = (in[pos] & 0xFF)
                | ((in[pos + 1] & 0xFF) << 8)
                | ((in[pos + 2] & 0xFF) << 16)
                | ((in[pos + 3] & 0xFF) << 24);
        inPos[0] = pos + 4;
        return value;
    }
}
