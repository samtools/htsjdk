package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FrequenciesNx16 {

    static int[] readAlphabet(final ByteBuffer cp){
        // gets the list of alphabets whose frequency!=0
        final int[] A = new int[Constants.NUMBER_OF_SYMBOLS];
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            A[i]=0;
        }
        int rle = 0;
        int sym = cp.get() & 0xFF;
        int last_sym = sym;
        do {
            A[sym] = 1;
            if (rle!=0) {
                rle--;
                sym++;
            } else {
                sym = cp.get() & 0xFF;
                if (sym == last_sym+1)
                    rle = cp.get() & 0xFF;
            }
            last_sym = sym;
        } while (sym != 0);
        return A;
    }

    static void readStatsOrder0(
            final ByteBuffer cp,
            ArithmeticDecoder decoder,
            RANSDecodingSymbol[] decodingSymbols) {
        // Use the Frequency table to set the values of F, C and R
        final int[] A = readAlphabet(cp);
        int x = 0;
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];

        // read F, normalise F then calculate C and R
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (A[j] > 0) {
                if ((F[j] = (cp.get() & 0xFF)) >= 128){
                    F[j] &= ~128;
                    F[j] = (( F[j] &0x7f) << 7) | (cp.get() & 0x7F);
                }
            }
        }
        normaliseFrequenciesOrder0(F,12);
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if(A[j]>0){

                // decoder.fc[j].F -> Frequency
                // decoder.fc[j].C -> Cumulative Frequency preceding the current symbol
                decoder.fc[j].F = F[j];
                decoder.fc[j].C = x;
                decodingSymbols[j].set(decoder.fc[j].C, decoder.fc[j].F);

                // R -> Reverse Lookup table
                Arrays.fill(decoder.R, x, x + decoder.fc[j].F, (byte) j);
                x += decoder.fc[j].F;
            }
        }
    }

    static int[] buildFrequenciesOrder0(final ByteBuffer inBuffer) {
        // Returns an array of raw symbol frequencies
        final int inSize = inBuffer.remaining();
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        for (int i = 0; i < inSize; i++) {
            F[0xFF & inBuffer.get()]++;
        }
        return F;
    }

    static int[] normaliseFrequenciesOrder0(final int[] F, final int bits) {
        // Returns an array of normalised Frequencies,
        // such that the frequencies add up to 1<<bits.
        int T = 0;

        // compute total Frequency
        for (int freq : F) {
            T += freq;
        }

        // Scale total of frequencies to max
        final int renormFreq = 1 << bits;
        final long tr = ((long) (renormFreq) << 31) / T + (1 << 30) / T;

        // keep track of the symbol that has the maximum frequency
        // in the input Frequency array.
        // This symbol's frequency might be altered at the end to make sure
        // that the total normalized frequencies add up to "renormFreq" value.
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

            // using tr to normalize symbol frequencies such that their total = renormFreq
            if ((F[j] = (int) ((F[j] * tr) >> 31)) == 0) {

                // A non-zero symbol frequency should not be incorrectly set to 0.
                // If the calculated value is 0, change it to 1
                F[j] = 1;
            }
            fsum += F[j];
        }

        // adjust the frequency of the symbol "M" such that
        // the sum of frequencies of all the symbols = renormFreq
        if (fsum < renormFreq) {
            F[M] += renormFreq - fsum;
        } else if (fsum > renormFreq){
            F[M] -= fsum - renormFreq;
        }
        return F;
    }

    static RANSEncodingSymbol[] buildSymsOrder0(final int[] F, final RANSEncodingSymbol[] syms) {
        // updates the RANSEncodingSymbol array for all the symbols
        final int[] C = new int[Constants.NUMBER_OF_SYMBOLS];

        // T = running sum of frequencies including the current symbol
        // F[j] = frequency of symbol "j"
        // C[j] = cumulative frequency of all the symbols preceding "j" (excluding the frequency of symbol "j")
        int T = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            C[j] = T;
            T += F[j];
            if (F[j] != 0) {

                //For each symbol, set start = cumulative frequency and freq = frequency
                syms[j].set(C[j], F[j], Constants.TF_SHIFT);
            }
        }
        return syms;
    }

    static void writeAlphabet(final ByteBuffer cp, final int[] F) {
        // Uses Run Length Encoding to write all the symbols whose frequency!=0
        int rle = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
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
                        for (rle = j + 1; rle < Constants.NUMBER_OF_SYMBOLS && F[rle] != 0; rle++);
                        rle -= j + 1;
                        cp.put((byte) rle);
                    }
                }
            }
        }

        // write 0 indicating the end of alphabet
        cp.put((byte) 0);
    }

    static int writeFrequenciesOrder0(final ByteBuffer cp, final int[] F) {
        // Order 0 frequencies store the complete alphabet of observed
        // symbols using run length encoding, followed by a table of frequencies
        // for each symbol in the alphabet.
        final int start = cp.position();

        // write the alphabet first and then their frequencies
        writeAlphabet(cp,F);
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
                if (F[j] < 128) {
                    cp.put((byte) (F[j] & 0x7f));
                } else {

                    // if F[j] >127, it is written in 2 bytes
                    // right shift by 7 and get the most Significant Bits.
                    // Set the Most Significant Bit of the first byte to 1 indicating that the frequency comprises of 2 bytes
                    cp.put((byte) (128 | (F[j] >> 7)));
                    cp.put((byte) (F[j] & 0x7f)); //Least Significant 7 Bits
                }
            }
        }
        return cp.position() - start;
    }

}