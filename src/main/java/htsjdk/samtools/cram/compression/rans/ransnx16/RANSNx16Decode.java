package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.ArithmeticDecoder;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.RANSDecodingSymbol;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RANSNx16Decode extends RANSDecode {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int FREQ_TABLE_OPTIONALLY_COMPRESSED_MASK = 0x01;

    public ByteBuffer uncompress(final ByteBuffer inBuffer) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // For RANS decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // the first byte of compressed stream gives the formatFlags
        final int formatFlags = inBuffer.get();
        final RANSNx16Params ransNx16Params = new RANSNx16Params(formatFlags);

        // TODO: add methods to handle various flags

        // if nosz is set, then uncompressed size is not recorded.
        int n_out = ransNx16Params.getNosz() ? 0 : Utils.readUint7(inBuffer);

        // If CAT is set then, the input is uncompressed
        if (ransNx16Params.getCAT()){
            byte[] data = new byte[n_out];
            inBuffer.get( data,0, n_out);
            return ByteBuffer.wrap(data);
        }
        else {
            initializeRANSDecoder();
            final ByteBuffer outBuffer = ByteBuffer.allocate(n_out);
            switch (ransNx16Params.getOrder()){
                case ZERO:
                    uncompressOrder0WayN(inBuffer, outBuffer, n_out, ransNx16Params);
                    break;
                case ONE:
                    uncompressOrder1WayN(inBuffer, outBuffer, n_out, ransNx16Params);
                    break;
                default:
                    throw new RuntimeException("Unknown rANS order: " + ransNx16Params.getOrder());
            }
            return outBuffer;
        }
    }

    private ByteBuffer uncompressOrder0WayN(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int n_out,
            final RANSNx16Params ransNx16Params) {

        // read the frequency table, get the normalised frequencies and use it to set the RANSDecodingSymbols
        readFrequencyTableOrder0(inBuffer);

        // uncompress using Nway rans states
        //TODO: remove this temporary variable aliasing/staging
        final ArithmeticDecoder D = getD()[0];
        final RANSDecodingSymbol[] syms = getDecodingSymbols()[0];
        final int Nway = ransNx16Params.getInterleaveSize();

        // Nway parallel rans states. Nway = 4 or 32
        final int[] rans = new int[Nway];

        // c is the array of decoded symbols
        final byte[] c = new byte[Nway];
        int r;
        for (r=0; r<Nway; r++){
            rans[r] = inBuffer.getInt();
        }

        // Number of elements that don't fall into the Nway streams
        int remSize = (n_out % Nway);
        final int out_end = n_out - remSize;
        for (int i = 0; i < out_end; i += Nway) {
            for (r=0; r<Nway; r++){

                // Nway parallel decoding rans states
                c[r] = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans[r], Constants.TOTAL_FREQ_SHIFT)];
                outBuffer.put(i+r, c[r]);
                rans[r] = syms[0xFF & c[r]].advanceSymbolStep(rans[r], Constants.TOTAL_FREQ_SHIFT);
                rans[r] = Utils.RANSDecodeRenormalizeNx16(rans[r], inBuffer);
            }
        }
        outBuffer.position(out_end);
        int rev_idx = 0;

        // decode the remaining bytes
        while (remSize>0){
            byte symbol = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans[rev_idx], Constants.TOTAL_FREQ_SHIFT)];
            syms[0xFF & symbol].advanceSymbolNx16(rans[rev_idx], inBuffer, Constants.TOTAL_FREQ_SHIFT);
            outBuffer.put(symbol);
            remSize --;
            rev_idx ++;
        }
        outBuffer.position(0);

        return outBuffer;
    }

    private ByteBuffer uncompressOrder1WayN(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int n_out,
            final RANSNx16Params ransNx16Params) {

        // TODO: does not work as expected. Need to fix!
        // read the first byte and calculate the bit shift
        int frequencyTableFirstByte = (inBuffer.get() & 0xFF);
        int shift = frequencyTableFirstByte >> 4;
        boolean optionalCompressFlag = ((frequencyTableFirstByte & FREQ_TABLE_OPTIONALLY_COMPRESSED_MASK)!=0);
        ByteBuffer freqTableSource;
        if (optionalCompressFlag) {

            // if optionalCompressFlag is true, the frequency table was compressed using RANS Nx16, N=4
            final int uncompressedLength = Utils.readUint7(inBuffer);
            final int compressedLength = Utils.readUint7(inBuffer);
            byte[] compressedFreqTable = new byte[compressedLength];

            // read compressedLength bytes into compressedFreqTable byte array
            inBuffer.get(compressedFreqTable,0,compressedLength);

            // decode the compressedFreqTable to get the uncompressedFreqTable
            freqTableSource = ByteBuffer.allocate(uncompressedLength);
            ByteBuffer compressedFrequencyTableBuffer = ByteBuffer.wrap(compressedFreqTable);
            compressedFrequencyTableBuffer.order(ByteOrder.LITTLE_ENDIAN);
            uncompressOrder0WayN(compressedFrequencyTableBuffer, freqTableSource, uncompressedLength,ransNx16Params);
        }
        else {
            freqTableSource = inBuffer;
        }
        readFrequencyTableOrder1(freqTableSource, shift);
        D1N.uncompress(inBuffer, outBuffer, getD(), getDecodingSymbols(), ransNx16Params);
        return outBuffer;
    }

    private void readFrequencyTableOrder0(
            final ByteBuffer cp) {
        final ArithmeticDecoder decoder = getD()[0];
        final RANSDecodingSymbol[] decodingSymbols = getDecodingSymbols()[0];
        // Use the Frequency table to set the values of F, C and R
        final int[] A = readAlphabet(cp);
        int cumulativeFreq = 0;
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];

        // read F, normalise F then calculate C and R
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (A[j] > 0) {
                if ((F[j] = (cp.get() & 0xFF)) >= 0x80){
                    F[j] &= ~0x80;
                    F[j] = (( F[j] &0x7f) << 7) | (cp.get() & 0x7F);
                }
            }
        }
        Utils.normaliseFrequenciesOrder0(F,12);
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if(A[j]>0){

                // decoder.fc[j].F -> Frequency
                // decoder.fc[j].C -> Cumulative Frequency preceding the current symbol
                decoder.freq[j] = F[j];
                decoder.cumulativeFreq[j] = cumulativeFreq;
                decodingSymbols[j].set(decoder.cumulativeFreq[j], decoder.freq[j]);

                // R -> Reverse Lookup table
                Arrays.fill(decoder.reverseLookup, cumulativeFreq, cumulativeFreq + decoder.freq[j], (byte) j);
                cumulativeFreq += decoder.freq[j];
            }
        }
    }

    private void readFrequencyTableOrder1(
            final ByteBuffer cp,
            int shift) {
        final int[][] F = new int[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        final int[][] C = new int[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
        final ArithmeticDecoder[] D = getD();
        final RANSDecodingSymbol[][] decodingSymbols = getDecodingSymbols();
        final int[] A = readAlphabet(cp);

        for (int i=0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (A[i] > 0) {
                int run = 0;
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    if (A[j] > 0) {
                        if (run > 0) {
                            run--;
                        } else {
                            F[i][j] = Utils.readUint7(cp);
                            if (F[i][j] == 0){
                                run = Utils.readUint7(cp);
                            }
                        }
                    }
                }

                // For each symbol, normalise it's order 0 frequency table
                Utils.normaliseFrequenciesOrder0(F[i],shift);
                int cumulativeFreq=0;

                // set decoding symbols
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    D[i].freq[j]=F[i][j];
                    D[i].cumulativeFreq[j]=cumulativeFreq;
                    decodingSymbols[i][j].set(
                            D[i].cumulativeFreq[j],
                            D[i].freq[j]
                    );
                    /* Build reverse lookup table */
                    Arrays.fill(D[i].reverseLookup, cumulativeFreq, cumulativeFreq + D[i].freq[j], (byte) j);
                    cumulativeFreq+=F[i][j];
                }
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