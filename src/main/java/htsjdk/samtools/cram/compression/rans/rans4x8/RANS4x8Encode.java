package htsjdk.samtools.cram.compression.rans.rans4x8;

import htsjdk.samtools.cram.compression.rans.*;
import htsjdk.utils.ValidationUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static htsjdk.samtools.cram.compression.rans.Constants.NUMBER_OF_SYMBOLS;

public class RANS4x8Encode extends RANSEncode<RANS4x8Params> {
    private static final int ORDER_BYTE_LENGTH = 1;
    private static final int COMPRESSED_BYTE_LENGTH = 4;
    private static final int RAW_BYTE_LENGTH = 4;
    private static final int PREFIX_BYTE_LENGTH = ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH + RAW_BYTE_LENGTH;

    // streams smaller than this value don't have sufficient symbol context for ORDER-1 encoding,
    // so always use ORDER-0
    private static final int MINIMUM__ORDER_1_SIZE = 4;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);


    public ByteBuffer compress(final ByteBuffer inBuffer, final RANS4x8Params params) {
        final RANSParams.ORDER order= params.getOrder();
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        initializeRANSEncoder();
        if (inBuffer.remaining() < MINIMUM__ORDER_1_SIZE) {
            // ORDER-1 encoding of less than 4 bytes is not permitted, so just use ORDER-0
            return compressOrder0Way4(inBuffer);
        }
        switch (order) {
            case ZERO:
                return compressOrder0Way4(inBuffer);

            case ONE:
                return compressOrder1Way4(inBuffer);

            default:
                throw new RuntimeException("Unknown rANS order: " + params.getOrder());
        }
    }

    private ByteBuffer compressOrder0Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);

        // move the output buffer ahead to the start of the frequency table (we'll come back and
        // write the output stream prefix at the end of this method)
        outBuffer.position(PREFIX_BYTE_LENGTH); // start of frequency table

        // get the normalised frequencies of the alphabets
        final int[] F = calcFrequenciesOrder0(inBuffer);

        // using the normalised frequencies, set the RANSEncodingSymbols
        buildSymsOrder0(F);

        final ByteBuffer cp = outBuffer.slice();

        // write Frequency table
        final int frequencyTableSize = writeFrequenciesOrder0(cp, F);

        inBuffer.rewind();

        final RANSEncodingSymbol[] syms = getEncodingSymbols()[0];
        final int cdata_size;
        final int in_size = inBuffer.remaining();
        int rans0, rans1, rans2, rans3;
        final ByteBuffer ptr = cp.slice();
        rans0 = Constants.RANS_4x8_LOWER_BOUND;
        rans1 = Constants.RANS_4x8_LOWER_BOUND;
        rans2 = Constants.RANS_4x8_LOWER_BOUND;
        rans3 = Constants.RANS_4x8_LOWER_BOUND;

        int i;
        switch (i = (in_size & 3)) {
            case 3:
                rans2 = syms[0xFF & inBuffer.get(in_size - (i - 2))].putSymbol4x8(rans2, ptr);
            case 2:
                rans1 = syms[0xFF & inBuffer.get(in_size - (i - 1))].putSymbol4x8(rans1, ptr);
            case 1:
                rans0 = syms[0xFF & inBuffer.get(in_size - (i))].putSymbol4x8(rans0, ptr);
            case 0:
                break;
        }
        for (i = (in_size & ~3); i > 0; i -= 4) {
            final int c3 = 0xFF & inBuffer.get(i - 1);
            final int c2 = 0xFF & inBuffer.get(i - 2);
            final int c1 = 0xFF & inBuffer.get(i - 3);
            final int c0 = 0xFF & inBuffer.get(i - 4);

            rans3 = syms[c3].putSymbol4x8(rans3, ptr);
            rans2 = syms[c2].putSymbol4x8(rans2, ptr);
            rans1 = syms[c1].putSymbol4x8(rans1, ptr);
            rans0 = syms[c0].putSymbol4x8(rans0, ptr);
        }

        ptr.putInt(rans3);
        ptr.putInt(rans2);
        ptr.putInt(rans1);
        ptr.putInt(rans0);
        ptr.flip();
        cdata_size = ptr.limit();
        // reverse the compressed bytes, so that they become in REVERSE order:
        Utils.reverse(ptr);
        inBuffer.position(inBuffer.limit());

        // write the prefix at the beginning of the output buffer
        writeCompressionPrefix(RANSParams.ORDER.ZERO, outBuffer, inSize, frequencyTableSize, cdata_size);
        return outBuffer;
    }

    private ByteBuffer compressOrder1Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = allocateOutputBuffer(inSize);

        // move to start of frequency
        outBuffer.position(PREFIX_BYTE_LENGTH);

        // get normalized frequencies
        final int[][] F = calcFrequenciesOrder1(inBuffer);

        // using the normalised frequencies, set the RANSEncodingSymbols
        buildSymsOrder1(F);

        final ByteBuffer cp = outBuffer.slice();
        final int frequencyTableSize = writeFrequenciesOrder1(cp, F);

        inBuffer.rewind();
        final int compressedBlobSize = E14.compress(inBuffer, getEncodingSymbols(), cp);

        // write the prefix at the beginning of the output buffer
        writeCompressionPrefix(RANSParams.ORDER.ONE, outBuffer, inSize, frequencyTableSize, compressedBlobSize);
        return outBuffer;
    }

    private static void writeCompressionPrefix(
            final RANSParams.ORDER order,
            final ByteBuffer outBuffer,
            final int inSize,
            final int frequencyTableSize,
            final int compressedBlobSize) {
        ValidationUtils.validateArg(order == RANSParams.ORDER.ONE || order == RANSParams.ORDER.ZERO,"unrecognized RANS order");
        outBuffer.limit(PREFIX_BYTE_LENGTH + frequencyTableSize + compressedBlobSize);

        // go back to the beginning of the stream and write the prefix values
        // write the (ORDER as a single byte at offset 0)
        outBuffer.put(0, (byte) (order == RANSParams.ORDER.ZERO ? 0 : 1));
        outBuffer.order(ByteOrder.LITTLE_ENDIAN);
        // move past the ORDER and write the compressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH, frequencyTableSize + compressedBlobSize);
        // move past the compressed size and write the uncompressed size
        outBuffer.putInt(ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH, inSize);
        outBuffer.rewind();
    }

    private static int[] calcFrequenciesOrder0(final ByteBuffer inBuffer) {
        // TODO: remove duplicate code -use Utils.normalise here
        final int inSize = inBuffer.remaining();

        // Compute statistics
        // T = total of true counts
        // F = scaled integer frequencies
        // M = sum(fs)
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        int T = 0; //// T is the total number of symbols in the input
        for (int i = 0; i < inSize; i++) {
            F[0xFF & inBuffer.get()]++;
            T++;
        }
        final long tr = ((long) Constants.TOTAL_FREQ << 31) / T + (1 << 30) / T;

        // Normalise so T[i] == TOTFREQ
        // m is the maximum frequency value
        // M is the symbol that has the maximum frequency
        int m = 0;
        int M = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (m < F[j]) {
                m = F[j];
                M = j;
            }
        }

        int fsum = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] == 0) {
                continue;
            }
            // using tr to normalize symbol frequencies such that their total = (1<<12) = 4096
            if ((F[j] = (int) ((F[j] * tr) >> 31)) == 0) {
                // make sure that a non-zero symbol frequency is not incorrectly set to 0.
                // Change it to 1 if the calculated value is 0.
                F[j] = 1;
            }
            fsum += F[j];
        }

        fsum++;
        // adjust the frequency of the symbol with maximum frequency to make sure that
        // the sum of frequencies of all the symbols = 4096
        if (fsum < Constants.TOTAL_FREQ) {
            F[M] += Constants.TOTAL_FREQ - fsum;
        } else {
            F[M] -= fsum - Constants.TOTAL_FREQ;
        }
        assert (F[M] > 0);
        return F;
    }

    private static int[][] calcFrequenciesOrder1(final ByteBuffer in) {
        final int in_size = in.remaining();

        final int[][] F = new int[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        final int[] T = new int[Constants.NUMBER_OF_SYMBOLS];
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

        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (T[i] == 0) {
                continue;
            }

            final double p = ((double) Constants.TOTAL_FREQ) / T[i];
            int t2 = 0, m = 0, M = 0;
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
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
            if (t2 < Constants.TOTAL_FREQ) {
                F[i][M] += Constants.TOTAL_FREQ - t2;
            } else {
                F[i][M] -= t2 - Constants.TOTAL_FREQ;
            }
        }

        return F;
    }

    private void buildSymsOrder0(final int[] F) {
        final RANSEncodingSymbol[] encodingSymbols = getEncodingSymbols()[0];

        // TODO: commented out to suppress spotBugs warning
        //final int[] C = new int[Constants.NUMBER_OF_SYMBOLS];

        // T = running sum of frequencies including the current symbol
        // F[j] = frequency of symbol "j"
        // C[j] = cumulative frequency of all the symbols preceding "j" (and excluding the frequency of symbol "j")
        int cumulativeFreq = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
                //For each symbol, set start = cumulative frequency and freq = frequency
                encodingSymbols[j].set(cumulativeFreq, F[j], Constants.TOTAL_FREQ_SHIFT);
                cumulativeFreq += F[j];
            }
        }
    }

    private void buildSymsOrder1(final int[][] F) {
        final RANSEncodingSymbol[][] encodingSymbols = getEncodingSymbols();
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            final int[] F_i_ = F[i];
            int cumulativeFreq = 0;
            for (int symbol = 0; symbol < Constants.NUMBER_OF_SYMBOLS; symbol++) {
                if (F_i_[symbol] != 0) {
                    encodingSymbols[i][symbol].set(cumulativeFreq, F_i_[symbol], Constants.TOTAL_FREQ_SHIFT);
                    cumulativeFreq += F_i_[symbol];
                }
            }
        }
    }

    private static int writeFrequenciesOrder0(final ByteBuffer cp, final int[] F) {
        final int start = cp.position();

        int rle = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
                // j
                if (rle != 0) {
                    rle--;
                } else {
                    // write the symbol if it is the first symbol or if rle = 0.
                    // if rle != 0, then skip writing the symbol.
                    cp.put((byte) j);
                    // We've encoded two symbol frequencies in a row.
                    // How many more are there?  Store that count so
                    // we can avoid writing consecutive symbols.
                    // Note: maximum possible rle = 254
                    // rle requires atmost 1 byte
                    if (rle == 0 && j != 0 && F[j - 1] != 0) {
                        for (rle = j + 1; rle < NUMBER_OF_SYMBOLS && F[rle] != 0; rle++)
                            ;
                        rle -= j + 1;
                        cp.put((byte) rle);
                    }
                }

                // F[j]
                if (F[j] < 128) {
                    cp.put((byte) (F[j]));
                } else {
                    // if F[j] >127, it is written in 2 bytes
                    cp.put((byte) (128 | (F[j] >> 8)));
                    cp.put((byte) (F[j] & 0xff));
                }
            }
        }

        // write 0 indicating the end of frequency table
        cp.put((byte) 0);
        return cp.position() - start;
    }

    private static int writeFrequenciesOrder1(final ByteBuffer cp, final int[][] F) {
        final int start = cp.position();
        final int[] T = new int[Constants.NUMBER_OF_SYMBOLS];

        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                T[i] += F[i][j];
            }
        }

        int rle_i = 0;
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
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
                    for (rle_i = i + 1; rle_i < NUMBER_OF_SYMBOLS && T[rle_i] != 0; rle_i++)
                        ;
                    rle_i -= i + 1;
                    cp.put((byte) rle_i);
                }
            }

            final int[] F_i_ = F[i];
            int rle_j = 0;
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                if (F_i_[j] != 0) {

                    // j
                    if (rle_j != 0) {
                        rle_j--;
                    } else {
                        cp.put((byte) j);
                        if (rle_j == 0 && j != 0 && F_i_[j - 1] != 0) {
                            for (rle_j = j + 1; rle_j < NUMBER_OF_SYMBOLS && F_i_[rle_j] != 0; rle_j++)
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