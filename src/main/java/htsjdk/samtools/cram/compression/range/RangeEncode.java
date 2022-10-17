package htsjdk.samtools.cram.compression.range;


import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RangeEncode {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
//    private static final int MINIMUM__ORDER_1_SIZE = 4;

    public ByteBuffer compress(final ByteBuffer inBuffer, final RangeParams rangeParams) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        final ByteBuffer outBuffer = allocateOutputBuffer(inBuffer.remaining());
        final int formatFlags = rangeParams.getFormatFlags();
        outBuffer.put((byte) (formatFlags)); // one byte for formatFlags

        //  NoSize
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
        final int e_len = inputBuffer.remaining(); // e_len -> inSize

        // Pack
        if (rangeParams.isPack()) {
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
                inputBuffer = encodePack(inputBuffer, outBuffer, frequencyTable, packMappingTable, numSymbols);
            } else {
                // unset pack flag in the first byte of the outBuffer
                outBuffer.put(0,(byte)(outBuffer.get(0) & ~RangeParams.PACK_FLAG_MASK));
            }
        }

        if (rangeParams.isCAT()){

        } else if (rangeParams.isExternalCompression()){


        } else if (rangeParams.isRLE()){
            switch (rangeParams.getOrder()) {
                case ZERO:
//                    return compressRLEOrder0(inputBuffer, rangeParams, outBuffer); //src, e_len, this.stream
                case ONE:
//                    return compressRLEOrder1(inputBuffer, rangeParams, outBuffer); //src, e_len, this.stream
            }
        } else {
            switch (rangeParams.getOrder()) {
                case ZERO:
                    return compressOrder0(inputBuffer, rangeParams, outBuffer); //src, e_len, this.stream
                case ONE:
//                    return compressOrder1(inputBuffer, rangeParams, outBuffer); //src, e_len, this.stream
            }

        }



//        if flagsAND Cat then
//              data  ReadData(len)
//        else if flagsAND Ext then
//              data  DecodeEXT(len)
//        else if flagsAND RLE then
//        ...
//        else ..


//        // step 1: Encode meta-data
//        var pack_meta
//        if (flags & ARITH_PACK)
//	    [pack_meta, src, e_len] = this.encodePack(src)
//
//        // step 2: Write any meta data
//        if (flags & ARITH_PACK)
//            this.stream.WriteStream(pack_meta)







        // temp
        return inBuffer;
    }

    private ByteBuffer compressOrder0 (
            final ByteBuffer inBuffer,
            final RangeParams rangeParams,
            final ByteBuffer outBuffer) {

        int maxSymbol = 0;
        final int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++){
            if(maxSymbol < inBuffer.get(i)){
                maxSymbol = inBuffer.get(i);
            }
        }
        maxSymbol++; // TODO: Is this correct? Not what spec states!!

        ByteModel byteModel = new ByteModel(maxSymbol);
        outBuffer.put((byte) maxSymbol);

        // TODO: should we pass outBuffer to rangecoder?
        RangeCoder rangeCoder = new RangeCoder();

        for (int i=0; i <inSize; i++){
            byteModel.modelEncode(outBuffer,rangeCoder,inBuffer.getInt(i));
        }
        rangeCoder.rangeEncodeEnd(outBuffer);

        // TODO: should we set littleEndian true somehwere?
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