package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSEncode;
import htsjdk.samtools.cram.compression.rans.RANSEncodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Encoder for the CRAM 3.1 rANSNx16 codec with 16-bit state renormalization (as opposed to the rAns4x8 codec,
 * which uses 8-bit state renormalization), and order-0 or order-1 context. Also supports bit-packing and run length
 * encoding. Does not support striping for write (see the spec).
 *
 * This codec is also used internally by the read name NameTokenisation codec.
 */
public class RANSNx16Encode extends RANSEncode<RANSNx16Params> {
    /////////////////////////////////////////////////////////////////////////////////////////////////
    // Stripe flag is not implemented in the write implementation
    /////////////////////////////////////////////////////////////////////////////////////////////////

    private static final ByteBuffer EMPTY_BUFFER = CompressionUtils.allocateByteBuffer(0);

    // This method assumes that inBuffer is already rewound.
    // It compresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the compressed data.
    public ByteBuffer compress(final ByteBuffer inBuffer, final RANSNx16Params ransNx16Params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        final ByteBuffer outBuffer = CompressionUtils.allocateOutputBuffer(inBuffer.remaining());
        final int formatFlags = ransNx16Params.getFormatFlags();
        outBuffer.put((byte) (formatFlags)); // one byte for formatFlags

        //  NoSize
        if (!ransNx16Params.isNosz()) {
            // original size is not recorded
            CompressionUtils.writeUint7(inBuffer.remaining(),outBuffer);
        }

        ByteBuffer inputBuffer = inBuffer;

        // Stripe
        // Stripe flag is not implemented in the write implementation
        if (ransNx16Params.isStripe()) {
            throw new CRAMException("RANSNx16 Encoding with Stripe Flag is not implemented.");
        }

        // Pack
        if (ransNx16Params.isPack()) {
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
            if (numSymbols !=0 && numSymbols <= 16) {
                inputBuffer = CompressionUtils.encodePack(inputBuffer, outBuffer, frequencyTable, packMappingTable, numSymbols);
            } else {
                // unset pack flag in the first byte of the outBuffer
                outBuffer.put(0,(byte)(outBuffer.get(0) & ~RANSNx16Params.PACK_FLAG_MASK));
            }
        }

        // RLE
        if (ransNx16Params.isRLE()){
            inputBuffer = encodeRLE(inputBuffer, outBuffer, ransNx16Params);
        }

        if (ransNx16Params.isCAT()) {
            // Data is uncompressed
            outBuffer.put(inputBuffer);
            outBuffer.limit(outBuffer.position());
            outBuffer.rewind(); // set position to 0
            return outBuffer;
        }

        // if after encoding pack and rle, the inputBuffer size < Nway, then use order 0
        if (inputBuffer.remaining() < ransNx16Params.getNumInterleavedRANSStates() && ransNx16Params.getOrder() == RANSParams.ORDER.ONE) {

            // set order flag to "0" in the first byte of the outBuffer
            outBuffer.put(0,(byte)(outBuffer.get(0) & ~RANSNx16Params.ORDER_FLAG_MASK));
            if (inputBuffer.remaining() == 0){
                outBuffer.limit(outBuffer.position());
                outBuffer.rewind();
                return outBuffer;
            }
            compressOrder0WayN(inputBuffer, new RANSNx16Params(outBuffer.get(0)), outBuffer);
            return outBuffer;
        }

        switch (ransNx16Params.getOrder()) {
            case ZERO:
                compressOrder0WayN(inputBuffer, ransNx16Params, outBuffer);
                return outBuffer;
            case ONE:
                compressOrder1WayN(inputBuffer, ransNx16Params, outBuffer);
                return outBuffer;
            default:
                throw new CRAMException("Unknown rANS order: " + ransNx16Params.getOrder());
        }
    }

