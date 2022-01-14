package htsjdk.samtools.cram.compression.rans.rans4x8;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSEncode;
import htsjdk.samtools.cram.compression.rans.RANSEncodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.Utils;
import htsjdk.utils.ValidationUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANS4x8Encode extends RANSEncode<RANS4x8Params> {

    // streams smaller than this value don't have sufficient symbol context for ORDER-1 encoding,
    // so always use ORDER-0
    private static final int MINIMUM_ORDER_1_SIZE = 4;
    private static final ByteBuffer EMPTY_BUFFER = CompressionUtils.allocateByteBuffer(0);

    // This method assumes that inBuffer is already rewound.
    // It compresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the compressed data.
    public ByteBuffer compress(final ByteBuffer inBuffer, final RANS4x8Params params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        initializeRANSEncoder();
        if (inBuffer.remaining() < MINIMUM_ORDER_1_SIZE) {
            // ORDER-1 encoding of less than 4 bytes is not permitted, so just use ORDER-0
            return compressOrder0Way4(inBuffer);
        }
        final RANSParams.ORDER order= params.getOrder();
        switch (order) {
            case ZERO:
                return compressOrder0Way4(inBuffer);

            case ONE:
                return compressOrder1Way4(inBuffer);

            default:
                throw new CRAMException("Unknown rANS order: " + params.getOrder());
        }
    }

    private ByteBuffer compressOrder0Way4(final ByteBuffer inBuffer) {
        final int inputSize = inBuffer.remaining();
        final ByteBuffer outBuffer = CompressionUtils.allocateOutputBuffer(inputSize);

        // move the output buffer ahead to the start of the frequency table (we'll come back and
        // write the output stream prefix at the end of this method)
        outBuffer.position(Constants.RANS_4x8_PREFIX_BYTE_LENGTH); // start of frequency table

        // get the normalised frequencies of the alphabets
        final int[] normalizedFreq = calcFrequenciesOrder0(inBuffer);

        // using the normalised frequencies, set the RANSEncodingSymbols
        buildSymsOrder0(normalizedFreq);
        final ByteBuffer cp = CompressionUtils.slice(outBuffer);

        // write Frequency table
        final int frequencyTableSize = writeFrequenciesOrder0(cp, normalizedFreq);

        inBuffer.rewind();

        final RANSEncodingSymbol[] syms = getEncodingSymbols()[0];
        final int in_size = inBuffer.remaining();
        long rans0, rans1, rans2, rans3;
        final ByteBuffer ptr = CompressionUtils.slice(cp);
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
            final byte c3 = inBuffer.get(i - 1);
            final byte c2 = inBuffer.get(i - 2);
            final byte c1 = inBuffer.get(i - 3);
            final byte c0 = inBuffer.get(i - 4);

            rans3 = syms[0xFF & c3].putSymbol4x8(rans3, ptr);
            rans2 = syms[0xFF & c2].putSymbol4x8(rans2, ptr);
            rans1 = syms[0xFF & c1].putSymbol4x8(rans1, ptr);
            rans0 = syms[0xFF & c0].putSymbol4x8(rans0, ptr);
        }

        ptr.order(ByteOrder.BIG_ENDIAN);
        ptr.putInt((int) rans3);
        ptr.putInt((int) rans2);
        ptr.putInt((int) rans1);
        ptr.putInt((int) rans0);
        ptr.flip();
        final int cdata_size = ptr.limit();
        // reverse the compressed bytes, so that they become in REVERSE order:
        Utils.reverse(ptr);
        inBuffer.position(inBuffer.limit());

        // write the prefix at the beginning of the output buffer
        writeCompressionPrefix(RANSParams.ORDER.ZERO, outBuffer, inputSize, frequencyTableSize, cdata_size);
        return outBuffer;
    }

    private ByteBuffer compressOrder1Way4(final ByteBuffer inBuffer) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer outBuffer = CompressionUtils.allocateOutputBuffer(inSize);

        // move to start of frequency
        outBuffer.position(Constants.RANS_4x8_PREFIX_BYTE_LENGTH);

        // get normalized frequencies
        final int[][] normalizedFreq = calcFrequenciesOrder1(inBuffer);

        // using the normalised frequencies, set the RANSEncodingSymbols
        buildSymsOrder1(normalizedFreq);

        final ByteBuffer cp = CompressionUtils.slice(outBuffer);
        final int frequencyTableSize = writeFrequenciesOrder1(cp, normalizedFreq);
        inBuffer.rewind();
        final int in_size = inBuffer.remaining();
        long rans0, rans1, rans2, rans3;
        rans0 = Constants.RANS_4x8_LOWER_BOUND;
        rans1 = Constants.RANS_4x8_LOWER_BOUND;
        rans2 = Constants.RANS_4x8_LOWER_BOUND;
        rans3 = Constants.RANS_4x8_LOWER_BOUND;

        final int isz4 = in_size >> 2;
        int i0 = isz4 - 2;
        int i1 = 2 * isz4 - 2;
        int i2 = 3 * isz4 - 2;
        int i3 = 4 * isz4 - 2;

        byte l0 = 0;
        if (i0 + 1 >= 0) {
            l0 = inBuffer.get(i0 + 1);
        }
        byte l1 = 0;
        if (i1 + 1 >= 0) {
            l1 = inBuffer.get(i1 + 1);
        }
        byte l2 = 0;
        if (i2 + 1 >= 0) {
            l2 = inBuffer.get(i2 + 1);
        }

        // Deal with the remainder
        byte l3 = inBuffer.get(in_size - 1);

        // Slicing is needed for buffer reversing later
        final ByteBuffer ptr = CompressionUtils.slice(cp);
        final RANSEncodingSymbol[][] syms = getEncodingSymbols();
        for (i3 = in_size - 2; i3 > 4 * isz4 - 2 && i3 >= 0; i3--) {
            final byte c3 = inBuffer.get(i3);
            rans3 = syms[0xFF & c3][0xFF & l3].putSymbol4x8(rans3, ptr);
            l3 = c3;
        }

        for (; i0 >= 0; i0--, i1--, i2--, i3--) {
            final byte c0 = inBuffer.get(i0);
            final byte c1 = inBuffer.get(i1);
            final byte c2 = inBuffer.get(i2);
            final byte c3 = inBuffer.get(i3);

            rans3 = syms[0xFF & c3][0xFF & l3].putSymbol4x8(rans3, ptr);
            rans2 = syms[0xFF & c2][0xFF & l2].putSymbol4x8(rans2, ptr);
            rans1 = syms[0xFF & c1][0xFF & l1].putSymbol4x8(rans1, ptr);
            rans0 = syms[0xFF & c0][0xFF & l0].putSymbol4x8(rans0, ptr);

            l0 = c0;
            l1 = c1;
            l2 = c2;
            l3 = c3;
        }

        rans3 = syms[0][0xFF & l3].putSymbol4x8(rans3, ptr);
        rans2 = syms[0][0xFF & l2].putSymbol4x8(rans2, ptr);
        rans1 = syms[0][0xFF & l1].putSymbol4x8(rans1, ptr);
        rans0 = syms[0][0xFF & l0].putSymbol4x8(rans0, ptr);

        ptr.order(ByteOrder.BIG_ENDIAN);
        ptr.putInt((int) rans3);
        ptr.putInt((int) rans2);
        ptr.putInt((int) rans1);
        ptr.putInt((int) rans0);
        ptr.flip();
        final int compressedBlobSize = ptr.limit();
        Utils.reverse(ptr);
        /*
         * Depletion of the in buffer cannot be confirmed because of the get(int
         * position) method use during encoding, hence enforcing:
         */
        inBuffer.position(inBuffer.limit());

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
        outBuffer.limit(Constants.RANS_4x8_PREFIX_BYTE_LENGTH + frequencyTableSize + compressedBlobSize);

        // go back to the beginning of the stream and write the prefix values
        // write the (ORDER as a single byte at offset 0)
        outBuffer.put(0, (byte) (order == RANSParams.ORDER.ZERO ? 0 : 1));
        // move past the ORDER and write the compressed size
        outBuffer.putInt(Constants.RANS_4x8_ORDER_BYTE_LENGTH, frequencyTableSize + compressedBlobSize);
        // move past the compressed size and write the uncompressed size
        outBuffer.putInt(Constants.RANS_4x8_ORDER_BYTE_LENGTH + Constants.RANS_4x8_COMPRESSED_BYTE_LENGTH, inSize);
        outBuffer.rewind();
    }

    private static int[] calcFrequenciesOrder0(final ByteBuffer inBuffer) {
        // TODO: remove duplicate code -use Utils.normalise here
        final int T = inBuffer.remaining();

        // Compute statistics
        // T = total of true counts = inBuffer size
        // F = scaled integer frequencies
        // M = sum(fs)
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        for (int i = 0; i < T; i++) {
            F[0xFF & inBuffer.get()]++;
        }

        // Normalise so T == TOTFREQ
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

        final long tr = ((long) Constants.TOTAL_FREQ << 31) / T + (1 << 30) / T;
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

        // Commenting the below line as it is incrementing fsum by 1, which does not make sense
        // and it also makes total normalised frequency = 4095 and not 4096.
        // fsum++;

        // adjust the frequency of the symbol with maximum frequency to make sure that
        // the sum of frequencies of all the symbols = 4096
        if (fsum < Constants.TOTAL_FREQ) {
            F[M] += Constants.TOTAL_FREQ - fsum;
        } else {
            F[M] -= fsum - Constants.TOTAL_FREQ;
        }
        return F;
    }

    private static int[][] calcFrequenciesOrder1(final ByteBuffer in) {
        final int in_size = in.remaining();

        final int[][] F = new int[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        final int[] T = new int[Constants.NUMBER_OF_SYMBOLS];
        int last_i = 0;
        for (int i = 0; i < in_size; i++) {
            int c = 0xFF & in.get();
            F[last_i][c]++;
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

            // Commenting the below line as it is incrementing t2 by 1, which does not make sense
            // and it also makes total normalised frequency = 4095 and not 4096.
            // t2++;

            if (t2 < Constants.TOTAL_FREQ) {
                F[i][M] += Constants.TOTAL_FREQ - t2;
            } else {
                F[i][M] -= t2 - Constants.TOTAL_FREQ;
            }
        }

        return F;
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
                        for (rle = j + 1; rle < Constants.NUMBER_OF_SYMBOLS && F[rle] != 0; rle++)
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
                    for (rle_i = i + 1; rle_i < Constants.NUMBER_OF_SYMBOLS && T[rle_i] != 0; rle_i++)
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
                            for (rle_j = j + 1; rle_j < Constants.NUMBER_OF_SYMBOLS && F_i_[rle_j] != 0; rle_j++)
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