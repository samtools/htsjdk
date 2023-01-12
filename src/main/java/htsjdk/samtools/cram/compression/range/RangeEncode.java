package htsjdk.samtools.cram.compression.range;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.BZIP2ExternalCompressor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class RangeEncode<T extends RangeParams> {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer compress(final ByteBuffer inBuffer, final RangeParams rangeParams) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        ByteBuffer outBuffer = allocateOutputBuffer(inBuffer.remaining());
        outBuffer.order(ByteOrder.BIG_ENDIAN);
        final int formatFlags = rangeParams.getFormatFlags();
        outBuffer.put((byte) (formatFlags));

        if (!rangeParams.isNosz()) {
            // original size is not recorded
            int insize = inBuffer.remaining();
            Utils.writeUint7(insize,outBuffer);
        }

        ByteBuffer inputBuffer = inBuffer;

        // Stripe flag is not implemented in the write implementation
        if (rangeParams.isStripe()) {
            throw new CRAMException("Range Encoding with Stripe Flag is not implemented.");
        }

        final RangeParams.ORDER order = rangeParams.getOrder();
        final int inSize = inputBuffer.remaining(); // e_len -> inSize

        // Pack
        if (rangeParams.isPack()) {
            final int[] frequencyTable = new int[Constants.NUMBER_OF_SYMBOLS];
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
                inputBuffer = encodePack(inputBuffer, outBuffer, frequencyTable, packMappingTable, numSymbols);
            } else {
                // unset pack flag in the first byte of the outBuffer
                outBuffer.put(0,(byte)(outBuffer.get(0) & ~RangeParams.PACK_FLAG_MASK));
            }
        }

        if (rangeParams.isCAT()){

            // Data is uncompressed
            outBuffer.put(inputBuffer);
            outBuffer.limit(outBuffer.position());
            outBuffer.rewind(); // set position to 0
            return outBuffer;
        } else if (rangeParams.isExternalCompression()){
            byte[] rawBytes = new byte[inputBuffer.remaining()];
            inputBuffer.get( rawBytes,inBuffer.position(), inputBuffer.remaining());
            final BZIP2ExternalCompressor compressor = new BZIP2ExternalCompressor();
            final byte [] extCompressedBytes = compressor.compress(rawBytes);
            outBuffer.put(extCompressedBytes);
            outBuffer.limit(outBuffer.position());
            outBuffer.rewind(); // set position to 0
            return outBuffer;
        } else if (rangeParams.isRLE()){
            switch (rangeParams.getOrder()) {
                case ZERO:
                    return compressRLEOrder0(inputBuffer, rangeParams, outBuffer);
                case ONE:
                    return compressRLEOrder1(inputBuffer, rangeParams, outBuffer);
            }
        } else {
            switch (rangeParams.getOrder()) {
                case ZERO:
                    return compressOrder0(inputBuffer, rangeParams, outBuffer);
                case ONE:
                    return compressOrder1(inputBuffer, rangeParams, outBuffer);
            }

        }
        return outBuffer;
    }

    private ByteBuffer compressOrder0 (
            final ByteBuffer inBuffer,
            final RangeParams rangeParams,
            final ByteBuffer outBuffer) {

        int maxSymbol = 0;
        final int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++){
            if(maxSymbol < (inBuffer.get(i) & 0xFF)){
                maxSymbol = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbol++; // TODO: Is this correct? Not what spec states!!

        // TODO: initialize byteModel -> set and reset symbols?
        ByteModel byteModel = new ByteModel(maxSymbol);
        outBuffer.put((byte) maxSymbol);
        RangeCoder rangeCoder = new RangeCoder();
        for (int i = 0; i < inSize; i++){
            byteModel.modelEncode(outBuffer,rangeCoder,inBuffer.get(i)&0xFF);
        }
        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
        return outBuffer;
    }

    private ByteBuffer compressOrder1 (
            final ByteBuffer inBuffer,
            final RangeParams rangeParams,
            final ByteBuffer outBuffer) {
        int maxSymbol = 0;
        final int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++){
            if(maxSymbol < (inBuffer.get(i) & 0xFF)){
                maxSymbol = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbol++; // TODO: Is this correct? Not what spec states!!

        final List<ByteModel> byteModelList = new ArrayList();

        // TODO: initialize byteModel -> set and reset symbols?

        for(int i=0;i<maxSymbol;i++){
            byteModelList.add(i,new ByteModel(maxSymbol));
        }
        outBuffer.put((byte) maxSymbol);

        // TODO: should we pass outBuffer to rangecoder?
        RangeCoder rangeCoder = new RangeCoder();

        int last = 0;
        for (int i = 0; i < inSize; i++ ){
            byteModelList.get(last).modelEncode(outBuffer, rangeCoder, inBuffer.get(i) & 0xFF);
            last = inBuffer.get(i) & 0xFF;
        }
        rangeCoder.rangeEncodeEnd(outBuffer);

        // TODO: should we set littleEndian true somehwere?
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
        return outBuffer;
    }

    private ByteBuffer compressRLEOrder0 (
            final ByteBuffer inBuffer,
            final RangeParams rangeParams,
            final ByteBuffer outBuffer) {
        int maxSymbols = 0;
        int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++) {
            if (maxSymbols < (inBuffer.get(i) & 0xFF)) {
                maxSymbols = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbols++;  // FIXME not what spec states!

        ByteModel modelLit = new ByteModel(maxSymbols);
        final List<ByteModel> byteModelRunsList = new ArrayList(258);

        for (int i=0; i <= 257; i++){
            byteModelRunsList.add(i,new ByteModel(4));
        }
        outBuffer.put((byte)maxSymbols);
        RangeCoder rangeCoder = new RangeCoder();


        int i = 0;
        while (i < inSize) {
            modelLit.modelEncode(outBuffer, rangeCoder, inBuffer.get(i) & 0xFF);
            int run = 1;
            while (i+run < inSize && (inBuffer.get(i+run) & 0xFF)== (inBuffer.get(i) & 0xFF)){
                run++;
            }
            run--; // Check this!!
            int rctx = inBuffer.get(i) & 0xFF;
            int last = inBuffer.get(i) & 0xFF;
            i += run+1;
            int part = run >=3 ? 3 : run;
            byteModelRunsList.get(rctx).modelEncode(outBuffer, rangeCoder, part);
            run -= part;
            rctx = 256;
            while (part == 3){
                part = run >=3 ? 3 : run;
                byteModelRunsList.get(rctx).modelEncode(outBuffer,rangeCoder,part);
                rctx = 257;
                run -= part;
            }
        }
        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
        return outBuffer;
    }

    private ByteBuffer compressRLEOrder1 (
            final ByteBuffer inBuffer,
            final RangeParams rangeParams,
            final ByteBuffer outBuffer) {
        int maxSymbols = 0;
        int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++) {
            if (maxSymbols < (inBuffer.get(i) & 0xFF)) {
                maxSymbols = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbols++;  // FIXME not what spec states!

        final List<ByteModel> modelLitList = new ArrayList<>(maxSymbols);
        for (int i = 0; i < maxSymbols; i++){
            modelLitList.add(i, new ByteModel(maxSymbols));
        }
        final List<ByteModel> byteModelRunsList = new ArrayList(258);
        for (int i=0; i <= 257; i++){
            byteModelRunsList.add(i,new ByteModel(4));
        }
        outBuffer.put((byte)maxSymbols);
        RangeCoder rangeCoder = new RangeCoder();


        int i = 0;
        int last = 0;
        while (i < inSize) {
            modelLitList.get(last).modelEncode(outBuffer, rangeCoder, inBuffer.get(i) & 0xFF);
            int run = 1;
            while (i+run < inSize && inBuffer.get(i+run) == inBuffer.get(i)){
                run++;
            }
            run--; // Check this!!
            int rctx = inBuffer.get(i) & 0xFF;
            last = inBuffer.get(i) & 0xFF;
            i += run+1;
            int part = run >=3 ? 3 : run;
            byteModelRunsList.get(rctx).modelEncode(outBuffer, rangeCoder, part);
            run -= part;
            rctx = 256;
            while (part == 3){
                part = run >=3 ? 3 : run;
                byteModelRunsList.get(rctx).modelEncode(outBuffer,rangeCoder,part);
                rctx = 257;
                run -= part;
            }
        }
        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
        return outBuffer;
    }

    protected ByteBuffer allocateOutputBuffer(final int inSize) {

        // same as the allocateOutputBuffer in RANS4x8Encode and RANSNx16Encode
        // consider deduplication
        final int compressedSize = (int) (1.05 * inSize + 257 * 257 * 3 + 9);
        final ByteBuffer outputBuffer = ByteBuffer.allocate(compressedSize);
        if (outputBuffer.remaining() < compressedSize) {
            throw new RuntimeException("Failed to allocate sufficient buffer size for Range coder.");
        }
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return outputBuffer;
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