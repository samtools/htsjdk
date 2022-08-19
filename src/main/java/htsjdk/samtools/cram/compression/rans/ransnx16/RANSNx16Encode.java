package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSEncode;
import htsjdk.samtools.cram.compression.rans.RANSEncodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
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
        final int formatFlags = ransNx16Params.getFormatFlags() & 0xFF;
        outBuffer.put((byte) (formatFlags)); // one byte for formatFlags

        // TODO: add methods to handle various flags

        //  NoSize
        if (!ransNx16Params.getNosz()) {
            // original size is not recorded
            int insize = inBuffer.remaining();
            Utils.writeUint7(insize,outBuffer);
        }

        // using inputBuffer as inBuffer is declared final
        ByteBuffer inputBuffer = inBuffer;

        // TODO: Add Stripe

        // Pack
        if (ransNx16Params.getPack()) {
            final int[] frequencyTable = new int[Constants.NUMBER_OF_SYMBOLS];
            final int inSize = inputBuffer.remaining();
            for (int i = 0; i < inSize; i ++) {
                frequencyTable[inputBuffer.get(i) & 0xFF]++;
            }
            int numSymbols = 0;
            final int[] packMappingTable = new int[Constants.NUMBER_OF_SYMBOLS];
            for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
                if (frequencyTable[i]>0) {
                    packMappingTable[i] = numSymbols++;
                }
            }

            // skip Packing if numSymbols = 0  or numSymbols > 16
            if (numSymbols !=0 & numSymbols <= 16) {
                inputBuffer = encodePack(inputBuffer, outBuffer, frequencyTable, packMappingTable, numSymbols);
            } else {
                // unset pack flag in the first byte of the outBuffer
                outBuffer.put(0,(byte)(outBuffer.get(0) & ~RANSNx16Params.PACK_FLAG_MASK));
            }
        }

        // RLE
        if (ransNx16Params.getRLE()){
            inputBuffer = encodeRLE(inputBuffer, ransNx16Params, outBuffer);
        }


        if (ransNx16Params.getCAT()) {
            // Data is uncompressed
            outBuffer.put(inputBuffer);
            outBuffer.limit(outBuffer.position());
            outBuffer.rewind(); // set position to 0
            return outBuffer;
        }

        // if after encoding pack and rle, the inputBuffer size < 4, then use order 0
        if (inputBuffer.remaining() < MINIMUM__ORDER_1_SIZE && ransNx16Params.getOrder() == RANSParams.ORDER.ONE) {

            // set order flag to "0" in the first byte of the outBuffer
            outBuffer.put(0,(byte)(outBuffer.get(0) & ~RANSNx16Params.ORDER_FLAG_MASK));
            if (inputBuffer.remaining() == 0){
                outBuffer.limit(outBuffer.position()); //TODO: check if this is correct
                outBuffer.rewind();
                return outBuffer;

            }
            return compressOrder0WayN(inputBuffer, new RANSNx16Params(outBuffer.get(0)), outBuffer);
        }

        switch (ransNx16Params.getOrder()) {
            case ZERO:
                return compressOrder0WayN(inputBuffer, ransNx16Params, outBuffer);
            case ONE:
                return compressOrder1WayN(inputBuffer, ransNx16Params, outBuffer);
            default:
                throw new RuntimeException("Unknown rANS order: " + ransNx16Params.getOrder());
        }
    }

    private ByteBuffer compressOrder0WayN (
            final ByteBuffer inBuffer,
            final RANSNx16Params ransNx16Params,
            final ByteBuffer outBuffer) {
        initializeRANSEncoder();
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
        final int Nway = ransNx16Params.getNumInterleavedRANSStates();

        final int compressedDataSize;
        final int inputSize = inBuffer.remaining();
        final ByteBuffer ptr = cp.slice();
        final long[] rans = new long[Nway];
        final byte[] symbol = new byte[Nway];
        for (int r=0; r<Nway; r++){

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
        for (int i = (interleaveSize * Nway); i > 0; i -= Nway) {
            for (int r = Nway - 1; r >= 0; r--){

                // encode using Nway parallel rans states. Nway = 4 or 32
                symbol[r] = inBuffer.get(i - (Nway - r));
                rans[r] = ransEncodingSymbols[0xFF & symbol[r]].putSymbolNx16(rans[r], ptr);
            }
        }
        for (int i=Nway-1; i>=0; i--){
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
        initializeRANSEncoder();
        final ByteBuffer cp = outBuffer.slice();
        final int[][] frequencies = buildFrequenciesOrder1(inBuffer, ransNx16Params.getNumInterleavedRANSStates());

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
        final int Nway = ransNx16Params.getNumInterleavedRANSStates();
        final int inputSize = inBuffer.remaining();
        final long[] rans = new long[Nway];
        for (int r=0; r<Nway; r++){

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
        final byte[] symbol = new byte[Nway];
        final byte[] context = new byte[Nway];
        for (int r=0; r<Nway; r++){

            // initialize interleaveStreamIndex
            // interleaveStreamIndex = (index of last element in the interleaved stream - 1) = (interleaveSize - 1) - 1
            interleaveStreamIndex[r] = (r+1)*interleaveSize - 2;

            //intialize symbol
            symbol[r]=0;
            if((interleaveStreamIndex[r]+1 >= 0) & (r!= Nway-1)){
                symbol[r] = inBuffer.get(interleaveStreamIndex[r] + 1);
            }
            if ( r == Nway-1 ){
                symbol[r] = inBuffer.get(inputSize - 1);
            }
        }

        // deal with the reminder
        for (
                interleaveStreamIndex[Nway - 1] = inputSize - 2;
                interleaveStreamIndex[Nway - 1] > Nway * interleaveSize - 2 && interleaveStreamIndex[Nway - 1] >= 0;
                interleaveStreamIndex[Nway - 1]-- ) {
            context[Nway - 1] = inBuffer.get(interleaveStreamIndex[Nway - 1]);
            rans[Nway - 1] = ransEncodingSymbols[0xFF & context[Nway - 1]][0xFF & symbol[Nway - 1]].putSymbolNx16(rans[Nway - 1], ptr);
            symbol[Nway - 1] = context[Nway - 1];
        }

        while (interleaveStreamIndex[0] >= 0) {
            for (int r=0; r<Nway; r++ ){
                context[Nway-1-r] = inBuffer.get(interleaveStreamIndex[Nway-1-r]);
                rans[Nway-1-r] = ransEncodingSymbols[0xFF & context[Nway-1-r]][0xFF & symbol[Nway-1 - r]].putSymbolNx16(rans[Nway-1-r],ptr);
                symbol[Nway-1-r]=context[Nway-1-r];
            }
            for (int r=0; r<Nway; r++ ){
                interleaveStreamIndex[r]--;
            }

        }

        for (int r=0; r<Nway; r++ ){
            rans[Nway -1 - r] = ransEncodingSymbols[0][0xFF & symbol[Nway -1 - r]].putSymbolNx16(rans[Nway-1 - r], ptr);
        }

        ptr.order(ByteOrder.BIG_ENDIAN);
        for (int r=Nway-1; r>=0; r-- ){
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
        byte contextSymbol = 0;
        byte srcSymbol;
        for (int i = 0; i < inputSize; i++) {

            // update the context array
            frequency[Constants.NUMBER_OF_SYMBOLS][0xFF & contextSymbol]++;
            srcSymbol = inBuffer.get(i);
            frequency[0xFF & contextSymbol][0xFF & srcSymbol ]++;
            contextSymbol = srcSymbol;
        }
        frequency[Constants.NUMBER_OF_SYMBOLS][0xFF & contextSymbol]++;

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

    private ByteBuffer encodeRLE(final ByteBuffer inBuffer ,final RANSParams ransParams, final ByteBuffer outBuffer){

        // Find the symbols that benefit from RLE, i.e, the symbols that occur more than 2 times in succession.
        // spec: For symbols that occur many times in succession, we can replace them with a single symbol and a count.
        final int[] rleSymbols = new int[Constants.NUMBER_OF_SYMBOLS];
        int inputSize = inBuffer.remaining();

        int lastSymbol = -1;
        for (int i = 0; i < inputSize; i++) {
            int currentSymbol = inBuffer.get(i)&0xFF;
            rleSymbols[currentSymbol] += (currentSymbol==lastSymbol ? 1:-1);
            lastSymbol = currentSymbol;
        }

        // numRLESymbols is the number of symbols that are run length encoded
        int numRLESymbols = 0;
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (rleSymbols[i]>0) {
                numRLESymbols++;
            }
        }

        if (numRLESymbols==0) {
            // Format cannot cope with zero RLE symbols, so pick one!
            numRLESymbols = 1;
            rleSymbols[0] = 1;
        }

        // create rleMetaData buffer to store rle metadata.
        // This buffer will be compressed using compressOrder0WayN towards the end of this method
        // TODO: How did we come up with this calculation for Buffer size? numRLESymbols+1+inputSize
        ByteBuffer rleMetaData = ByteBuffer.allocate(numRLESymbols+1+inputSize); // rleMetaData

        // write number of symbols that are run length encoded to the outBuffer
        rleMetaData.put((byte) numRLESymbols);

        for (int i=0; i<256; i++){
            if (rleSymbols[i] >0){
                // write the symbols that are run length encoded
                rleMetaData.put((byte) i);
            }

        }

        // Apply RLE
        // encodedData -> input src data without repetition
        ByteBuffer encodedData = ByteBuffer.allocate(inputSize); // rleInBuffer
        int encodedDataIdx = 0; // rleInBufferIndex

        for (int i = 0; i < inputSize; i++) {
            encodedData.put(encodedDataIdx++,inBuffer.get(i));
            if (rleSymbols[inBuffer.get(i)&0xFF]>0) {
                lastSymbol = inBuffer.get(i) & 0xFF;
                int run = 0;

                // calculate the run value for current symbol
                while (i+run+1 < inputSize && (inBuffer.get(i+run+1)& 0xFF)==lastSymbol) {
                    run++;
                }

                // write the run value to metadata
                Utils.writeUint7(run, rleMetaData);

                // go to the next element that is not equal to it's previous element
                i += run;
            }
        }

        encodedData.limit(encodedDataIdx);
        // limit and rewind
        // TODO: check if position of rleMetadata is at the end of the buffer as expected
        rleMetaData.limit(rleMetaData.position());
        rleMetaData.rewind();

        // compress the rleMetaData Buffer
        ByteBuffer compressedRleMetaData = allocateOutputBuffer(rleMetaData.remaining());

        // TODO: Nway? Check other places as well -> How to setInterleaveSize? - can i do it by changing formatflags?
        // // Compress lengths with O0 and literals with O0/O1 ("order" param)
        // TODO: get Nway from ransParams and use N to uncompress

        compressOrder0WayN(rleMetaData, new RANSNx16Params(0x00),compressedRleMetaData);

        // write to compressedRleMetaData to outBuffer
        Utils.writeUint7(rleMetaData.limit()*2, outBuffer);
        Utils.writeUint7(encodedDataIdx, outBuffer);
        Utils.writeUint7(compressedRleMetaData.limit(),outBuffer);

        outBuffer.put(compressedRleMetaData);

        /*
         * Depletion of the inBuffer cannot be confirmed because of the get(int
         * position) method use during encoding, hence enforcing:
         */
        inBuffer.position(inBuffer.limit());
        return encodedData;
    }

    private ByteBuffer encodePack(
            final ByteBuffer inBuffer ,
            final ByteBuffer outBuffer,
            final int[] frequencyTable,
            final int[] packMappingTable,
            final int numSymbols){
        final int inSize = inBuffer.remaining();
        ByteBuffer data;
        if (numSymbols <= 1) {
            data = ByteBuffer.allocate(0);
        } else if (numSymbols <= 2) {

            // 1 bit per value
            int dataSize = (int) Math.ceil((double) inSize/8);
            data = ByteBuffer.allocate(dataSize);
            int j = -1;
            for (int i = 0; i < inSize; i ++) {
                if (i % 8 == 0) {
                    data.put(++j, (byte) 0);
                }
                data.put(j, (byte) (data.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << (i % 8))));
            }
        } else if (numSymbols <= 4) {

            // 2 bits per value
            int dataSize = (int) Math.ceil((double) inSize/4);
            data = ByteBuffer.allocate(dataSize);
            int j = -1;
            for (int i = 0; i < inSize; i ++) {
                if (i % 4 == 0) {
                    data.put(++j, (byte) 0);
                }
                data.put(j, (byte) (data.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << ((i % 4) * 2))));
            }
        } else {

            // 4 bits per value
            int dataSize = (int) Math.ceil((double)inSize/2);
            data = ByteBuffer.allocate(dataSize);
            int j = -1;
            for (int i = 0; i < inSize; i ++) {
                if (i % 2 == 0) {
                    data.put(++j, (byte) 0);
                }
                data.put(j, (byte) (data.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << ((i % 2) * 4))));
            }
        }

        // write numSymbols
        outBuffer.put((byte) numSymbols);

        // write mapping table "packMappingTable" that converts mapped value to original symbol
        for(int i = 0 ; i < Constants.NUMBER_OF_SYMBOLS; i ++) {
            if (frequencyTable[i] > 0) {
                outBuffer.put((byte) i);
            }
        }

        // write the length of data
        Utils.writeUint7(data.limit(), outBuffer);
        return data; // Here position = 0 since we have always accessed the data buffer using index
    }

}