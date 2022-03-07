package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.ArithmeticDecoder;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.RANSDecodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RANSNx16Decode extends RANSDecode<RANSNx16Params>{
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer uncompress(final ByteBuffer inBuffer,  final RANSNx16Params params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // For RANS decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        initializeRANSDecoder();

        // the first byte of compressed stream gives the formatFlags
        final int formatFlags = inBuffer.get();
        params.setFormatFlags(formatFlags);
        int n_out = params.getnOut();
        final RANSParams.ORDER order = params.getOrder(); // Order-0 or Order-1 entropy coding
        final boolean x32 = params.getX32(); // Interleave N = 32 rANS states (else N = 4)
        final boolean stripe = params.getStripe(); //multiway interleaving of byte streams
        final boolean nosz = params.getNosz(); // original size is not recorded
        final boolean cat = params.getCAT(); // Data is uncompressed
        final boolean rle = params.getRLE(); // Run length encoding, with runs and literals encoded separately
        final boolean pack = params.getPack(); // Pack 2, 4, 8 or infinite symbols per byte

        // TODO: add methods to handle various flags

        // N-way interleaving. If the NWay flag is set, use 32 way interleaving, else use 4 way
        final int Nway = (x32) ? 32 : 4;

        // if nosz is set, then uncompressed size is not recorded.
        if (!nosz) {
            n_out = Utils.readUint7(inBuffer);
        }
        ByteBuffer outBuffer = ByteBuffer.allocate(n_out);

        // If CAT is set then, the input is uncompressed
        if (cat){
            byte[] data = new byte[n_out];
            outBuffer = inBuffer.get( data,0, n_out);
        }
        else {
            switch (order){
                case ZERO:
                    outBuffer = uncompressOrder0WayN(inBuffer,outBuffer, n_out,Nway);
                    break;
//                case ONE:
//                    uncompressOrder1WayN(inBuffer,n_out, Nway);
//                    break;
                default:
                    throw new RuntimeException("Unknown rANS order: " + order);
            }
        }
        return outBuffer;
    }

    private ByteBuffer uncompressOrder0WayN(final ByteBuffer inBuffer, final ByteBuffer outBuffer,final int n_out,final int Nway) {
        // read the frequency table, get the normalised frequencies and use it to set the RANSDecodingSymbols
        readStatsOrder0(inBuffer);
        // uncompress using Nway rans states
        D0N.uncompress(inBuffer, getD()[0], getDecodingSymbols()[0], outBuffer,n_out,Nway);
        return outBuffer;
    }

    private void readStatsOrder0(
            final ByteBuffer cp) {
        final ArithmeticDecoder decoder = getD()[0];
        final RANSDecodingSymbol[] decodingSymbols = getDecodingSymbols()[0];
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
        Utils.normaliseFrequenciesOrder0(F,12);
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

    private static int[] readAlphabet(final ByteBuffer cp){
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

}