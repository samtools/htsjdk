package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.rans.Constants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility methods shared across CRAM 3.1 compression codecs (rANS, Range, Name Tokeniser, etc.),
 * including uint7 encoding, bit-packing, and STRIPE data transformation.
 */
public class CompressionUtils {
    /**
     * Write an unsigned integer using 7-bit variable-length encoding (uint7). Each output byte uses
     * 7 bits for data and the high bit as a continuation flag (1 = more bytes follow).
     *
     * @param i the value to write (must be non-negative)
     * @param cp the output buffer
     */
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

    /**
     * Read an unsigned integer using 7-bit variable-length encoding (uint7). Each byte uses
     * 7 bits for data and the high bit as a continuation flag (1 = more bytes follow).
     *
     * @param cp the input buffer
     * @return the decoded unsigned integer value
     */
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
            throw new CRAMException("Failed to allocate sufficient buffer size for CRAM codec.");
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

    /**
     * Return a byte array with a size that matches the limit of the provided ByteBuffer. If the ByteBuffer is
     * backed by a byte array that matches the limit of the ByteBuffer, the backing array will be returned directly.
     * Otherwise, copy the contents of the ByteBuffer into a new byte array and return the new byte array.
     * @param buffer input ByteBuffer which is the source of the byte array
     * @return A byte array. If the ByteBuffer is backed by a byte array that matches the limit of the ByteBuffer,
     * return the backing array directly. Otherwise, copy the contents of the ByteBuffer into a new byte array.
     */
    private static final int STRIPE_NUM_STREAMS = 4;

    /**
     * Compute the uncompressed size for each stripe stream. Earlier streams get the extra bytes
     * when totalSize is not evenly divisible by the number of streams.
     *
     * @param totalSize the total uncompressed size
     * @return array of per-stream sizes
     */
    public static int[] buildStripeUncompressedSizes(final int totalSize) {
        final int[] sizes = new int[STRIPE_NUM_STREAMS];
        final int q = totalSize / STRIPE_NUM_STREAMS;
        final int r = totalSize % STRIPE_NUM_STREAMS;
        for (int i = 0; i < STRIPE_NUM_STREAMS; i++) {
            sizes[i] = (i < r) ? q + 1 : q;
        }
        return sizes;
    }

    /**
     * Transpose (de-interleave) input data into N=4 separate streams using round-robin byte distribution.
     * Stream i gets bytes at positions i, i+4, i+8, ...
     *
     * @param inBuffer the input data (position to limit)
     * @param sizes per-stream uncompressed sizes from {@link #buildStripeUncompressedSizes}
     * @return array of ByteBuffers, one per stream
     */
    public static ByteBuffer[] stripeTranspose(final ByteBuffer inBuffer, final int[] sizes) {
        final ByteBuffer[] chunks = new ByteBuffer[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            chunks[i] = allocateByteBuffer(sizes[i]);
            for (int j = 0; j < sizes[i]; j++) {
                chunks[i].put(j, inBuffer.get(inBuffer.position() + j * sizes.length + i));
            }
        }
        return chunks;
    }

    /**
     * @return the number of streams used by the STRIPE codec (always 4)
     */
    public static int getStripeNumStreams() {
        return STRIPE_NUM_STREAMS;
    }

    public static byte[] toByteArray(final ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.array().length == buffer.limit()) {
            return buffer.array();
        }

        final byte[] bytes = new byte[buffer.limit() - buffer.arrayOffset()];
        buffer.get(bytes);
        return bytes;
    }
}