package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.CRAMException;
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
    private static final int RLE_META_OPTIONALLY_COMPRESSED_MASK = 0x01;

    // This method assumes that inBuffer is already rewound.
    // It uncompresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the uncompressed data.
    public ByteBuffer uncompress(final ByteBuffer inBuffer) {

        // For RANS decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return uncompress(inBuffer, 0);
    }

    private ByteBuffer uncompress(final ByteBuffer inBuffer, final int outSize) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // the first byte of compressed stream gives the formatFlags
        final int formatFlags = inBuffer.get() & 0xFF;
        final RANSNx16Params ransNx16Params = new RANSNx16Params(formatFlags);

        // if nosz flag is set, then uncompressed size is not recorded.
        int uncompressedSize = ransNx16Params.isNosz() ? outSize : Utils.readUint7(inBuffer);

        // if stripe, then decodeStripe
        if (ransNx16Params.isStripe()) {
            return decodeStripe(inBuffer, uncompressedSize);
        }

        // if pack, get pack metadata, which will be used later to decode packed data
        int packDataLength = 0;
        int numSymbols = 0;
        int[] packMappingTable = null;
        if (ransNx16Params.isPack()) {
            packDataLength = uncompressedSize;
            numSymbols = inBuffer.get() & 0xFF;

            // if (numSymbols > 16 or numSymbols==0), raise exception
            if (numSymbols <= 16 && numSymbols != 0) {
                packMappingTable = new int[numSymbols];
                for (int i = 0; i < numSymbols; i++) {
                    packMappingTable[i] = inBuffer.get() & 0xFF;
                }
                uncompressedSize = Utils.readUint7(inBuffer);
            } else {
                throw new CRAMException("Bit Packing is not permitted when number of distinct symbols is greater than 16 or equal to 0. " +
                        "Number of distinct symbols: " + numSymbols);
            }
        }

        // if rle, get rle metadata, which will be used later to decode rle
        int uncompressedRLEOutputLength = 0;
        int[] rleSymbols = null;
        ByteBuffer uncompressedRLEMetaData = null;
        if (ransNx16Params.isRLE()) {
            rleSymbols = new int[Constants.NUMBER_OF_SYMBOLS];
            final int uncompressedRLEMetaDataLength = Utils.readUint7(inBuffer);
            uncompressedRLEOutputLength = uncompressedSize;
            uncompressedSize = Utils.readUint7(inBuffer);
            uncompressedRLEMetaData = decodeRLEMeta(inBuffer, uncompressedRLEMetaDataLength, rleSymbols, ransNx16Params);
        }

        ByteBuffer outBuffer;
        // If CAT is set then, the input is uncompressed
        if (ransNx16Params.isCAT()) {
            final byte[] data = new byte[uncompressedSize];
            inBuffer.get(data, 0, uncompressedSize);
            outBuffer = ByteBuffer.wrap(data);
        } else {
            outBuffer = ByteBuffer.allocate(uncompressedSize);
            if (uncompressedSize != 0) {
                switch (ransNx16Params.getOrder()) {
                    case ZERO:
                        uncompressOrder0WayN(inBuffer, outBuffer, uncompressedSize, ransNx16Params);
                        break;
                    case ONE:
                        uncompressOrder1WayN(inBuffer, outBuffer, ransNx16Params);
                        break;
                    default:
                        throw new CRAMException("Unknown rANS order: " + ransNx16Params.getOrder());
                }
            }
        }

        // if rle, then decodeRLE
        if (ransNx16Params.isRLE()) {
            outBuffer = decodeRLE(outBuffer, rleSymbols, uncompressedRLEMetaData, uncompressedRLEOutputLength);
        }

        // if pack, then decodePack
        if (ransNx16Params.isPack()) {
            outBuffer = decodePack(outBuffer, packMappingTable, numSymbols, packDataLength);
        }
        return outBuffer;
    }

    private void uncompressOrder0WayN(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize,
            final RANSNx16Params ransNx16Params) {
        initializeRANSDecoder();

        // read the frequency table, get the normalised frequencies and use it to set the RANSDecodingSymbols
        readFrequencyTableOrder0(inBuffer);

        // uncompress using Nway rans states
        final int Nway = ransNx16Params.getNumInterleavedRANSStates();

        // Nway parallel rans states. Nway = 4 or 32
        final long[] rans = new long[Nway];

        // symbols is the array of decoded symbols
        final byte[] symbols = new byte[Nway];
        for (int r=0; r<Nway; r++){
            rans[r] = inBuffer.getInt();
        }

        // size of each interleaved stream
        // For Nway = 4, division by 4 is the same as right shift by 2 bits
        // For Nway = 32, division by 32 is the same as right shift by 5 bits
        final int interleaveSize = (Nway == 4) ? (outSize >> 2) : (outSize >> 5);

        // Number of elements that don't fall into the Nway streams
        int remSize = outSize - (interleaveSize * Nway);
        final int out_end = outSize - remSize;
        final ArithmeticDecoder D = getD()[0];
        final RANSDecodingSymbol[] syms = getDecodingSymbols()[0];
        for (int i = 0; i < out_end; i += Nway) {
            for (int r=0; r<Nway; r++){

                // Nway parallel decoding rans states
                symbols[r] = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans[r], Constants.TOTAL_FREQ_SHIFT)];
                outBuffer.put(i+r, symbols[r]);
                rans[r] = syms[0xFF & symbols[r]].advanceSymbolStep(rans[r], Constants.TOTAL_FREQ_SHIFT);
                rans[r] = Utils.RANSDecodeRenormalizeNx16(rans[r], inBuffer);
            }
        }
        outBuffer.position(out_end);
        int reverseIndex = 0;

        // decode the remaining bytes
        while (remSize>0){
            byte remainingSymbol = D.reverseLookup[Utils.RANSGetCumulativeFrequency(rans[reverseIndex], Constants.TOTAL_FREQ_SHIFT)];
            syms[0xFF & remainingSymbol].advanceSymbolNx16(rans[reverseIndex], inBuffer, Constants.TOTAL_FREQ_SHIFT);
            outBuffer.put(remainingSymbol);
            remSize --;
            reverseIndex ++;
        }
        outBuffer.rewind();
    }

    private void uncompressOrder1WayN(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final RANSNx16Params ransNx16Params) {

        // read the first byte
        final int frequencyTableFirstByte = (inBuffer.get() & 0xFF);
        final boolean optionalCompressFlag = ((frequencyTableFirstByte & FREQ_TABLE_OPTIONALLY_COMPRESSED_MASK)!=0);
        final ByteBuffer freqTableSource;
        if (optionalCompressFlag) {

            // spec: The order-1 frequency table itself may still be quite large,
            // so is optionally compressed using the order-0 rANSNx16 codec with a fixed 4-way interleaving.

            // if optionalCompressFlag is true, the frequency table was compressed using RANS Nx16, N=4 Order 0
            final int uncompressedLength = Utils.readUint7(inBuffer);
            final int compressedLength = Utils.readUint7(inBuffer);
            byte[] compressedFreqTable = new byte[compressedLength];

            // read compressedLength bytes into compressedFreqTable byte array
            inBuffer.get(compressedFreqTable,0,compressedLength);

            // decode the compressedFreqTable to get the uncompressedFreqTable using RANS Nx16, N=4 Order 0 uncompress
            freqTableSource = ByteBuffer.allocate(uncompressedLength);
            final ByteBuffer compressedFrequencyTableBuffer = ByteBuffer.wrap(compressedFreqTable);
            compressedFrequencyTableBuffer.order(ByteOrder.LITTLE_ENDIAN);

            // uncompress using RANSNx16 Order 0, Nway = 4
            // formatFlags = (~RANSNx16Params.ORDER_FLAG_MASK & ~RANSNx16Params.N32_FLAG_MASK) = ~(RANSNx16Params.ORDER_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK)
            uncompressOrder0WayN(compressedFrequencyTableBuffer, freqTableSource, uncompressedLength,new RANSNx16Params(~(RANSNx16Params.ORDER_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK))); // format flags = 0
        }
        else {
            freqTableSource = inBuffer;
        }

        // Moving initializeRANSDecoder() from the beginning of this method to this point in the code
        // due to the nested call to uncompressOrder0WayN, which also invokes the initializeRANSDecoder() method.
        // TODO: we should work on a more permanent solution for this issue!
        initializeRANSDecoder();
        final int shift = frequencyTableFirstByte >> 4;
        readFrequencyTableOrder1(freqTableSource, shift);
        final int outputSize = outBuffer.remaining();

        // Nway parallel rans states. Nway = 4 or 32
        final int Nway = ransNx16Params.getNumInterleavedRANSStates();
        final long[] rans = new long[Nway];
        final int[] interleaveStreamIndex = new int[Nway];
        final int[] context = new int[Nway];

        // size of interleaved stream = outputSize / Nway
        // For Nway = 4, division by 4 is the same as right shift by 2 bits
        // For Nway = 32, division by 32 is the same as right shift by 5 bits
        final int interleaveSize = (Nway==4) ? (outputSize >> 2): (outputSize >> 5);

        for (int r=0; r<Nway; r++){

            // initialize rans, interleaveStreamIndex and context arrays
            rans[r] = inBuffer.getInt();
            interleaveStreamIndex[r] = r * interleaveSize;
            context[r] = 0;
        }

        final ArithmeticDecoder[] D = getD();
        final RANSDecodingSymbol[][] syms = getDecodingSymbols();
        final int[] symbol = new int[Nway];
        while (interleaveStreamIndex[0] < interleaveSize) {
            for (int r = 0; r < Nway; r++){
                symbol[r] = 0xFF & D[context[r]].reverseLookup[Utils.RANSGetCumulativeFrequency(rans[r], shift)];
                outBuffer.put(interleaveStreamIndex[r],(byte) symbol[r]);
                rans[r] = syms[context[r]][symbol[r]].advanceSymbolStep(rans[r], shift);
                rans[r] = Utils.RANSDecodeRenormalizeNx16(rans[r], inBuffer);
                context[r]=symbol[r];
            }
            for (int r = 0; r < Nway; r++){
                interleaveStreamIndex[r]++;
            }
        }

        // Deal with the remaining elements
        for (; interleaveStreamIndex[Nway - 1] < outputSize; interleaveStreamIndex[Nway - 1]++) {
            symbol[Nway - 1] = 0xFF & D[context[Nway - 1]].reverseLookup[Utils.RANSGetCumulativeFrequency(rans[Nway - 1], shift)];
            outBuffer.put(interleaveStreamIndex[Nway - 1], (byte) symbol[Nway - 1]);
            rans[Nway - 1] = syms[context[Nway - 1]][symbol[Nway - 1]].advanceSymbolNx16(rans[Nway - 1], inBuffer, shift);
            context[Nway - 1] = symbol[Nway - 1];
        }
    }

    private void readFrequencyTableOrder0(
            final ByteBuffer cp) {

        // Use the Frequency table to set the values of Frequencies, Cumulative Frequency
        // and Reverse Lookup table

        final int[] alphabet = readAlphabet(cp);
        final ArithmeticDecoder decoder = getD()[0];

        // read frequencies, normalise frequencies
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (alphabet[j] > 0) {
                if ((decoder.frequencies[j] = (cp.get() & 0xFF)) >= 0x80){
                    decoder.frequencies[j] &= ~0x80;
                    decoder.frequencies[j] = (( decoder.frequencies[j] &0x7f) << 7) | (cp.get() & 0x7F);
                }
            }
        }
        Utils.normaliseFrequenciesOrder0Shift(decoder.frequencies, Constants.TOTAL_FREQ_SHIFT);

        final RANSDecodingSymbol[] decodingSymbols = getDecodingSymbols()[0];
        int cumulativeFrequency = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if(alphabet[j]>0){

                // set RANSDecodingSymbol
                decodingSymbols[j].set(cumulativeFrequency, decoder.frequencies[j]);

                // update Reverse Lookup table
                Arrays.fill(decoder.reverseLookup, cumulativeFrequency, cumulativeFrequency + decoder.frequencies[j], (byte) j);
                cumulativeFrequency += decoder.frequencies[j];
            }
        }
    }

    private void readFrequencyTableOrder1(
            final ByteBuffer cp,
            final int shift) {
        final ArithmeticDecoder[] D = getD();
        final RANSDecodingSymbol[][] decodingSymbols = getDecodingSymbols();
        final int[] alphabet = readAlphabet(cp);

        for (int i=0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (alphabet[i] > 0) {
                int run = 0;
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    if (alphabet[j] > 0) {
                        if (run > 0) {
                            run--;
                        } else {
                            D[i].frequencies[j] = Utils.readUint7(cp);
                            if (D[i].frequencies[j] == 0){
                                run = Utils.readUint7(cp);
                            }
                        }
                    }
                }

                // For each symbol, normalise it's order 0 frequency table
                Utils.normaliseFrequenciesOrder0Shift(D[i].frequencies,shift);
                int cumulativeFreq=0;

                // set decoding symbols
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    decodingSymbols[i][j].set(
                            cumulativeFreq,
                            D[i].frequencies[j]
                    );
                    /* Build reverse lookup table */
                    Arrays.fill(D[i].reverseLookup, cumulativeFreq, cumulativeFreq + D[i].frequencies[j], (byte) j);
                    cumulativeFreq+=D[i].frequencies[j];
                }
            }
        }
    }

    private static int[] readAlphabet(final ByteBuffer cp){
        // gets the list of alphabets whose frequency!=0
        final int[] alphabet = new int[Constants.NUMBER_OF_SYMBOLS];
        int rle = 0;
        int symbol = cp.get() & 0xFF;
        int lastSymbol = symbol;
        do {
            alphabet[symbol] = 1;
            if (rle!=0) {
                rle--;
                symbol++;
            } else {
                symbol = cp.get() & 0xFF;
                if (symbol == lastSymbol+1) {
                    rle = cp.get() & 0xFF;
                }
            }
            lastSymbol = symbol;
        } while (symbol != 0);
        return alphabet;
    }

    private ByteBuffer decodeRLEMeta(
            final ByteBuffer inBuffer,
            final int uncompressedRLEMetaDataLength,
            final int[] rleSymbols,
            final RANSNx16Params ransNx16Params) {
        final ByteBuffer uncompressedRLEMetaData;

        // The bottom bit of uncompressedRLEMetaDataLength is a flag to indicate
        // whether rle metadata is uncompressed (1) or com- pressed (0).
        if ((uncompressedRLEMetaDataLength & RLE_META_OPTIONALLY_COMPRESSED_MASK)!=0) {
            final byte[] uncompressedRLEMetaDataArray = new byte[(uncompressedRLEMetaDataLength-1)/2];
            inBuffer.get(uncompressedRLEMetaDataArray, 0, (uncompressedRLEMetaDataLength-1)/2);
            uncompressedRLEMetaData = ByteBuffer.wrap(uncompressedRLEMetaDataArray);
        } else {
            final int compressedRLEMetaDataLength = Utils.readUint7(inBuffer);
            final byte[] compressedRLEMetaDataArray = new byte[compressedRLEMetaDataLength];
            inBuffer.get(compressedRLEMetaDataArray,0,compressedRLEMetaDataLength);
            final ByteBuffer compressedRLEMetaData = ByteBuffer.wrap(compressedRLEMetaDataArray);
            compressedRLEMetaData.order(ByteOrder.LITTLE_ENDIAN);
            uncompressedRLEMetaData = ByteBuffer.allocate(uncompressedRLEMetaDataLength / 2);
            // uncompress using Order 0 and N = Nway
            uncompressOrder0WayN(
                    compressedRLEMetaData,
                    uncompressedRLEMetaData,
                    uncompressedRLEMetaDataLength / 2,
                    new RANSNx16Params(0x00 | ransNx16Params.getFormatFlags() & RANSNx16Params.N32_FLAG_MASK));
        }

        int numRLESymbols = uncompressedRLEMetaData.get() & 0xFF;
        if (numRLESymbols == 0) {
            numRLESymbols = Constants.NUMBER_OF_SYMBOLS;
        }
        for (int i = 0; i< numRLESymbols; i++) {
            rleSymbols[uncompressedRLEMetaData.get() & 0xFF] = 1;
        }
        return uncompressedRLEMetaData;
    }

    private ByteBuffer decodeRLE(
            final ByteBuffer inBuffer,
            final int[] rleSymbols,
            final ByteBuffer uncompressedRLEMetaData,
            final int uncompressedRLEOutputLength) {
        final ByteBuffer rleOutBuffer = ByteBuffer.allocate(uncompressedRLEOutputLength);
        int j = 0;
        for(int i = 0; j< uncompressedRLEOutputLength; i++){
            final byte sym = inBuffer.get(i);
            if (rleSymbols[sym & 0xFF]!=0){
                final int run = Utils.readUint7(uncompressedRLEMetaData);
                for (int r=0; r<= run; r++){
                    rleOutBuffer.put(j++, sym);
                }
            }else {
                rleOutBuffer.put(j++, sym);
            }
        }
        return rleOutBuffer;
    }

    private ByteBuffer decodePack(
            final ByteBuffer inBuffer,
            final int[] packMappingTable,
            final int numSymbols,
            final int uncompressedPackOutputLength) {
        final ByteBuffer outBufferPack = ByteBuffer.allocate(uncompressedPackOutputLength);
        int j = 0;
        if (numSymbols <= 1) {
            for (int i=0; i < uncompressedPackOutputLength; i++){
                outBufferPack.put(i, (byte) packMappingTable[0]);
            }
        }

        // 1 bit per value
        else if (numSymbols <= 2) {
            int v = 0;
            for (int i=0; i < uncompressedPackOutputLength; i++){
                if (i % 8 == 0){
                    v = inBuffer.get(j++);
                }
                outBufferPack.put(i, (byte) packMappingTable[v & 1]);
                v >>=1;
            }
        }

        // 2 bits per value
        else if (numSymbols <= 4){
            int v = 0;
            for(int i=0; i < uncompressedPackOutputLength; i++){
                if (i % 4 == 0){
                    v = inBuffer.get(j++);
                }
                outBufferPack.put(i, (byte) packMappingTable[v & 3]);
                v >>=2;
            }
        }

        // 4 bits per value
        else if (numSymbols <= 16){
            int v = 0;
            for(int i=0; i < uncompressedPackOutputLength; i++){
                if (i % 2 == 0){
                    v = inBuffer.get(j++);
                }
                outBufferPack.put(i, (byte) packMappingTable[v & 15]);
                v >>=4;
            }
        }
        return outBufferPack;
    }

    private ByteBuffer decodeStripe(final ByteBuffer inBuffer, final int outSize){
        final int numInterleaveStreams = inBuffer.get() & 0xFF;

        // retrieve lengths of compressed interleaved streams
        final int[] compressedLengths = new int[numInterleaveStreams];
        for ( int j=0; j<numInterleaveStreams; j++ ){
            compressedLengths[j] = Utils.readUint7(inBuffer);
        }

        // Decode the compressed interleaved stream
        final int[] uncompressedLengths = new int[numInterleaveStreams];
        final ByteBuffer[] transposedData = new ByteBuffer[numInterleaveStreams];
        for ( int j=0; j<numInterleaveStreams; j++){
            uncompressedLengths[j] = (int) Math.floor(((double) outSize)/numInterleaveStreams);
            if ((outSize % numInterleaveStreams) > j){
                uncompressedLengths[j]++;
            }

            transposedData[j] = uncompress(inBuffer, uncompressedLengths[j]);
        }

        // Transpose
        final ByteBuffer outBuffer = ByteBuffer.allocate(outSize);
        for (int j = 0; j <numInterleaveStreams; j++) {
            for (int i = 0; i < uncompressedLengths[j]; i++) {
                outBuffer.put((i*numInterleaveStreams)+j, transposedData[j].get(i));
            }
        }
        return outBuffer;
    }

}