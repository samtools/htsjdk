package htsjdk.samtools.cram.compression.rans.ransnx16;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.rans.ArithmeticDecoder;
import htsjdk.samtools.cram.compression.rans.Constants;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.RANSDecodingSymbol;
import htsjdk.samtools.cram.compression.rans.RANSParams;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RANSNx16Decode extends RANSDecode {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int FREQ_TABLE_OPTIONALLY_COMPRESSED_MASK = 0x01;

    public ByteBuffer uncompress(final ByteBuffer inBuffer) {
        return uncompress(inBuffer, 0);
    }

    public ByteBuffer uncompress(final ByteBuffer inBuffer, int outSize) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // For RANS decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // the first byte of compressed stream gives the formatFlags
        final int formatFlags = inBuffer.get() & 0xFF;
        final RANSNx16Params ransNx16Params = new RANSNx16Params(formatFlags);

        // if nosz flag is set, then uncompressed size is not recorded.
        outSize = ransNx16Params.isNosz() ? outSize : Utils.readUint7(inBuffer);

        // if stripe, then decodeStripe
        if (ransNx16Params.isStripe()) {
            return decodeStripe(inBuffer, outSize);
        }

        // if pack, get pack metadata, which will be used later to decode packed data
        int packDataLength = 0;
        int numSymbols = 0;
        int[] packMappingTable = new int[0];
        if (ransNx16Params.isPack()) {
            packDataLength = outSize;
            numSymbols = inBuffer.get() & 0xFF;

            // if (numSymbols > 16 or numSymbols==0), raise exception
            if (numSymbols <= 16 && numSymbols != 0) {
                packMappingTable = new int[numSymbols];
                for (int i = 0; i < numSymbols; i++) {
                    packMappingTable[i] = inBuffer.get() & 0xFF;
                }
                outSize = Utils.readUint7(inBuffer);
            } else {
                throw new CRAMException("Bit Packing is not permitted when number of distinct symbols is greater than 16 or equal to 0. Number of distinct symbols: " + numSymbols);
            }
        }

        // if rle, get rle metadata, which will be used later to decode rle
        final int uncompressedRLEMetaDataLength;
        int uncompressedRLEOutputLength = 0;
        final int[] rleSymbols = new int[Constants.NUMBER_OF_SYMBOLS];
        ByteBuffer uncompressedRLEMetaData = null;
        if (ransNx16Params.isRLE()) {
            uncompressedRLEMetaDataLength = Utils.readUint7(inBuffer);
            uncompressedRLEOutputLength = outSize;
            outSize = Utils.readUint7(inBuffer);
            // TODO: maybe move decodeRLEMeta in-line
            uncompressedRLEMetaData = decodeRLEMeta(inBuffer, ransNx16Params, uncompressedRLEMetaDataLength, rleSymbols);
        }

        ByteBuffer outBuffer = ByteBuffer.allocate(outSize);
        // If CAT is set then, the input is uncompressed
        if (ransNx16Params.isCAT()) {
            byte[] data = new byte[outSize];
            inBuffer.get(data, 0, outSize);
            outBuffer = ByteBuffer.wrap(data);
        } else {
            if (outSize != 0) {
                switch (ransNx16Params.getOrder()) {
                    case ZERO:
                        uncompressOrder0WayN(inBuffer, outBuffer, outSize, ransNx16Params);
                        break;
                    case ONE:
                        uncompressOrder1WayN(inBuffer, outBuffer, outSize, ransNx16Params);
                        break;
                    default:
                        throw new RuntimeException("Unknown rANS order: " + ransNx16Params.getOrder());
                }
            }
        }

        // if rle, then decodeRLE
        if (ransNx16Params.isRLE() && uncompressedRLEMetaData != null) {
            outBuffer = decodeRLE(outBuffer, rleSymbols, uncompressedRLEMetaData, uncompressedRLEOutputLength);
        }

        // if pack, then decodePack
        if (ransNx16Params.isPack() && packMappingTable.length > 0) {
            outBuffer = decodePack(outBuffer, packMappingTable, numSymbols, packDataLength);
        }
        return outBuffer;
    }

    private ByteBuffer uncompressOrder0WayN(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize,
            final RANSNx16Params ransNx16Params) {
        initializeRANSDecoder();

        // read the frequency table, get the normalised frequencies and use it to set the RANSDecodingSymbols
        readFrequencyTableOrder0(inBuffer);

        // uncompress using Nway rans states
        //TODO: remove this temporary variable aliasing/staging
        final ArithmeticDecoder D = getD()[0];
        final RANSDecodingSymbol[] syms = getDecodingSymbols()[0];
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
        return outBuffer;
    }

    private ByteBuffer uncompressOrder1WayN(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize,
            final RANSNx16Params ransNx16Params) {
        initializeRANSDecoder();

        // read the first byte and calculate the bit shift
        final int frequencyTableFirstByte = (inBuffer.get() & 0xFF);
        final int shift = frequencyTableFirstByte >> 4;
        final boolean optionalCompressFlag = ((frequencyTableFirstByte & FREQ_TABLE_OPTIONALLY_COMPRESSED_MASK)!=0);
        ByteBuffer freqTableSource;
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
            ByteBuffer compressedFrequencyTableBuffer = ByteBuffer.wrap(compressedFreqTable);
            compressedFrequencyTableBuffer.order(ByteOrder.LITTLE_ENDIAN);
            uncompressOrder0WayN(compressedFrequencyTableBuffer, freqTableSource, uncompressedLength,new RANSNx16Params(0x00)); // format flags = 0
        }
        else {
            freqTableSource = inBuffer;
        }
        readFrequencyTableOrder1(freqTableSource, shift);
        final ArithmeticDecoder[] D = getD();
        final RANSDecodingSymbol[][] syms = getDecodingSymbols();
        final int outputSize = outBuffer.remaining();
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Nway parallel rans states. Nway = 4 or 32
        final int Nway = ransNx16Params.getNumInterleavedRANSStates();
        final long[] rans = new long[Nway];
        final int[] interleaveStreamIndex = new int[Nway];
        final int[] context = new int[Nway];
        final int[] symbol = new int[Nway];

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
        return outBuffer;
    }

    private void readFrequencyTableOrder0(
            final ByteBuffer cp) {

        // Use the Frequency table to set the values of Frequencies, Cumulative Frequency
        // and Reverse Lookup table

        final ArithmeticDecoder decoder = getD()[0];
        final RANSDecodingSymbol[] decodingSymbols = getDecodingSymbols()[0];
        final int[] alphabet = readAlphabet(cp);
        int cumulativeFrequency = 0;
        final int[] frequencies = new int[Constants.NUMBER_OF_SYMBOLS];

        // read frequencies, normalise frequencies then calculate C and R
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (alphabet[j] > 0) {
                if ((frequencies[j] = (cp.get() & 0xFF)) >= 0x80){
                    frequencies[j] &= ~0x80;
                    frequencies[j] = (( frequencies[j] &0x7f) << 7) | (cp.get() & 0x7F);
                }
            }
        }
        Utils.normaliseFrequenciesOrder0Shift(frequencies,12);
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if(alphabet[j]>0){

                // set RANSDecodingSymbol
                decoder.freq[j] = frequencies[j];
                decoder.cumulativeFreq[j] = cumulativeFrequency;
                decodingSymbols[j].set(decoder.cumulativeFreq[j], decoder.freq[j]);

                // update Reverse Lookup table
                Arrays.fill(decoder.reverseLookup, cumulativeFrequency, cumulativeFrequency + decoder.freq[j], (byte) j);
                cumulativeFrequency += decoder.freq[j];
            }
        }
    }

    private void readFrequencyTableOrder1(
            final ByteBuffer cp,
            int shift) {
        final int[][] frequencies = new int[Constants.NUMBER_OF_SYMBOLS][Constants.NUMBER_OF_SYMBOLS];
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
                            frequencies[i][j] = Utils.readUint7(cp);
                            if (frequencies[i][j] == 0){
                                run = Utils.readUint7(cp);
                            }
                        }
                    }
                }

                // For each symbol, normalise it's order 0 frequency table
                Utils.normaliseFrequenciesOrder0Shift(frequencies[i],shift);
                int cumulativeFreq=0;

                // set decoding symbols
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    D[i].freq[j]=frequencies[i][j];
                    D[i].cumulativeFreq[j]=cumulativeFreq;
                    decodingSymbols[i][j].set(
                            D[i].cumulativeFreq[j],
                            D[i].freq[j]
                    );
                    /* Build reverse lookup table */
                    Arrays.fill(D[i].reverseLookup, cumulativeFreq, cumulativeFreq + D[i].freq[j], (byte) j);
                    cumulativeFreq+=frequencies[i][j];
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
                if (symbol == lastSymbol+1)
                    rle = cp.get() & 0xFF;
            }
            lastSymbol = symbol;
        } while (symbol != 0);
        return alphabet;
    }

    private ByteBuffer decodeRLEMeta(final ByteBuffer inBuffer , final RANSParams ransParams, final int uncompressedRLEMetaDataLength, final int[] rleSymbols) {
        ByteBuffer uncompressedRLEMetaData;
        final int compressedRLEMetaDataLength;
        if ((uncompressedRLEMetaDataLength & 0x01)!=0) {
            byte[] uncompressedRLEMetaDataArray = new byte[(uncompressedRLEMetaDataLength-1)/2];
            inBuffer.get(uncompressedRLEMetaDataArray, 0, (uncompressedRLEMetaDataLength-1)/2);
            uncompressedRLEMetaData = ByteBuffer.wrap(uncompressedRLEMetaDataArray);
        } else {
            compressedRLEMetaDataLength = Utils.readUint7(inBuffer);
            byte[] compressedRLEMetaDataArray = new byte[compressedRLEMetaDataLength];
            inBuffer.get(compressedRLEMetaDataArray,0,compressedRLEMetaDataLength);
            ByteBuffer compressedRLEMetaData = ByteBuffer.wrap(compressedRLEMetaDataArray);
            compressedRLEMetaData.order(ByteOrder.LITTLE_ENDIAN);
            uncompressedRLEMetaData = ByteBuffer.allocate(uncompressedRLEMetaDataLength / 2);
            
            // TODO: get Nway from ransParams and use N to uncompress
            uncompressOrder0WayN(compressedRLEMetaData,uncompressedRLEMetaData, uncompressedRLEMetaDataLength / 2, new RANSNx16Params(0x00)); // N should come from the prev step
        }

        int numRLESymbols = uncompressedRLEMetaData.get() & 0xFF;
        if (numRLESymbols == 0) {
            numRLESymbols = 256;
        }
        for (int i = 0; i< numRLESymbols; i++) {
            rleSymbols[uncompressedRLEMetaData.get() & 0xFF] = 1;
        }
        return uncompressedRLEMetaData;
    }

    private ByteBuffer decodeRLE(ByteBuffer inBuffer , final int[] rleSymbols, final ByteBuffer uncompressedRLEMetaData, int uncompressedRLEOutputLength) {
        ByteBuffer rleOutBuffer = ByteBuffer.allocate(uncompressedRLEOutputLength);
        int j = 0;
        for(int i = 0; j< uncompressedRLEOutputLength; i++){
            byte sym = inBuffer.get(i);
            if (rleSymbols[sym & 0xFF]!=0){
                int run = Utils.readUint7(uncompressedRLEMetaData);
                for (int r=0; r<= run; r++){
                    rleOutBuffer.put(j++, sym);
                }
            }else {
                rleOutBuffer.put(j++, sym);
            }
        }
        inBuffer = rleOutBuffer;
        return inBuffer;
    }

    private ByteBuffer decodePack(ByteBuffer inBuffer, final int[] packMappingTable, int numSymbols, int uncompressedPackOutputLength) {
        ByteBuffer outBufferPack = ByteBuffer.allocate(uncompressedPackOutputLength);
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
        inBuffer = outBufferPack;
        return inBuffer;
    }

    private ByteBuffer decodeStripe(ByteBuffer inBuffer, final int outSize){

        final int numInterleaveStreams = inBuffer.get() & 0xFF;

        // retrieve lengths of compressed interleaved streams
        int[] clen = new int[numInterleaveStreams];
        for ( int j=0; j<numInterleaveStreams; j++ ){
            clen[j] = Utils.readUint7(inBuffer);
        }

        // Decode the compressed interleaved stream
        int[] ulen = new int[numInterleaveStreams];
        ByteBuffer[] T = new ByteBuffer[numInterleaveStreams];

        for ( int j=0; j<numInterleaveStreams; j++){
            ulen[j] = (int) Math.floor(((double) outSize)/numInterleaveStreams);
            if ((outSize % numInterleaveStreams) > j){
                ulen[j]++;
            }

            T[j] = uncompress(inBuffer, ulen[j]);
        }

        // Transpose
        ByteBuffer out = ByteBuffer.allocate(outSize);
        for (int j = 0; j <numInterleaveStreams; j++) {
            for (int i = 0; i < ulen[j]; i++) {
                out.put((i*numInterleaveStreams)+j, T[j].get(i));
            }
        }

        return out;
    }

}