package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;
import java.util.Arrays;

// T = total of true counts
// F = scaled integer frequencies
// M = sum(fs)

final class Frequencies {

    static void readStatsOrder0(final ByteBuffer cp, final ArithmeticDecoder decoder, final RANSDecodingSymbol[] decodingSymbols) {
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

    static void readStatsOrder1(final ByteBuffer cp, final ArithmeticDecoder[] D, final RANSDecodingSymbol[][] decodingSymbols) {
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

    static int[] calcFrequenciesOrder0(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();

        // Compute statistics
        final int[] F = new int[RANS.NUMBER_OF_SYMBOLS];
        int T = 0;
        for (int i = 0; i < inSize; i++) {
            F[0xFF & inBuffer.get()]++;
            T++;
        }
        final long tr = ((long) Constants.TOTFREQ << 31) / T + (1 << 30) / T;

        // Normalise so T[i] == TOTFREQ
        int m = 0;
        int M = 0;  // frequency denominator ?
        for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
            if (m < F[j]) {
                m = F[j];
                M = j;
            }
        }

        int fsum = 0;
        for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] == 0) {
                continue;
            }
            if ((F[j] = (int) ((F[j] * tr) >> 31)) == 0) {
                F[j] = 1;
            }
            fsum += F[j];
        }

        fsum++;
        if (fsum < Constants.TOTFREQ) {
            F[M] += Constants.TOTFREQ - fsum;
        } else {
            F[M] -= fsum - Constants.TOTFREQ;
        }

        assert (F[M] > 0);
        return F;
    }

    static int[][] calcFrequenciesOrder1(final ByteBuffer in) {
        final int in_size = in.remaining();

        final int[][] F = new int[RANS.NUMBER_OF_SYMBOLS][RANS.NUMBER_OF_SYMBOLS];
        final int[] T = new int[RANS.NUMBER_OF_SYMBOLS];
        int c;

        int last_i = 0;
        for (int i = 0; i < in_size; i++) {
            F[last_i][c = (0xFF & in.get())]++;
            T[last_i]++;
            last_i = c;
        }
        F[0][0xFF & in.get((in_size >> 2))]++;
        F[0][0xFF & in.get(2 * (in_size >> 2))]++;
        F[0][0xFF & in.get(3 * (in_size >> 2))]++;
        T[0] += 3;

        for (int i = 0; i < RANS.NUMBER_OF_SYMBOLS; i++) {
            if (T[i] == 0) {
                continue;
            }

            final double p = ((double) Constants.TOTFREQ) / T[i];
            int t2 = 0, m = 0, M = 0;
            for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
                if (F[i][j] == 0)
                    continue;

                if (m < F[i][j]) {
                    m = F[i][j];
                    M = j;
                }

                if ((F[i][j] *= p) == 0)
                    F[i][j] = 1;
                t2 += F[i][j];
            }

            t2++;
            if (t2 < Constants.TOTFREQ) {
                F[i][M] += Constants.TOTFREQ - t2;
            } else {
                F[i][M] -= t2 - Constants.TOTFREQ;
            }
        }

        return F;
    }

    static RANSEncodingSymbol[] buildSymsOrder0(final int[] F, final RANSEncodingSymbol[] syms) {
        final int[] C = new int[RANS.NUMBER_OF_SYMBOLS];

        int T = 0;
        for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
            C[j] = T;
            T += F[j];
            if (F[j] != 0) {
                syms[j].set(C[j], F[j], Constants.TF_SHIFT);
            }
        }
        return syms;
    }

    static int writeFrequenciesOrder0(final ByteBuffer cp, final int[] F) {
        final int start = cp.position();

        int rle = 0;
        for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
                // j
                if (rle != 0) {
                    rle--;
                } else {
                    cp.put((byte) j);
                    if (rle == 0 && j != 0 && F[j - 1] != 0) {
                        for (rle = j + 1; rle < 256 && F[rle] != 0; rle++)
                            ;
                        rle -= j + 1;
                        cp.put((byte) rle);
                    }
                }

                // F[j]
                if (F[j] < 128) {
                    cp.put((byte) (F[j]));
                } else {
                    cp.put((byte) (128 | (F[j] >> 8)));
                    cp.put((byte) (F[j] & 0xff));
                }
            }
        }

        cp.put((byte) 0);
        return cp.position() - start;
    }

    static RANSEncodingSymbol[][] buildSymsOrder1(final int[][] F, final RANSEncodingSymbol[][] syms) {
        for (int i = 0; i < RANS.NUMBER_OF_SYMBOLS; i++) {
            final int[] F_i_ = F[i];
            int x = 0;
            for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
                if (F_i_[j] != 0) {
                    syms[i][j].set(x, F_i_[j], Constants.TF_SHIFT);
                    x += F_i_[j];
                }
            }
        }

        return syms;
    }

    static int writeFrequenciesOrder1(final ByteBuffer cp, final int[][] F) {
        final int start = cp.position();
        final int[] T = new int[RANS.NUMBER_OF_SYMBOLS];

        for (int i = 0; i < RANS.NUMBER_OF_SYMBOLS; i++) {
            for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
                T[i] += F[i][j];
            }
        }

        int rle_i = 0;
        for (int i = 0; i < RANS.NUMBER_OF_SYMBOLS; i++) {
            if (T[i] == 0) {
                continue;
            }

            // Store frequency table
            // i
            if (rle_i != 0) {
                rle_i--;
            } else {
                cp.put((byte) i);
                // FIXME: could use order-0 statistics to observe which alphabet
                // symbols are present and base RLE on that ordering instead.
                if (i != 0 && T[i - 1] != 0) {
                    for (rle_i = i + 1; rle_i < 256 && T[rle_i] != 0; rle_i++)
                        ;
                    rle_i -= i + 1;
                    cp.put((byte) rle_i);
                }
            }

            final int[] F_i_ = F[i];
            int rle_j = 0;
            for (int j = 0; j < RANS.NUMBER_OF_SYMBOLS; j++) {
                if (F_i_[j] != 0) {

                    // j
                    if (rle_j != 0) {
                        rle_j--;
                    } else {
                        cp.put((byte) j);
                        if (rle_j == 0 && j != 0 && F_i_[j - 1] != 0) {
                            for (rle_j = j + 1; rle_j < 256 && F_i_[rle_j] != 0; rle_j++)
                                ;
                            rle_j -= j + 1;
                            cp.put((byte) rle_j);
                        }
                    }

                    // F_i_[j]
                    if (F_i_[j] < 128) {
                        cp.put((byte) F_i_[j]);
                    } else {
                        cp.put((byte) (128 | (F_i_[j] >> 8)));
                        cp.put((byte) (F_i_[j] & 0xff));
                    }
                }
            }
            cp.put((byte) 0);
        }
        cp.put((byte) 0);

        return cp.position() - start;
    }

}
