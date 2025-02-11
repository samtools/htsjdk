package htsjdk.samtools.cram.compression.range;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.BZIP2ExternalCompressor;
import htsjdk.samtools.cram.compression.CompressionUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;


/**
 * Decoder for the CRAM 3.1 arithmetic (range) codec. This is an adaptive, byte-wise compression codec for use
 * with data streams that have a varying byte/symbol probability distribution. Significantly more expensive than
 * rAns.
 */
public class RangeDecode {

    private static final ByteBuffer EMPTY_BUFFER = CompressionUtils.allocateByteBuffer(0);

    // This method assumes that inBuffer is already rewound.
    // It uncompresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the uncompressed data.
    public ByteBuffer uncompress(final ByteBuffer inBuffer) {
        return uncompress(inBuffer, 0);
    }

    private ByteBuffer uncompress(final ByteBuffer inBuffer, final int outSize) {
        // For Range decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // the first byte of compressed stream gives the formatFlags
        final int formatFlags = inBuffer.get() & 0xFF;
        final RangeParams rangeParams = new RangeParams(formatFlags);

       // noSz
        int uncompressedSize = rangeParams.isNosz() ? outSize : CompressionUtils.readUint7(inBuffer);

        // stripe
        if (rangeParams.isStripe()) {
            return decodeStripe(inBuffer, uncompressedSize);
        }

        // pack
        // if pack, get pack metadata, which will be used later to decode packed data
        int packDataLength = 0;
        int numSymbols = 0;
        byte[] packMappingTable = null;
        if (rangeParams.isPack()){
            packDataLength = uncompressedSize;
            numSymbols = inBuffer.get() & 0xFF;

            // if (numSymbols > 16 or numSymbols==0), raise exception
            if (numSymbols <= 16 && numSymbols!=0) {
                packMappingTable = new byte[numSymbols];
                for (int i = 0; i < numSymbols; i++) {
                    packMappingTable[i] = inBuffer.get();
                }
                uncompressedSize = CompressionUtils.readUint7(inBuffer);
            } else {
                throw new CRAMException("Bit Packing is not permitted when number of distinct symbols is greater than 16 or equal to 0. " +
                        "Number of distinct symbols: " + numSymbols);
            }
        }

        ByteBuffer outBuffer;
        if (rangeParams.isCAT()){
            outBuffer = CompressionUtils.slice(inBuffer);
            outBuffer.limit(uncompressedSize);
            // While resetting the position to the end is not strictly necessary,
            // it is being done for the sake of completeness and
            // to meet the requirements of the tests that verify the boundary conditions.
            inBuffer.position(inBuffer.position()+uncompressedSize);
        } else if (rangeParams.isExternalCompression()){
            final byte[] extCompressedBytes = new byte[inBuffer.remaining()];
            int extCompressedBytesIdx = 0;
            final int start = inBuffer.position();
            final int end = inBuffer.limit();
            for (int i = start; i < end; i++) {
                extCompressedBytes[extCompressedBytesIdx] = inBuffer.get();
                extCompressedBytesIdx++;
            }
            outBuffer = uncompressEXT(extCompressedBytes);
        } else if (rangeParams.isRLE()){
            outBuffer = CompressionUtils.allocateByteBuffer(uncompressedSize);
            switch (rangeParams.getOrder()) {
                case ZERO:
                    uncompressRLEOrder0(inBuffer, outBuffer, uncompressedSize);
                    break;
                case ONE:
                    uncompressRLEOrder1(inBuffer, outBuffer, uncompressedSize);
                    break;
            }
        } else {
            outBuffer = CompressionUtils.allocateByteBuffer(uncompressedSize);
            switch (rangeParams.getOrder()){
                case ZERO:
                    uncompressOrder0(inBuffer, outBuffer, uncompressedSize);
                    break;
                case ONE:
                    uncompressOrder1(inBuffer, outBuffer, uncompressedSize);
                    break;
            }
        }

        // if pack, then decodePack
        if (rangeParams.isPack()) {
            outBuffer = CompressionUtils.decodePack(outBuffer, packMappingTable, numSymbols, packDataLength);
        }
        outBuffer.rewind();
        return outBuffer;
    }

