package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSEncode;
import htsjdk.samtools.cram.compression.rans.RANSEncodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;

public class RANSNx16Encode extends RANSEncode<RANSNx16Params> {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int MINIMUM__ORDER_1_SIZE = 4;

    public ByteBuffer compress(final ByteBuffer inBuffer, final RANSNx16Params params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        final ByteBuffer outBuffer = allocateOutputBuffer(inBuffer.remaining());
        final int formatFlags = params.getFormatFlags();
        outBuffer.put((byte) (formatFlags)); // one byte for formatFlags
        final RANSParams.ORDER order = params.getOrder(); // Order-0 or Order-1 entropy coding
        final boolean x32 = params.getX32(); // Interleave N = 32 rANS states (else N = 4)
        final boolean stripe = params.getStripe(); //multiway interleaving of byte streams
        final boolean nosz = params.getNosz(); // original size is not recorded
        final boolean cat = params.getCAT(); // Data is uncompressed
        final boolean rle = params.getRLE(); // Run length encoding, with runs and literals encoded separately
        final boolean pack = params.getPack(); // Pack 2, 4, 8 or infinite symbols per byte

        // TODO: add methods to handle various flags

        // N-way interleaving
        final int Nway = (x32) ? 32 : 4;

        //stripe size
        final int N = formatFlags>>8;

        if (!nosz) {
            int insize = inBuffer.remaining();
            Utils.writeUint7(insize,outBuffer);
        }
        initializeRANSEncoder();
        if (cat) {
            outBuffer.put(inBuffer);
            return outBuffer;
        }

        if (inBuffer.remaining() < MINIMUM__ORDER_1_SIZE) {
            // TODO: check if this still applies for Nx16 or if there is a different limit
            // ORDER-1 encoding of less than 4 bytes is not permitted, so just use ORDER-0
            return compressOrder0WayN(inBuffer, Nway, outBuffer);
        }

        switch (order) {
            case ZERO:
                return compressOrder0WayN(inBuffer, Nway, outBuffer);
//            case ONE:
//                return compressOrder1WayN(inBuffer, Nway, outBuffer);
            default:
                throw new RuntimeException("Unknown rANS order: " + order);
        }
    }

    private ByteBuffer compressOrder0WayN(final ByteBuffer inBuffer, final int Nway, final ByteBuffer outBuffer) {
        final int inSize = inBuffer.remaining();
        final int[] F = buildFrequenciesOrder0(inBuffer);
        final ByteBuffer cp = outBuffer.slice();
        int bitSize = (int) Math.ceil(Math.log(inSize) / Math.log(2));
        if (bitSize == 0) {
            // TODO: check this!
            // If there is just one symbol, bitsize = log (1)/log(2) = 0.
            bitSize = 1;
        }
        if (bitSize > 12) {
            bitSize = 12;
        }
        final int prefix_size = outBuffer.position();

        // Normalize Frequencies such that sum of Frequencies = 1 << bitsize
        Utils.normaliseFrequenciesOrder0(F, bitSize);

        // Write the Frequency table. Keep track of the size for later
        final int frequencyTableSize = writeFrequenciesOrder0(cp, F);

        // Normalize Frequencies such that sum of Frequencies = 1 << 12
        Utils.normaliseFrequenciesOrder0(F, 12);

        // update the RANS Encoding Symbols
        buildSymsOrder0(F);
        inBuffer.rewind();
        final int compressedBlobSize = E0N.compress(inBuffer, getEncodingSymbols()[0], cp, Nway);
        outBuffer.rewind(); // set position to 0
        outBuffer.limit(prefix_size + frequencyTableSize + compressedBlobSize);
        return outBuffer;
    }

    private static int[] buildFrequenciesOrder0(final ByteBuffer inBuffer) {
        // Returns an array of raw symbol frequencies
        final int inSize = inBuffer.remaining();
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        for (int i = 0; i < inSize; i++) {
            F[0xFF & inBuffer.get()]++;
        }
        return F;
    }

    private static int writeFrequenciesOrder0(final ByteBuffer cp, final int[] F) {
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

    private static void writeAlphabet(final ByteBuffer cp, final int[] F) {
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

    private RANSEncodingSymbol[] buildSymsOrder0(final int[] F) {
        final RANSEncodingSymbol[] syms = getEncodingSymbols()[0];
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

}