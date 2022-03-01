package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RANS4x8Decode extends RANSDecode<RANS4x8Params> {

    private static final int RAW_BYTE_LENGTH = 4;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer uncompress(final ByteBuffer inBuffer, final RANS4x8Params params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        initializeRANSDecoder();
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
        readStatsOrder0(inBuffer, getD()[0], getDecodingSymbols()[0]);
        D04.uncompress(inBuffer, getD()[0], getDecodingSymbols()[0], outBuffer);

        return outBuffer;
    }

    private ByteBuffer uncompressOrder1Way4(final ByteBuffer in, final ByteBuffer outBuffer) {
        // read the frequency table. using the frequency table, set the values of RANSDecodingSymbols
        readStatsOrder1(in, getD(), getDecodingSymbols());
        D14.uncompress(in, outBuffer, getD(), getDecodingSymbols());
        return outBuffer;
    }

    private void readStatsOrder0(final ByteBuffer cp, final ArithmeticDecoder decoder, final RANSDecodingSymbol[] decodingSymbols) {
        // Pre-compute reverse lookup of frequency.
        int rle = 0;
        int x = 0;
        int j = cp.get() & 0xFF;
        do {
            if ((decoder.fc[j].F = (cp.get() & 0xFF)) >= 128) {
                decoder.fc[j].F &= ~128;
                decoder.fc[j].F = ((decoder.fc[j].F & 127) << 8) | (cp.get() & 0xFF);
            }
            decoder.fc[j].C = x;

            decodingSymbols[j].set(decoder.fc[j].C, decoder.fc[j].F);

            /* Build reverse lookup table */
            Arrays.fill(decoder.R, x, x + decoder.fc[j].F, (byte) j);

            x += decoder.fc[j].F;

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

        assert (x < Constants.TOTFREQ);
    }

    private void readStatsOrder1(final ByteBuffer cp, final ArithmeticDecoder[] D, final RANSDecodingSymbol[][] decodingSymbols) {
        int rle_i = 0;
        int i = 0xFF & cp.get();
        do {
            int rle_j = 0;
            int x = 0;
            int j = 0xFF & cp.get();
            do {
                if ((D[i].fc[j].F = (0xFF & cp.get())) >= 128) {
                    D[i].fc[j].F &= ~128;
                    D[i].fc[j].F = ((D[i].fc[j].F & 127) << 8) | (0xFF & cp.get());
                }
                D[i].fc[j].C = x;

                if (D[i].fc[j].F == 0) {
                    D[i].fc[j].F = Constants.TOTFREQ;
                }

                decodingSymbols[i][j].set(
                        D[i].fc[j].C,
                        D[i].fc[j].F
                );

                /* Build reverse lookup table */
                Arrays.fill(D[i].R, x, x + D[i].fc[j].F, (byte) j);

                x += D[i].fc[j].F;
                assert (x <= Constants.TOTFREQ);

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