    private void uncompressOrder0(
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
    }

    private void uncompressOrder1(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize) {

        int maxSymbols = inBuffer.get() & 0xFF;
        maxSymbols = maxSymbols==0 ? 256 : maxSymbols;
        final List<ByteModel> byteModelList = new ArrayList(maxSymbols);
        for(int i=0;i<maxSymbols;i++){
            byteModelList.add(new ByteModel(maxSymbols));
        }
        final RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);
        for (int last = 0, i = 0; i < outSize; i++) {
            last = byteModelList.get(last).modelDecode(inBuffer, rangeCoder);
            outBuffer.put(i, (byte) last);
        }
    }

    private void uncompressRLEOrder0(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int outSize) {

        int maxSymbols = inBuffer.get() & 0xFF;
        maxSymbols = maxSymbols == 0 ? 256 : maxSymbols;
        final ByteModel modelLit = new ByteModel(maxSymbols);
        final List<ByteModel> byteModelRunsList = new ArrayList(258);
        for (int i=0; i <=257; i++){
            byteModelRunsList.add(i, new ByteModel(4));
        }
        RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);

        int i = 0;
        while (i < outSize) {
            outBuffer.put(i,(byte) modelLit.modelDecode(inBuffer, rangeCoder));
            final int last = outBuffer.get(i) & (0xFF);
            int part = byteModelRunsList.get(last).modelDecode(inBuffer,rangeCoder);
            int run = part;
            int rctx = 256;
            while (part == 3) {
                part = byteModelRunsList.get(rctx).modelDecode(inBuffer, rangeCoder);
                rctx = 257;
                run += part;
            }
            for (int j = 1; j <= run; j++){
                outBuffer.put(i+j, (byte) last);
            }
            i += run+1;
        }
    }

    private void uncompressRLEOrder1(
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

        final RangeCoder rangeCoder = new RangeCoder();
        rangeCoder.rangeDecodeStart(inBuffer);

        int last = 0;
        int i = 0;
        while (i < outSize) {
            outBuffer.put(i,(byte) byteModelLitList.get(last).modelDecode(inBuffer, rangeCoder));
            last = outBuffer.get(i) & 0xFF;
            int part = byteModelRunsList.get(last).modelDecode(inBuffer,rangeCoder);
            int run = part;
            int rctx = 256;
            while (part == 3) {
                part = byteModelRunsList.get(rctx).modelDecode(inBuffer, rangeCoder);
                rctx = 257;
                run += part;
            }
            for (int j = 1; j <= run; j++){
                outBuffer.put(i+j, (byte)last);
            }
            i += run+1;
        }
    }

    private ByteBuffer uncompressEXT(final byte[] extCompressedBytes) {
        final BZIP2ExternalCompressor compressor = new BZIP2ExternalCompressor();
        final byte [] extUncompressedBytes = compressor.uncompress(extCompressedBytes);
        return CompressionUtils.wrap(extUncompressedBytes);
    }

    private ByteBuffer decodeStripe(final ByteBuffer inBuffer, final int outSize){
        final int numInterleaveStreams = inBuffer.get() & 0xFF;

        // read lengths of compressed interleaved streams
        for ( int j=0; j<numInterleaveStreams; j++ ){
            CompressionUtils.readUint7(inBuffer); // not storing these values as they are not used
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
        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer(outSize);
        for (int j = 0; j <numInterleaveStreams; j++) {
            for (int i = 0; i < uncompressedLengths[j]; i++) {
                outBuffer.put((i*numInterleaveStreams)+j, transposedData[j].get(i));
            }
        }
        return outBuffer;
    }

}