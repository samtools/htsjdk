package htsjdk.samtools.cram.compression.range;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.BZIP2ExternalCompressor;
import htsjdk.samtools.cram.compression.rans.Utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class RangeDecode {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer uncompress(final ByteBuffer inBuffer) {
        return uncompressStream(inBuffer, 0);
    }

    public ByteBuffer uncompressStream(final ByteBuffer inBuffer, int outSize) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // TODO: little endian?
//        inBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // the first byte of compressed stream gives the formatFlags
        final int formatFlags = inBuffer.get() & 0xFF;
        final RangeParams rangeParams = new RangeParams(formatFlags);

       // noSz
        outSize = rangeParams.isNosz() ? outSize : Utils.readUint7(inBuffer);

        // stripe
        if (rangeParams.isStripe()) {
                return decodeStripe(inBuffer, outSize);
        }

        // pack
        // if pack, get pack metadata, which will be used later to decode packed data
        int packDataLength = 0;
        int numSymbols = 0;
        int[] packMappingTable = new int[0];
        if (rangeParams.isPack()){
            packDataLength = outSize;
            numSymbols = inBuffer.get() & 0xFF;

            // if (numSymbols > 16 or numSymbols==0), raise exception
            if (numSymbols <= 16 && numSymbols!=0) {
                packMappingTable = new int[numSymbols];
                for (int i = 0; i < numSymbols; i++) {
                    packMappingTable[i] = inBuffer.get() & 0xFF;
                }
                outSize = Utils.readUint7(inBuffer);
            } else {
                throw new CRAMException("Bit Packing is not permitted when number of distinct symbols is greater than 16 or equal to 0. Number of distinct symbols: " + numSymbols);
            }
        }

        ByteBuffer outBuffer = ByteBuffer.allocate(outSize);
        if (rangeParams.isCAT()){
            byte[] data = new byte[outSize];
            inBuffer.get( data,0, outSize);
            return ByteBuffer.wrap(data);
        } else if (rangeParams.isExternalCompression()){
            byte[] extCompressedBytes = new byte[inBuffer.remaining()];
            inBuffer.get( extCompressedBytes,inBuffer.position(), inBuffer.remaining());
            uncompressEXT(extCompressedBytes, outBuffer);
        } else if (rangeParams.isRLE()){
            switch (rangeParams.getOrder()) {
                case ZERO:
                    uncompressRLEOrder0(inBuffer, outBuffer, outSize);
                    break;
                case ONE:
                    uncompressRLEOrder1(inBuffer, outBuffer, outSize);
                    break;
            }
        } else {
            switch (rangeParams.getOrder()) {
                case ZERO:
                    uncompressOrder0(inBuffer, outBuffer, outSize);
                    break;
                case ONE:
                    uncompressOrder1(inBuffer, outBuffer, outSize);
                    break;
            }
        }

        // if pack, then decodePack
        if (rangeParams.isPack() && packMappingTable.length > 0) {
            outBuffer = decodePack(outBuffer, packMappingTable, numSymbols, packDataLength);
        }

        outBuffer.position(0);
        return outBuffer;

    }

    private ByteBuffer uncompressOrder0(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize) {

        int maxSymbols = inBuffer.get() & 0xFF;
        maxSymbols = maxSymbols==0 ? 256 : maxSymbols;

        final ByteModel byteModel = new ByteModel(maxSymbols);
        final RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);

        for (int i = 0; i < outSize; i++) {
            outBuffer.put(i, (byte) byteModel.modelDecode(inBuffer, rangeCoder));
        }
        return outBuffer;
    }

    private ByteBuffer uncompressOrder1(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize) {

        int maxSymbols = inBuffer.get() & 0xFF;
        maxSymbols = maxSymbols==0 ? 256 : maxSymbols;

        final List<ByteModel> byteModelList = new ArrayList();

        for(int i=0;i<maxSymbols;i++){
            byteModelList.add(new ByteModel(maxSymbols));
        }

        final RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);

        int last = 0;
        for (int i = 0; i < outSize; i++) {
            outBuffer.put(i, (byte) byteModelList.get(last).modelDecode(inBuffer, rangeCoder));
            last = outBuffer.get(i)& 0xFF;
        }

        return outBuffer;
    }

    private ByteBuffer uncompressRLEOrder0(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize) {

        int maxSymbols = inBuffer.get() & 0xFF;
        maxSymbols = maxSymbols == 0 ? 256 : maxSymbols;
        ByteModel modelLit = new ByteModel(maxSymbols);
        final List<ByteModel> byteModelRunsList = new ArrayList(258);
        for (int i=0; i <=257; i++){
            byteModelRunsList.add(i, new ByteModel(4));
        }
        RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);

        int i = 0;
        while (i < outSize) {
            outBuffer.put(i,(byte) modelLit.modelDecode(inBuffer, rangeCoder));
            int part = byteModelRunsList.get(outBuffer.get(i)&0xFF).modelDecode(inBuffer,rangeCoder);
            int run = part;
            int rctx = 256;
            while (part == 3) {
                part = byteModelRunsList.get(rctx).modelDecode(inBuffer, rangeCoder);
                rctx = 257;
                run += part;
            }
            for (int j = 1; j <= run; j++){
                outBuffer.put(i+j, outBuffer.get(i));
            }
            i += run+1;
        }
        return outBuffer;
    }

    private ByteBuffer uncompressRLEOrder1(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize) {

        int maxSymbols = inBuffer.get() & 0xFF;
        maxSymbols = maxSymbols == 0 ? 256 : maxSymbols;
        final List<ByteModel> byteModelLitList = new ArrayList(maxSymbols);
        for (int i=0; i < maxSymbols; i++) {
            byteModelLitList.add(i,new ByteModel(maxSymbols));
        }
        final List<ByteModel> byteModelRunsList = new ArrayList(258);
        for (int i=0; i <=257; i++){
            byteModelRunsList.add(i, new ByteModel(4));
        }

        RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);

        int last = 0;
        int i = 0;
        while (i < outSize) {
            outBuffer.put(i,(byte) byteModelLitList.get(last).modelDecode(inBuffer, rangeCoder));
            last = outBuffer.get(i) & 0xFF;
            int part = byteModelRunsList.get(outBuffer.get(i)&0xFF).modelDecode(inBuffer,rangeCoder);
            int run = part;
            int rctx = 256;
            while (part == 3) {
                part = byteModelRunsList.get(rctx).modelDecode(inBuffer, rangeCoder);
                rctx = 257;
                run += part;
            }
            for (int j = 1; j <= run; j++){
                outBuffer.put(i+j, outBuffer.get(i));
            }
            i += run+1;
        }
        return outBuffer;
    }

    private ByteBuffer uncompressEXT(
            final byte[] extCompressedBytes,
            final ByteBuffer outBuffer) {
        final BZIP2ExternalCompressor compressor = new BZIP2ExternalCompressor();
        final byte [] extUncompressedBytes = compressor.uncompress(extCompressedBytes);
        outBuffer.put(extUncompressedBytes);
        return outBuffer;
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

            T[j] = uncompressStream(inBuffer, ulen[j]);
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