    private void compressOrder0WayN (
            final ByteBuffer inBuffer,
            final RANSNx16Params ransNx16Params,
            final ByteBuffer outBuffer) {
        initializeRANSEncoder();
        final int inSize = inBuffer.remaining();
        int bitSize = (int) Math.ceil(Math.log(inSize) / Math.log(2));
        if (bitSize > Constants.TOTAL_FREQ_SHIFT) {
            bitSize = Constants.TOTAL_FREQ_SHIFT;
        }
        final int prefix_size = outBuffer.position();
        final int[] F = buildFrequenciesOrder0(inBuffer);
        final ByteBuffer cp = CompressionUtils.slice(outBuffer);

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

        // using the normalised frequencies, set the RANSEncodingSymbols
        buildSymsOrder0(F);
        inBuffer.rewind();
        final int Nway = ransNx16Params.getNumInterleavedRANSStates();

        // number of remaining elements = inputSize % Nway = inputSize - (interleaveSize * Nway)
        // For Nway = 4, division by 4 is the same as right shift by 2 bits
        // For Nway = 32, division by 32 is the same as right shift by 5 bits
        final int inputSize = inBuffer.remaining();
        final int interleaveSize = (Nway == 4) ? (inputSize >> 2) : (inputSize >> 5);
        int remainingSize = inputSize - (interleaveSize * Nway);
        int reverseIndex = 1;
        final long[] rans = new long[Nway];

        // initialize rans states
        for (int r=0; r<Nway; r++){
            rans[r] = Constants.RANS_Nx16_LOWER_BOUND;
        }
        final ByteBuffer ptr = CompressionUtils.slice(cp);
        final RANSEncodingSymbol[] ransEncodingSymbols = getEncodingSymbols()[0];
        // encoded in LIFO order
        while (remainingSize>0){

            // encode remaining elements first
            int remainingSymbol = 0xFF & inBuffer.get(inputSize - reverseIndex);
            rans[remainingSize - 1] = ransEncodingSymbols[remainingSymbol].putSymbolNx16(rans[remainingSize - 1], ptr);
            remainingSize --;
            reverseIndex ++;
        }
        final byte[] symbol = new byte[Nway];
        for (int i = (interleaveSize * Nway); i > 0; i -= Nway) {
            for (int r = Nway - 1; r >= 0; r--){

                // encode using Nway parallel rans states. Nway = 4 or 32
                symbol[r] = inBuffer.get(i - (Nway - r));
                rans[r] = ransEncodingSymbols[0xFF & symbol[r]].putSymbolNx16(rans[r], ptr);
            }
        }

        ptr.order(ByteOrder.BIG_ENDIAN);
        for (int i=Nway-1; i>=0; i--){
            ptr.putInt((int) rans[i]);
        }
        ptr.position();
        ptr.flip();
        final int compressedDataSize = ptr.limit();

        // since the data is encoded in reverse order,
        // reverse the compressed bytes, so that it is in correct order when uncompressed.
        Utils.reverse(ptr);
        inBuffer.position(inBuffer.limit());
        outBuffer.rewind(); // set position to 0
        outBuffer.limit(prefix_size + frequencyTableSize + compressedDataSize);
    }

