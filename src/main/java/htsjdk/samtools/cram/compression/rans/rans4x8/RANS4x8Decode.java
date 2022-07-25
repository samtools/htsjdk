package htsjdk.samtools.cram.compression.rans.rans4x8;

import htsjdk.samtools.cram.compression.rans.ArithmeticDecoder;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.RANSDecodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RANS4x8Decode extends RANSDecode {

    private static final int RAW_BYTE_LENGTH = 4;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer uncompress(final ByteBuffer inBuffer) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // first byte of compressed stream gives order
        final RANSParams.ORDER order = RANSParams.ORDER.fromInt(inBuffer.get());

        // For RANS decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // compressed bytes length
        final int inSize = inBuffer.getInt();
        if (inSize != inBuffer.remaining() - RAW_BYTE_LENGTH) {
            throw new RuntimeException("Incorrect input length.");
        }

        // uncompressed bytes length
        final int outSize = inBuffer.getInt();
        final ByteBuffer outBuffer = ByteBuffer.allocate(outSize);
        initializeRANSDecoder();
        switch (order) {
            case ZERO:
                return uncompressOrder0Way4(inBuffer, outBuffer);

            case ONE:
                return uncompressOrder1Way4(inBuffer, outBuffer);

            default:
                throw new RuntimeException("Unknown rANS order: " + order);
        }
    }

    private ByteBuffer uncompressOrder0Way4(final ByteBuffer inBuffer, final ByteBuffer outBuffer) {
        // read the frequency table. using the frequency table, set the values of RANSDecodingSymbols
        readStatsOrder0(inBuffer);

        final ArithmeticDecoder D = getD()[0];
        final RANSDecodingSymbol[] syms = getDecodingSymbols()[0];

        long rans0, rans1, rans2, rans3;
        rans0 = inBuffer.getInt();
        rans1 = inBuffer.getInt();
        rans2 = inBuffer.getInt();
        rans3 = inBuffer.getInt();

        final int out_sz = outBuffer.remaining();
        final int out_end = (out_sz & ~3);
        for (int i = 0; i < out_end; i += 4) {
            final byte c0 = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
            final byte c1 = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
            final byte c2 = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans2, Constants.TOTAL_FREQ_SHIFT)];
            final byte c3 = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans3, Constants.TOTAL_FREQ_SHIFT)];

            outBuffer.put(i, c0);
            outBuffer.put(i + 1, c1);
            outBuffer.put(i + 2, c2);
            outBuffer.put(i + 3, c3);

            rans0 = syms[0xFF & c0].advanceSymbolStep(rans0, Constants.TOTAL_FREQ_SHIFT);
            rans1 = syms[0xFF & c1].advanceSymbolStep(rans1, Constants.TOTAL_FREQ_SHIFT);
            rans2 = syms[0xFF & c2].advanceSymbolStep(rans2, Constants.TOTAL_FREQ_SHIFT);
            rans3 = syms[0xFF & c3].advanceSymbolStep(rans3,  Constants.TOTAL_FREQ_SHIFT);

            rans0 = Utils.RANSDecodeRenormalize4x8(rans0, inBuffer);
            rans1 = Utils.RANSDecodeRenormalize4x8(rans1, inBuffer);
            rans2 = Utils.RANSDecodeRenormalize4x8(rans2, inBuffer);
            rans3 = Utils.RANSDecodeRenormalize4x8(rans3, inBuffer);
        }

        outBuffer.position(out_end);
        byte c;
        switch (out_sz & 3) {
            case 0:
                break;

            case 1:
                c = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
                syms[0xFF & c].advanceSymbol4x8(rans0, inBuffer, Constants.TOTAL_FREQ_SHIFT);
                outBuffer.put(c);
                break;

            case 2:
                c = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
                syms[0xFF & c].advanceSymbol4x8(rans0, inBuffer, Constants.TOTAL_FREQ_SHIFT);
                outBuffer.put(c);

                c = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
                syms[0xFF & c].advanceSymbol4x8(rans1, inBuffer, Constants.TOTAL_FREQ_SHIFT);
                outBuffer.put(c);
                break;

            case 3:
                c = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
                syms[0xFF & c].advanceSymbol4x8(rans0, inBuffer,  Constants.TOTAL_FREQ_SHIFT);
                outBuffer.put(c);

                c = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
                syms[0xFF & c].advanceSymbol4x8(rans1, inBuffer, Constants.TOTAL_FREQ_SHIFT);
                outBuffer.put(c);

                c = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans2, Constants.TOTAL_FREQ_SHIFT)];
                syms[0xFF & c].advanceSymbol4x8(rans2, inBuffer, Constants.TOTAL_FREQ_SHIFT);
                outBuffer.put(c);
                break;
        }

        outBuffer.position(0);
        return outBuffer;
    }

    private ByteBuffer uncompressOrder1Way4(final ByteBuffer inBuffer, final ByteBuffer outBuffer) {
        // read the frequency table. using the frequency table, set the values of RANSDecodingSymbols
        readStatsOrder1(inBuffer);

        final ArithmeticDecoder[] D = getD();
        final RANSDecodingSymbol[][] syms = getDecodingSymbols();
        final int out_sz = outBuffer.remaining();
        long rans0, rans1, rans2, rans7;
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        rans0 = inBuffer.getInt();
        rans1 = inBuffer.getInt();
        rans2 = inBuffer.getInt();
        rans7 = inBuffer.getInt();

        final int isz4 = out_sz >> 2;
        int i0 = 0;
        int i1 = isz4;
        int i2 = 2 * isz4;
        int i7 = 3 * isz4;
        byte l0 = 0;
        byte l1 = 0;
        byte l2 = 0;
        byte l7 = 0;
        for (; i0 < isz4; i0++, i1++, i2++, i7++) {
            final byte c0 = D[0xFF & l0].reverseLookup[Utils.RANSGetCumulativeFrequency(rans0, Constants.TOTAL_FREQ_SHIFT)];
            final byte c1 = D[0xFF & l1].reverseLookup[Utils.RANSGetCumulativeFrequency(rans1, Constants.TOTAL_FREQ_SHIFT)];
            final byte c2 = D[0xFF & l2].reverseLookup[Utils.RANSGetCumulativeFrequency(rans2, Constants.TOTAL_FREQ_SHIFT)];
            final byte c7 = D[0xFF & l7].reverseLookup[Utils.RANSGetCumulativeFrequency(rans7, Constants.TOTAL_FREQ_SHIFT)];

            outBuffer.put(i0, c0);
            outBuffer.put(i1, c1);
            outBuffer.put(i2, c2);
            outBuffer.put(i7, c7);

            rans0 = syms[0xFF & l0][0xFF & c0].advanceSymbolStep(rans0,  Constants.TOTAL_FREQ_SHIFT);
            rans1 = syms[0xFF & l1][0xFF & c1].advanceSymbolStep(rans1, Constants.TOTAL_FREQ_SHIFT);
            rans2 = syms[0xFF & l2][0xFF & c2].advanceSymbolStep(rans2, Constants.TOTAL_FREQ_SHIFT);
            rans7 = syms[0xFF & l7][0xFF & c7].advanceSymbolStep(rans7,  Constants.TOTAL_FREQ_SHIFT);

            rans0 = Utils.RANSDecodeRenormalize4x8(rans0, inBuffer);
            rans1 = Utils.RANSDecodeRenormalize4x8(rans1, inBuffer);
            rans2 = Utils.RANSDecodeRenormalize4x8(rans2, inBuffer);
            rans7 = Utils.RANSDecodeRenormalize4x8(rans7, inBuffer);

            l0 = c0;
            l1 = c1;
            l2 = c2;
            l7 = c7;
        }

        // Remainder
        for (; i7 < out_sz; i7++) {
            final byte c7 = D[0xFF & l7].reverseLookup[Utils.RANSGetCumulativeFrequency(rans7, Constants.TOTAL_FREQ_SHIFT)];
            outBuffer.put(i7, c7);
            rans7 = syms[0xFF & l7][0xFF & c7].advanceSymbol4x8(rans7, inBuffer, Constants.TOTAL_FREQ_SHIFT);
            l7 = c7;
        }
        return outBuffer;
    }

    private void readStatsOrder0(final ByteBuffer cp) {
        // Pre-compute reverse lookup of frequency.
        final ArithmeticDecoder decoder = getD()[0];
        final RANSDecodingSymbol[] decodingSymbols = getDecodingSymbols()[0];
        int rle = 0;
        int x = 0;
        int j = cp.get() & 0xFF;
        do {
            if ((decoder.freq[j] = (cp.get() & 0xFF)) >= 0x80) {
                decoder.freq[j] &= ~0x80;
                decoder.freq[j] = ((decoder.freq[j] & 0x7F) << 8) | (cp.get() & 0xFF);
            }
            decoder.cumulativeFreq[j] = x;

            decodingSymbols[j].set(decoder.cumulativeFreq[j], decoder.freq[j]);

            /* Build reverse lookup table */
            Arrays.fill(decoder.reverseLookup, x, x + decoder.freq[j], (byte) j);

            x += decoder.freq[j];

            if (rle == 0 && j + 1 == (0xFF & cp.get(cp.position()))) {
                j = cp.get() & 0xFF;
                rle = cp.get() & 0xFF;
            } else if (rle != 0) {
                rle--;
                j++;
            } else {
                j = cp.get() & 0xFF;
            }
        } while (j != 0);

        assert (x <= Constants.TOTAL_FREQ);
    }

    private void readStatsOrder1(final ByteBuffer cp) {
        final ArithmeticDecoder[] D = getD();
        final RANSDecodingSymbol[][] decodingSymbols = getDecodingSymbols();
        int rle_i = 0;
        int i = 0xFF & cp.get();
        do {
            int rle_j = 0;
            int x = 0;
            int j = 0xFF & cp.get();
            do {
                if ((D[i].freq[j] = (0xFF & cp.get())) >= 0x80) {
                    D[i].freq[j] &= ~0x80;
                    D[i].freq[j] = ((D[i].freq[j] & 0x7F) << 8) | (0xFF & cp.get());
                }
                D[i].cumulativeFreq[j] = x;

                if (D[i].freq[j] == 0) {
                    D[i].freq[j] = Constants.TOTAL_FREQ;
                }

                decodingSymbols[i][j].set(
                        D[i].cumulativeFreq[j],
                        D[i].freq[j]
                );

                /* Build reverse lookup table */
                Arrays.fill(D[i].reverseLookup, x, x + D[i].freq[j], (byte) j);

                x += D[i].freq[j];
                assert (x <= Constants.TOTAL_FREQ);

                if (rle_j == 0 && j + 1 == (0xFF & cp.get(cp.position()))) {
                    j = (0xFF & cp.get());
                    rle_j = (0xFF & cp.get());
                } else if (rle_j != 0) {
                    rle_j--;
                    j++;
                } else {
                    j = (0xFF & cp.get());
                }
            } while (j != 0);

            if (rle_i == 0 && i + 1 == (0xFF & cp.get(cp.position()))) {
                i = (0xFF & cp.get());
                rle_i = (0xFF & cp.get());
            } else if (rle_i != 0) {
                rle_i--;
                i++;
            } else {
                i = (0xFF & cp.get());
            }
        } while (i != 0);
    }

}