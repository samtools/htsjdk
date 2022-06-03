package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSEncode;
import htsjdk.samtools.cram.compression.rans.RANSEncodingSymbol;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANSNx16Encode extends RANSEncode<RANSNx16Params> {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int MINIMUM__ORDER_1_SIZE = 4;

    public ByteBuffer compress(final ByteBuffer inBuffer, final RANSNx16Params ransNx16Params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        final ByteBuffer outBuffer = allocateOutputBuffer(inBuffer.remaining());
        final int formatFlags = ransNx16Params.getFormatFlags();
        outBuffer.put((byte) (formatFlags)); // one byte for formatFlags

        // TODO: add methods to handle various flags

        if (!ransNx16Params.getNosz()) {
            // original size is not recorded
            int insize = inBuffer.remaining();
            Utils.writeUint7(insize,outBuffer);
        }
        if (ransNx16Params.getCAT()) {
            // Data is uncompressed
            outBuffer.put(inBuffer);
            return outBuffer;
        }

        initializeRANSEncoder();
        if (inBuffer.remaining() < MINIMUM__ORDER_1_SIZE) {
            // TODO: check if this still applies for Nx16 or if there is a different limit
            // ORDER-1 encoding of less than 4 bytes is not permitted, so just use ORDER-0

            // First byte of the compressed output provides the order of RANS.
            // So, it has to be changed to 0x00
            outBuffer.put(0,(byte) 0x00);
            return compressOrder0WayN(inBuffer, new RANSNx16Params(0x00), outBuffer);
        }

        switch (ransNx16Params.getOrder()) {
            case ZERO:
                return compressOrder0WayN(inBuffer, ransNx16Params, outBuffer);
            case ONE:
                return compressOrder1WayN(inBuffer, ransNx16Params, outBuffer);
            default:
                throw new RuntimeException("Unknown rANS order: " + ransNx16Params.getOrder());
        }
    }

    private ByteBuffer compressOrder0WayN (
            final ByteBuffer inBuffer,
            final RANSNx16Params ransNx16Params,
            final ByteBuffer outBuffer) {
        final int inSize = inBuffer.remaining();
        final int[] F = buildFrequenciesOrder0(inBuffer);
        final ByteBuffer cp = outBuffer.slice();
        int bitSize = (int) Math.ceil(Math.log(inSize) / Math.log(2));

        // TODO: Can bitSize be 0 and should we handle it?
        if (bitSize > Constants.TOTAL_FREQ_SHIFT) {
            bitSize = Constants.TOTAL_FREQ_SHIFT;
        }
        final int prefix_size = outBuffer.position();

        // Normalize Frequencies such that sum of Frequencies = 1 << bitsize
        Utils.normaliseFrequenciesOrder0(F, bitSize);

        // Write the Frequency table. Keep track of the size for later
        final int frequencyTableSize = writeFrequenciesOrder0(cp, F);

        // Normalise Frequencies such that sum of Frequencies = 1 << 12
        // Since, Frequencies are already normalised to be a sum of power of 2,
        // for further normalisation, calculate the bit shift that is required to scale the frequencies to (1 << bits)
        if (bitSize != Constants.TOTAL_FREQ_SHIFT) {
            Utils.normaliseFrequenciesOrder0Shift(F, Constants.TOTAL_FREQ_SHIFT);
        }

        // update the RANS Encoding Symbols
        buildSymsOrder0(F);
        inBuffer.rewind();

        //TODO: tmp staging glue
        final RANSEncodingSymbol[] ransEncodingSymbols = getEncodingSymbols()[0];
        final int Nway = ransNx16Params.getInterleaveSize();

        final int compressedDataSize;
        final int inputSize = inBuffer.remaining();
        final ByteBuffer ptr = cp.slice();
        final long[] rans = new long[Nway];
        final int[] symbol = new int[Nway];
        int r;
        for (r=0; r<Nway; r++){

            // initialize rans states
            rans[r] = Constants.RANS_Nx16_LOWER_BOUND;
        }

        // number of remaining elements = inputSize % Nway = inputSize - (interleaveSize * Nway)
        // For Nway = 4, division by 4 is the same as right shift by 2 bits
        // For Nway = 32, division by 32 is the same as right shift by 5 bits
        final int interleaveSize = (Nway == 4) ? (inputSize >> 2) : (inputSize >> 5);
        int remainingSize = inputSize - (interleaveSize * Nway);
        int reverseIndex = 1;

        // encoded in LIFO order
        while (remainingSize>0){

            // encode remaining elements first
            int remainingSymbol =0xFF & inBuffer.get(inputSize - reverseIndex);
            rans[remainingSize - 1] = ransEncodingSymbols[remainingSymbol].putSymbolNx16(rans[remainingSize - 1], ptr);
            remainingSize --;
            reverseIndex ++;
        }
        int i;

        for (i = (interleaveSize * Nway); i > 0; i -= Nway) {
            for (r = Nway - 1; r >= 0; r--){

                // encode using Nway parallel rans states. Nway = 4 or 32
                symbol[r] = 0xFF & inBuffer.get(i - (Nway - r));
                rans[r] = ransEncodingSymbols[symbol[r]].putSymbolNx16(rans[r], ptr);
            }
        }
        for (i=Nway-1; i>=0; i--){
            ptr.putInt((int) rans[i]);
        }
        ptr.position();
        ptr.flip();
        compressedDataSize = ptr.limit();

        // since the data is encoded in reverse order,
        // reverse the compressed bytes, so that it is in correct order when uncompressed.
        Utils.reverse(ptr);
        inBuffer.position(inBuffer.limit());
        outBuffer.rewind(); // set position to 0
        outBuffer.limit(prefix_size + frequencyTableSize + compressedDataSize);
        return outBuffer;
    }

    private ByteBuffer compressOrder1WayN (
            final ByteBuffer inBuffer,
            final RANSNx16Params ransNx16Params,
            final ByteBuffer outBuffer) {
        final ByteBuffer cp = outBuffer.slice();
        final int[][] frequencies = buildFrequenciesOrder1(inBuffer, ransNx16Params.getInterleaveSize());

        // normalise frequencies with a variable shift calculated
        // using the minimum bit size that is needed to represent a frequency context array
        Utils.normaliseFrequenciesOrder1(frequencies, Constants.TOTAL_FREQ_SHIFT);
        final int prefix_size = outBuffer.position();

        // TODO: How is the buffer size calculated? js: 257*257*3+9
        ByteBuffer frequencyTable = allocateOutputBuffer(1);
        ByteBuffer compressedFrequencyTable = allocateOutputBuffer(1);

        // uncompressed frequency table
        final int uncompressedFrequencyTableSize = writeFrequenciesOrder1(frequencyTable,frequencies);
        frequencyTable.limit(uncompressedFrequencyTableSize);
        frequencyTable.rewind();

        // compressed frequency table using RANS Nx16 Order 0
        compressedFrequencyTable = compressOrder0WayN(frequencyTable, new RANSNx16Params(0x00), compressedFrequencyTable);
        frequencyTable.rewind();
        int compressedFrequencyTableSize = compressedFrequencyTable.limit();

        // spec: The order-1 frequency table itself may still be quite large,
        // so is optionally compressed using the order-0 rANSNx16 codec with a fixed 4-way interleaving.
        if (compressedFrequencyTableSize  < uncompressedFrequencyTableSize) {

            // first byte
            cp.put((byte) (1 | Constants.TOTAL_FREQ_SHIFT << 4 ));
            Utils.writeUint7(uncompressedFrequencyTableSize,cp);
            Utils.writeUint7(compressedFrequencyTableSize,cp);

            // write bytes from compressedFrequencyTable to cp
            int i=0;
            while (i<compressedFrequencyTableSize){
                cp.put(compressedFrequencyTable.get());
                i++;
            }
        } else {
            // first byte
            cp.put((byte) (0 | Constants.TOTAL_FREQ_SHIFT << 4 ));
            int i=0;
            while (i<uncompressedFrequencyTableSize){
                cp.put(frequencyTable.get());
                i++;
            }
        }
        int frequencyTableSize = cp.position();

        // normalise frequencies with a constant shift
        Utils.normaliseFrequenciesOrder1Shift(frequencies, Constants.TOTAL_FREQ_SHIFT);

        // set encoding symbol
        buildSymsOrder1(frequencies); // TODO: move into utils

        // uncompress for Nway = 4. then extend Nway to be variable - 4 or 32

        //TODO: tmp staging
        final RANSEncodingSymbol[][] ransEncodingSymbols = getEncodingSymbols();
        final int Nway = ransNx16Params.getInterleaveSize();
        final int inputSize = inBuffer.remaining();
        final long[] rans = new long[Nway];
        int r;
        for (r=0; r<Nway; r++){

            // initialize rans states
            rans[r] = Constants.RANS_Nx16_LOWER_BOUND;
        }

        /*
         * Slicing is needed for buffer reversing later.
         */
        final ByteBuffer ptr = cp.slice();

        // size of each interleaved array = total size / Nway;
        // For Nway = 4, division by 4 is the same as right shift by 2 bits
        // For Nway = 32, division by 32 is the same as right shift by 5 bits
        final int interleaveSize = (Nway == 4) ? inputSize >> 2: inputSize >> 5;
        final int[] interleaveStreamIndex = new int[Nway];
        final int[] symbol = new int[Nway];
        final int[] context = new int[Nway];
        for (r=0; r<Nway; r++){

            // initialize interleaveStreamIndex
            // interleaveStreamIndex = (index of last element in the interleaved stream - 1) = (interleaveSize - 1) - 1
            interleaveStreamIndex[r] = (r+1)*interleaveSize - 2;

            //intialize symbol
            symbol[r]=0;
            if((interleaveStreamIndex[r]+1 >= 0) & (r!= Nway-1)){
                symbol[r] = 0xFF & inBuffer.get(interleaveStreamIndex[r] + 1);
            }
            if ( r == Nway-1 ){
                symbol[r] = 0xFF & inBuffer.get(inputSize - 1);
            }
        }

        // deal with the reminder
        for (
                interleaveStreamIndex[Nway - 1] = inputSize - 2;
                interleaveStreamIndex[Nway - 1] > Nway * interleaveSize - 2 && interleaveStreamIndex[Nway - 1] >= 0;
                interleaveStreamIndex[Nway - 1]-- ) {
            context[Nway - 1] = 0xFF & inBuffer.get(interleaveStreamIndex[Nway - 1]);
            rans[Nway - 1] = ransEncodingSymbols[context[Nway - 1]][symbol[Nway - 1]].putSymbolNx16(rans[Nway - 1], ptr);
            symbol[Nway - 1] = context[Nway - 1];
        }

        while (interleaveStreamIndex[0] >= 0) {
            for (r=0; r<Nway; r++ ){
                context[Nway-1-r] = 0xFF & inBuffer.get(interleaveStreamIndex[Nway-1-r]);
                rans[Nway-1-r] = ransEncodingSymbols[context[Nway-1-r]][symbol[Nway-1 - r]].putSymbolNx16(rans[Nway-1-r],ptr);
                symbol[Nway-1-r]=context[Nway-1-r];
            }
            for (r=0; r<Nway; r++ ){
                interleaveStreamIndex[r]--;
            }

        }

        for (r=0; r<Nway; r++ ){
            rans[Nway -1 - r] = ransEncodingSymbols[0][symbol[Nway -1 - r]].putSymbolNx16(rans[Nway-1 - r], ptr);
        }

        ptr.order(ByteOrder.BIG_ENDIAN);
        for (r=Nway-1; r>=0; r-- ){
            ptr.putInt((int) rans[r]);
        }

        ptr.flip();
        final int compressedBlobSize = ptr.limit();
        Utils.reverse(ptr);
        /*
         * Depletion of the in buffer cannot be confirmed because of the get(int
         * position) method use during encoding, hence enforcing:
         */
        inBuffer.position(inBuffer.limit());

        outBuffer.rewind();
        outBuffer.limit(prefix_size + frequencyTableSize + compressedBlobSize);
        outBuffer.order(ByteOrder.LITTLE_ENDIAN);
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

    private static int[][] buildFrequenciesOrder1(final ByteBuffer inBuffer, final int Nway) {
        // Returns an array of raw symbol frequencies
        final int inputSize = inBuffer.remaining();

        // context is stored in frequency[Constants.NUMBER_OF_SYMBOLS] array
        final int[][] frequency = new int[Constants.NUMBER_OF_SYMBOLS+1][Constants.NUMBER_OF_SYMBOLS];

        // ‘\0’ is the initial context
        int contextSymbol = 0;
        int srcSymbol;
        for (int i = 0; i < inputSize; i++) {

            // update the context array
            frequency[Constants.NUMBER_OF_SYMBOLS][contextSymbol]++;
            srcSymbol = 0xFF & inBuffer.get(i);
            frequency[contextSymbol][srcSymbol ]++;
            contextSymbol = srcSymbol;
        }
        frequency[Constants.NUMBER_OF_SYMBOLS][contextSymbol]++;

        // set ‘\0’ as context for the first byte in the N interleaved streams.
        // the first byte of the first interleaved stream is already accounted for.
        for (int n = 1; n < Nway; n++){
            // For Nway = 4, division by 4 is the same as right shift by 2 bits
            // For Nway = 32, division by 32 is the same as right shift by 5 bits
            int symbol = Nway == 4 ? (0xFF & inBuffer.get((n*(inputSize >> 2)))) : (0xFF & inBuffer.get((n*(inputSize >> 5))));
            frequency[0][symbol]++;
        }
        frequency[Constants.NUMBER_OF_SYMBOLS][0] += Nway-1;
        return frequency;
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

    private static int writeFrequenciesOrder1(final ByteBuffer cp, final int[][] F) {
        final int start = cp.position();

        // writeAlphabet uses rle to write all the symbols whose frequency!=0
        writeAlphabet(cp,F[Constants.NUMBER_OF_SYMBOLS]);

        for (int i=0; i<Constants.NUMBER_OF_SYMBOLS; i++){
            if (F[Constants.NUMBER_OF_SYMBOLS][i]==0){
                continue;
            }

            // for each symbol with non zero frequency, order 0 frequency table (?)
            // many frequencies will be zero where a symbol is present in one context but not in others,
            // all zero frequencies are followed by a run length to omit adjacent zeros.
            int run = 0;
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                if (F[Constants.NUMBER_OF_SYMBOLS][j] == 0) {
                    continue;
                }
                if (run > 0) {
                    run--;
                } else {
                    Utils.writeUint7(F[i][j],cp);
                    if (F[i][j] == 0) {
                        // Count how many more zero-freqs we have
                        for (int k = j+1; k < Constants.NUMBER_OF_SYMBOLS; k++) {
                            if (F[Constants.NUMBER_OF_SYMBOLS][k] == 0) {
                                continue;
                            }
                            if (F[i][k] == 0) {
                                run++;
                            } else {
                                break;
                            }
                        }
                        Utils.writeUint7(run,cp);
                    }
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

    private void buildSymsOrder0(final int[] F) {
        final RANSEncodingSymbol[] syms = getEncodingSymbols()[0];
        // updates the RANSEncodingSymbol array for all the symbols

        // TODO: commented out to suppress spotBugs warning
        //final int[] C = new int[Constants.NUMBER_OF_SYMBOLS];

        // T = running sum of frequencies including the current symbol
        // F[j] = frequency of symbol "j"
        // cumulativeFreq = cumulative frequency of all the symbols preceding "j" (excluding the frequency of symbol "j")
        int cumulativeFreq = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {

                //For each symbol, set start = cumulative frequency and freq = frequency
                syms[j].set(cumulativeFreq, F[j], Constants.TOTAL_FREQ_SHIFT);
                cumulativeFreq += F[j];
            }
        }
    }

    private void buildSymsOrder1(final int[][] F) {
        // TODO: Call buildSymsOrder0 from buildSymsOrder1
        final RANSEncodingSymbol[][] encodingSymbols = getEncodingSymbols();
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            final int[] F_i_ = F[i];
            int cumulativeFreq = 0;
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                if (F_i_[j] != 0) {
                    encodingSymbols[i][j].set(cumulativeFreq, F_i_[j], Constants.TOTAL_FREQ_SHIFT);
                    cumulativeFreq += F_i_[j];
                }
            }
        }
    }

}