    private void compressOrder1WayN (
            final ByteBuffer inBuffer,
            final RANSNx16Params ransNx16Params,
            final ByteBuffer outBuffer) {
        final int[][] frequencies = buildFrequenciesOrder1(inBuffer, ransNx16Params.getNumInterleavedRANSStates());

        // normalise frequencies with a variable shift calculated
        // using the minimum bit size that is needed to represent a frequency context array
        Utils.normaliseFrequenciesOrder1(frequencies, Constants.TOTAL_FREQ_SHIFT);
        final int prefix_size = outBuffer.position();

        ByteBuffer frequencyTable = CompressionUtils.allocateOutputBuffer(1);
        final ByteBuffer compressedFrequencyTable = CompressionUtils.allocateOutputBuffer(1);

        // uncompressed frequency table
        final int uncompressedFrequencyTableSize = writeFrequenciesOrder1(frequencyTable,frequencies);
        frequencyTable.limit(uncompressedFrequencyTableSize);
        frequencyTable.rewind();

        // Compress using RANSNx16 Order 0, Nway = 4.
        // formatFlags = (~RANSNx16Params.ORDER_FLAG_MASK & ~RANSNx16Params.N32_FLAG_MASK) = ~(RANSNx16Params.ORDER_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK)
        compressOrder0WayN(frequencyTable, new RANSNx16Params(~(RANSNx16Params.ORDER_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK)), compressedFrequencyTable);
        frequencyTable.rewind();

        // Moving initializeRANSEncoder() from the beginning of this method to this point in the code
        // due to the nested call to compressOrder0WayN, which also invokes the initializeRANSEncoder() method.
        // TODO: we should work on a more permanent solution for this issue!
        initializeRANSEncoder();
        final int compressedFrequencyTableSize = compressedFrequencyTable.limit();
        final ByteBuffer cp = CompressionUtils.slice(outBuffer);

        // spec: The order-1 frequency table itself may still be quite large,
        // so is optionally compressed using the order-0 rANSNx16 codec with a fixed 4-way interleaving.
        if (compressedFrequencyTableSize  < uncompressedFrequencyTableSize) {

            // first byte
            cp.put((byte) (1 | Constants.TOTAL_FREQ_SHIFT << 4 ));
            CompressionUtils.writeUint7(uncompressedFrequencyTableSize,cp);
            CompressionUtils.writeUint7(compressedFrequencyTableSize,cp);

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
        final int frequencyTableSize = cp.position();

        // normalise frequencies with a constant shift
        Utils.normaliseFrequenciesOrder1Shift(frequencies, Constants.TOTAL_FREQ_SHIFT);

        // using the normalised frequencies, set the RANSEncodingSymbols
        buildSymsOrder1(frequencies);

        // uncompress for Nway = 4. then extend Nway to be variable - 4 or 32
        final int Nway = ransNx16Params.getNumInterleavedRANSStates();
        final long[] rans = new long[Nway];

        // initialize rans states
        for (int r=0; r<Nway; r++){
            rans[r] = Constants.RANS_Nx16_LOWER_BOUND;
        }

        // size of each interleaved array = total size / Nway;
        // For Nway = 4, division by 4 is the same as right shift by 2 bits
        // For Nway = 32, division by 32 is the same as right shift by 5 bits
        final int inputSize = inBuffer.remaining();
        final int interleaveSize = (Nway == 4) ? inputSize >> 2: inputSize >> 5;
        final int[] interleaveStreamIndex = new int[Nway];
        final byte[] symbol = new byte[Nway];
        for (int r=0; r<Nway; r++){

            // initialize interleaveStreamIndex
            // interleaveStreamIndex = (index of last element in the interleaved stream - 1) = (interleaveSize - 1) - 1
            interleaveStreamIndex[r] = (r+1)*interleaveSize - 2;

            //intialize symbol
            symbol[r]=0;
            if((interleaveStreamIndex[r]+1 >= 0) && (r!= Nway-1)){
                symbol[r] = inBuffer.get(interleaveStreamIndex[r] + 1);
            }
            if ( r == Nway-1 ){
                symbol[r] = inBuffer.get(inputSize - 1);
            }
        }

        // Slicing is needed for buffer reversing later.
        final ByteBuffer ptr = CompressionUtils.slice(cp);
        final RANSEncodingSymbol[][] ransEncodingSymbols = getEncodingSymbols();
        final byte[] context = new byte[Nway];

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
        for (int i = 0; i < inputSize; i++) {

            // update the context array
            frequency[Constants.NUMBER_OF_SYMBOLS][0xFF & contextSymbol]++;
            final byte srcSymbol = inBuffer.get(i);
            frequency[0xFF & contextSymbol][0xFF & srcSymbol ]++;
            contextSymbol = srcSymbol;
        }
        frequency[Constants.NUMBER_OF_SYMBOLS][0xFF & contextSymbol]++;

        // set ‘\0’ as context for the first byte in the N interleaved streams.
        // the first byte of the first interleaved stream is already accounted for.
        for (int n = 1; n < Nway; n++){
            // For Nway = 4, division by 4 is the same as right shift by 2 bits
            // For Nway = 32, division by 32 is the same as right shift by 5 bits
            final int symbol = Nway == 4 ? (0xFF & inBuffer.get((n*(inputSize >> 2)))) : (0xFF & inBuffer.get((n*(inputSize >> 5))));
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
                    CompressionUtils.writeUint7(F[i][j],cp);
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
                        cp.put((byte) run);
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

    private ByteBuffer encodeRLE(final ByteBuffer inBuffer, final ByteBuffer outBuffer, final RANSNx16Params ransNx16Params){

        // Find the symbols that benefit from RLE, i.e, the symbols that occur more than 2 times in succession.
        // spec: For symbols that occur many times in succession, we can replace them with a single symbol and a count.
        final int[] runCounts = new int[Constants.NUMBER_OF_SYMBOLS];
        int inputSize = inBuffer.remaining();

        int lastSymbol = -1;
        for (int i = 0; i < inputSize; i++) {
            final int currentSymbol = inBuffer.get(i)&0xFF;
            runCounts[currentSymbol] += (currentSymbol==lastSymbol ? 1:-1);
            lastSymbol = currentSymbol;
        }

        // numRLESymbols is the number of symbols that are run length encoded
        int numRLESymbols = 0;
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (runCounts[i]>0) {
                numRLESymbols++;
            }
        }

        if (numRLESymbols==0) {
            // Format cannot cope with zero RLE symbols, so pick one!
            numRLESymbols = 1;
            runCounts[0] = 1;
        }

        // create rleMetaData buffer to store rle metadata.
        // This buffer will be compressed using compressOrder0WayN towards the end of this method
        // TODO: How did we come up with this calculation for Buffer size? numRLESymbols+1+inputSize
        final ByteBuffer rleMetaData = CompressionUtils.allocateByteBuffer(numRLESymbols+1+inputSize); // rleMetaData

        // write number of symbols that are run length encoded
        rleMetaData.put((byte) numRLESymbols);

        for (int i=0; i<Constants.NUMBER_OF_SYMBOLS; i++){
            if (runCounts[i] >0){
                // write the symbols that are run length encoded
                rleMetaData.put((byte) i);
            }

        }

        // Apply RLE
        // encodedBuffer -> input src data without repetition
        final ByteBuffer encodedBuffer = CompressionUtils.allocateByteBuffer(inputSize); // rleInBuffer
        int encodedBufferIdx = 0; // rleInBufferIndex

        for (int i = 0; i < inputSize; i++) {
            encodedBuffer.put(encodedBufferIdx++,inBuffer.get(i));
            if (runCounts[inBuffer.get(i)&0xFF]>0) {
                lastSymbol = inBuffer.get(i) & 0xFF;
                int run = 0;

                // calculate the run value for current symbol
                while (i+run+1 < inputSize && (inBuffer.get(i+run+1)& 0xFF)==lastSymbol) {
                    run++;
                }

                // write the run value to metadata
                CompressionUtils.writeUint7(run, rleMetaData);

                // go to the next element that is not equal to its previous element
                i += run;
            }
        }

        encodedBuffer.limit(encodedBufferIdx);
        // limit and rewind
        rleMetaData.limit(rleMetaData.position());
        rleMetaData.rewind();

        // compress the rleMetaData Buffer
        final ByteBuffer compressedRleMetaData = CompressionUtils.allocateOutputBuffer(rleMetaData.remaining());

        // compress using Order 0 and N = Nway
        compressOrder0WayN(rleMetaData, new RANSNx16Params(0x00 | ransNx16Params.getFormatFlags() & RANSNx16Params.N32_FLAG_MASK),compressedRleMetaData);

        // write to compressedRleMetaData to outBuffer
        CompressionUtils.writeUint7(rleMetaData.limit()*2, outBuffer);
        CompressionUtils.writeUint7(encodedBufferIdx, outBuffer);
        CompressionUtils.writeUint7(compressedRleMetaData.limit(),outBuffer);

        outBuffer.put(compressedRleMetaData);

        /*
         * Depletion of the inBuffer cannot be confirmed because of the get(int
         * position) method use during encoding, hence enforcing:
         */
        inBuffer.position(inBuffer.limit());
        return encodedBuffer;
    }

}