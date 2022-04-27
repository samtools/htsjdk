package htsjdk.samtools.cram.compression.rans.rans4x8;

import htsjdk.samtools.cram.compression.rans.ArithmeticDecoder;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.RANSDecodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;

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
        D04.uncompress(inBuffer, getD()[0], getDecodingSymbols()[0], outBuffer);
        return outBuffer;
    }

    private ByteBuffer uncompressOrder1Way4(final ByteBuffer in, final ByteBuffer outBuffer) {
        // read the frequency table. using the frequency table, set the values of RANSDecodingSymbols
        readStatsOrder1(in);
        D14.uncompress(in, outBuffer, getD(), getDecodingSymbols());
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

        assert (x < Constants.TOTAL_FREQ);
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