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
            // writeByte
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
            c = cp.get();
            i = (i << 7) | (c & 0x7f);
        } while ((c & 0x80) != 0);
        return i;
    }

    /** Write uint7 into byte[] at posHolder[0], advancing posHolder[0]. */
    public static void writeUint7(final int i, final byte[] buf, final int[] posHolder) {
        int s = 0;
        int X = i;
        do {
            s += 7;
            X >>= 7;
        } while (X > 0);
        int pos = posHolder[0];
        do {
            s -= 7;
            final int s_ = (s > 0) ? 1 : 0;
            buf[pos++] = (byte) (((i >> s) & 0x7f) + (s_ << 7));
        } while (s > 0);
        posHolder[0] = pos;
    }

    /** Read uint7 from byte[] at posHolder[0], advancing posHolder[0]. */
    public static int readUint7(final byte[] buf, final int[] posHolder) {
        int i = 0;
        int c;
        do {
            c = buf[posHolder[0]++];
            i = (i << 7) | (c & 0x7f);
        } while ((c & 0x80) != 0);
        return i;
    }

    /**
     * Pack input symbols into a smaller number of bits per value based on the number of distinct
     * symbols. Writes the pack header (symbol count, mapping table, packed length) to outBuffer
     * and returns the packed data as a separate buffer.
     *
     * @param inBuffer the input data to pack
     * @param outBuffer the output buffer for the pack header (symbol count, mapping table, packed length)
     * @param frequencyTable frequency counts for each byte value (0-255)
     * @param packMappingTable mapping from original symbol to packed value
     * @param numSymbols the number of distinct symbols in the input
     * @return a ByteBuffer containing the packed data
     */
    public static ByteBuffer encodePack(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer,
            final int[] frequencyTable,
            final int[] packMappingTable,
            final int numSymbols) {
        final int inSize = inBuffer.remaining();
        final ByteBuffer encodedBuffer;
        if (numSymbols <= 1) {
            encodedBuffer = CompressionUtils.allocateByteBuffer(0);
        } else if (numSymbols <= 2) {

            // 1 bit per value
            final int encodedBufferSize = (int) Math.ceil((double) inSize / 8);
            encodedBuffer = CompressionUtils.allocateByteBuffer(encodedBufferSize);
            int j = -1;
            for (int i = 0; i < inSize; i++) {
                if (i % 8 == 0) {
                    encodedBuffer.put(++j, (byte) 0);
                }
                encodedBuffer.put(
                        j, (byte) (encodedBuffer.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << (i % 8))));
            }
        } else if (numSymbols <= 4) {

            // 2 bits per value
            final int encodedBufferSize = (int) Math.ceil((double) inSize / 4);
            encodedBuffer = CompressionUtils.allocateByteBuffer(encodedBufferSize);
            int j = -1;
            for (int i = 0; i < inSize; i++) {
                if (i % 4 == 0) {
                    encodedBuffer.put(++j, (byte) 0);
                }
                encodedBuffer.put(
                        j, (byte) (encodedBuffer.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << ((i % 4) * 2))));
            }
        } else {

            // 4 bits per value
            final int encodedBufferSize = (int) Math.ceil((double) inSize / 2);
            encodedBuffer = CompressionUtils.allocateByteBuffer(encodedBufferSize);
            int j = -1;
            for (int i = 0; i < inSize; i++) {
                if (i % 2 == 0) {
                    encodedBuffer.put(++j, (byte) 0);
                }
                encodedBuffer.put(
                        j, (byte) (encodedBuffer.get(j) + (packMappingTable[inBuffer.get(i) & 0xFF] << ((i % 2) * 4))));
            }
        }

        // write numSymbols
        outBuffer.put((byte) numSymbols);

        // write mapping table "packMappingTable" that converts mapped value to original symbol
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (frequencyTable[i] > 0) {
                outBuffer.put((byte) i);
            }
        }

        // write the length of data
        CompressionUtils.writeUint7(encodedBuffer.limit(), outBuffer);
        return encodedBuffer; // Here position = 0 since we have always accessed the data buffer using index
    }

    /**
     * Unpack bit-packed data back to one byte per symbol, reversing the transformation
     * performed by {@link #encodePack}.
     *
     * @param inBuffer the packed input data
     * @param packMappingTable mapping from packed value back to original symbol
     * @param numSymbols the number of distinct symbols (determines bits per value)
     * @param uncompressedPackOutputLength the expected number of output bytes
     * @return a ByteBuffer containing the unpacked data
     */
    public static ByteBuffer decodePack(
            final ByteBuffer inBuffer,
            final byte[] packMappingTable,
            final int numSymbols,
            final int uncompressedPackOutputLength) {
        final ByteBuffer outBufferPack = CompressionUtils.allocateByteBuffer(uncompressedPackOutputLength);
        int j = 0;
        if (numSymbols <= 1) {
            for (int i = 0; i < uncompressedPackOutputLength; i++) {
                outBufferPack.put(i, packMappingTable[0]);
            }
        }

        // 1 bit per value
        else if (numSymbols <= 2) {
            int v = 0;
            for (int i = 0; i < uncompressedPackOutputLength; i++) {
                if (i % 8 == 0) {
                    v = inBuffer.get(j++);
                }
                outBufferPack.put(i, packMappingTable[v & 1]);
                v >>= 1;
            }
        }

        // 2 bits per value
        else if (numSymbols <= 4) {
            int v = 0;
            for (int i = 0; i < uncompressedPackOutputLength; i++) {
                if (i % 4 == 0) {
                    v = inBuffer.get(j++);
                }
                outBufferPack.put(i, packMappingTable[v & 3]);
                v >>= 2;
            }
        }

        // 4 bits per value
        else if (numSymbols <= 16) {
            int v = 0;
            for (int i = 0; i < uncompressedPackOutputLength; i++) {
                if (i % 2 == 0) {
                    v = inBuffer.get(j++);
                }
                outBufferPack.put(i, packMappingTable[v & 15]);
                v >>= 4;
            }
        }
        return outBufferPack;
    }

    /**
     * Allocate an output buffer large enough to hold compressed rANS data, including worst-case
     * frequency table overhead and header bytes.
     *
     * @param inSize the uncompressed input size
     * @return a little-endian ByteBuffer sized for the worst-case compressed output
     */
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

    /**
     * Allocate a new little-endian ByteBuffer of the specified size.
     *
     * @param bufferSize the capacity of the buffer
     * @return a new little-endian ByteBuffer
     */
    public static ByteBuffer allocateByteBuffer(final int bufferSize) {
        return ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Wrap a byte array in a little-endian ByteBuffer.
     *
     * @param inputBytes the byte array to wrap
     * @return a little-endian ByteBuffer backed by the input array
     */
    public static ByteBuffer wrap(final byte[] inputBytes) {
        return ByteBuffer.wrap(inputBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Create a little-endian slice of the given ByteBuffer (from position to limit).
     *
     * @param inputBuffer the buffer to slice
     * @return a new little-endian ByteBuffer sharing the input's content
     */
    public static ByteBuffer slice(final ByteBuffer inputBuffer) {
        return inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Number of interleaved streams used by the STRIPE transformation. */
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

    /**
     * Return a byte array with contents matching the ByteBuffer from position 0 to limit.
     * If the buffer is backed by an array that exactly matches its limit, returns the
     * backing array directly (no copy). Otherwise copies the data into a new array.
     *
     * @param buffer the source ByteBuffer
     * @return a byte array containing the buffer's data
     */
    public static byte[] toByteArray(final ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.array().length == buffer.limit()) {
            return buffer.array();
        }

        final byte[] bytes = new byte[buffer.limit() - buffer.arrayOffset()];
        buffer.get(bytes);
        return bytes;
    }
}
