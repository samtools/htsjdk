package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.rans.Constants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CompressionUtils {
    public static void writeUint7(final int i, final ByteBuffer cp) {
        int s = 0;
        int X = i;
        do {
            s += 7;
            X >>= 7;
        } while (X > 0);
        do {
            s -= 7;
            //writeByte
            final int s_ = (s > 0) ? 1 : 0;
            cp.put((byte) (((i >> s) & 0x7f) + (s_ << 7)));
        } while (s > 0);
    }

    public static int readUint7(final ByteBuffer cp) {
        int i = 0;
        int c;
        do {
            //read byte
            c = cp.get();
            i = (i << 7) | (c & 0x7f);
        } while ((c & 0x80) != 0);
        return i;
    }

    // Implementation of the spec bit-packing algorithm for range coding.
    public static ByteBuffer encodePack(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int[] frequencyTable,
            final int[] packMappingTable,
            final int numSymbols){
        final int inSize = inBuffer.remaining();
        final ByteBuffer encodedBuffer;
        if (numSymbols <= 1) {
            encodedBuffer = CompressionUtils.allocateByteBuffer(0);
        } else if (numSymbols <= 2) {

            // 1 bit per value
            final int encodedBufferSize = (int) Math.ceil((double) inSize/8);
            encodedBuffer = CompressionUtils.allocateByteBuffer(encodedBufferSize);
            int j = -1;
            for (int i = 0; i < inSize; i ++) {
                if (i % 8 == 0) {
                    encodedBuffer.put(++j, (byte) 0);
                }
                encodedBuffer.put(j, (byte) (encodedBuffer.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << (i % 8))));
            }
        } else if (numSymbols <= 4) {

            // 2 bits per value
            final int encodedBufferSize = (int) Math.ceil((double) inSize/4);
            encodedBuffer = CompressionUtils.allocateByteBuffer(encodedBufferSize);
            int j = -1;
            for (int i = 0; i < inSize; i ++) {
                if (i % 4 == 0) {
                    encodedBuffer.put(++j, (byte) 0);
                }
                encodedBuffer.put(j, (byte) (encodedBuffer.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << ((i % 4) * 2))));
            }
        } else {

            // 4 bits per value
            final int encodedBufferSize = (int) Math.ceil((double)inSize/2);
            encodedBuffer = CompressionUtils.allocateByteBuffer(encodedBufferSize);
            int j = -1;
            for (int i = 0; i < inSize; i ++) {
                if (i % 2 == 0) {
                    encodedBuffer.put(++j, (byte) 0);
                }
                encodedBuffer.put(j, (byte) (encodedBuffer.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << ((i % 2) * 4))));
            }
        }

        // write numSymbols
        outBuffer.put((byte) numSymbols);

        // write mapping table "packMappingTable" that converts mapped value to original symbol
        for(int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i ++) {
            if (frequencyTable[i] > 0) {
                outBuffer.put((byte) i);
            }
        }

        // write the length of data
        CompressionUtils.writeUint7(encodedBuffer.limit(), outBuffer);
        return encodedBuffer; // Here position = 0 since we have always accessed the data buffer using index
    }

    public static ByteBuffer decodePack(
            final ByteBuffer inBuffer,
            final byte[] packMappingTable,
            final int numSymbols,
            final int uncompressedPackOutputLength) {
        final ByteBuffer outBufferPack = CompressionUtils.allocateByteBuffer(uncompressedPackOutputLength);
        int j = 0;
        if (numSymbols <= 1) {
            for (int i=0; i < uncompressedPackOutputLength; i++){
                outBufferPack.put(i, packMappingTable[0]);
            }
        }

        // 1 bit per value
        else if (numSymbols <= 2) {
            int v = 0;
            for (int i=0; i < uncompressedPackOutputLength; i++){
                if (i % 8 == 0){
                    v = inBuffer.get(j++);
                }
                outBufferPack.put(i, packMappingTable[v & 1]);
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
                outBufferPack.put(i, packMappingTable[v & 3]);
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
                outBufferPack.put(i, packMappingTable[v & 15]);
                v >>=4;
            }
        }
        return outBufferPack;
    }

    public static ByteBuffer allocateOutputBuffer(final int inSize) {
        // This calculation is identical to the one in samtools rANS_static.c
        // Presumably the frequency table (always big enough for order 1) = 257*257,
        // then * 3 for each entry (byte->symbol, 2 bytes -> scaled frequency),
        // + 9 for the header (order byte, and 2 int lengths for compressed/uncompressed lengths).
        final int compressedSize = (int) (inSize + 257 * 257 * 3 + 9);
        final ByteBuffer outputBuffer = allocateByteBuffer(compressedSize);
        if (outputBuffer.remaining() < compressedSize) {
            throw new CRAMException("Failed to allocate sufficient buffer size for RANS coder.");
        }
        return outputBuffer;
    }

    // returns a new LITTLE_ENDIAN ByteBuffer of size = bufferSize
    public static ByteBuffer allocateByteBuffer(final int bufferSize){
        return ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);
    }

    // returns a LITTLE_ENDIAN ByteBuffer that is created by wrapping a byte[]
    public static ByteBuffer wrap(final byte[] inputBytes){
        return ByteBuffer.wrap(inputBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    // returns a LITTLE_ENDIAN ByteBuffer that is created by inputBuffer.slice()
    public static ByteBuffer slice(final ByteBuffer inputBuffer){
        return inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN);
    }
}