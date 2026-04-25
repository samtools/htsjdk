/*
 * The MIT License
 *
 * Copyright (c) 2024 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.zip.DeflaterFactory;
import htsjdk.samtools.util.zip.InflaterFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A reusable codec for compressing and decompressing GZIP and BGZF data using direct
 * {@link Deflater}/{@link Inflater} operations on {@link ByteBuffer}s. Designed to be
 * allocated once and reused across many compress/decompress operations.
 *
 * <p>Supports two output formats for compression:
 * <ul>
 *     <li>{@link Format#GZIP} — standard 10-byte GZIP header (RFC 1952)</li>
 *     <li>{@link Format#BGZF} — BGZF header with BC extra subfield (SAM/BAM spec)</li>
 * </ul>
 *
 * <p>Decompression handles both formats transparently by parsing the FLG byte and
 * skipping any optional GZIP fields.
 *
 * <p>Not thread-safe. Use one instance per thread.
 */
public class GzipCodec {

    /** The output format for compression. */
    public enum Format {
        GZIP,
        BGZF
    }

    // Standard GZIP header: 10 bytes (RFC 1952)
    private static final int GZIP_HEADER_SIZE = 10;

    // BGZF header: 18 bytes (standard GZIP + FEXTRA with BC subfield)
    private static final int BGZF_HEADER_SIZE = BlockCompressedStreamConstants.BLOCK_HEADER_LENGTH;

    // GZIP trailer: CRC32 (4 bytes) + ISIZE (4 bytes)
    private static final int GZIP_TRAILER_SIZE = 8;

    // GZIP magic bytes
    private static final byte GZIP_ID1 = BlockCompressedStreamConstants.GZIP_ID1;
    private static final byte GZIP_ID2 = (byte) BlockCompressedStreamConstants.GZIP_ID2;
    private static final byte GZIP_CM_DEFLATE = BlockCompressedStreamConstants.GZIP_CM_DEFLATE;

    // GZIP FLG bits
    private static final int FTEXT = 1;
    private static final int FHCRC = 2;
    private static final int FEXTRA = 4;
    private static final int FNAME = 8;
    private static final int FCOMMENT = 16;

    private final Deflater deflater;
    private final Inflater inflater;
    private final CRC32 crc32 = new CRC32();
    private boolean checkCrcs = false;

    /** Create a codec with the default compression level and default strategy. */
    public GzipCodec() {
        this(Defaults.COMPRESSION_LEVEL, Deflater.DEFAULT_STRATEGY);
    }

    /** Create a codec with the specified compression level and default strategy. */
    public GzipCodec(final int compressionLevel) {
        this(compressionLevel, Deflater.DEFAULT_STRATEGY);
    }

    /** Create a codec with the specified compression level and deflate strategy. */
    public GzipCodec(final int compressionLevel, final int deflateStrategy) {
        this(compressionLevel, deflateStrategy, new DeflaterFactory(), new InflaterFactory());
    }

    /**
     * Create a codec with full control over compression parameters and factory implementations.
     *
     * @param compressionLevel deflate compression level (0-9)
     * @param deflateStrategy  deflate strategy (e.g., {@link Deflater#DEFAULT_STRATEGY}, {@link Deflater#FILTERED})
     * @param deflaterFactory  factory for creating Deflater instances
     * @param inflaterFactory  factory for creating Inflater instances
     */
    public GzipCodec(
            final int compressionLevel,
            final int deflateStrategy,
            final DeflaterFactory deflaterFactory,
            final InflaterFactory inflaterFactory) {
        // nowrap=true: we produce raw deflate and handle GZIP framing ourselves
        this.deflater = deflaterFactory.makeDeflater(compressionLevel, true);
        this.deflater.setStrategy(deflateStrategy);
        this.inflater = inflaterFactory.makeInflater(true);
    }

    /** Enable or disable CRC32 validation during decompression. */
    public void setCheckCrcs(final boolean check) {
        this.checkCrcs = check;
    }

    // --------------------------------------------------------------------------------------------
    // Compression
    // --------------------------------------------------------------------------------------------

    /**
     * Compress data from {@code input} into {@code output} using standard GZIP format.
     *
     * @param input  data to compress (from position to limit; position is advanced to limit)
     * @param output buffer to write compressed data into (from position; position is advanced)
     * @return number of bytes written to output
     */
    public int compress(final ByteBuffer input, final ByteBuffer output) {
        return compress(input, output, Format.GZIP);
    }

    /**
     * Compress data from {@code input} into {@code output} using the specified format.
     *
     * @param input  data to compress (from position to limit; position is advanced to limit)
     * @param output buffer to write compressed data into (from position; position is advanced)
     * @param format the output format ({@link Format#GZIP} or {@link Format#BGZF})
     * @return number of bytes written to output
     */
    public int compress(final ByteBuffer input, final ByteBuffer output, final Format format) {
        final int outputStart = output.position();
        final int inputSize = input.remaining();

        // Compute CRC32 over the uncompressed input
        crc32.reset();
        final int inputPos = input.position();
        // Use a slice to avoid disturbing input's position
        final ByteBuffer crcSlice = input.slice();
        crc32.update(crcSlice);

        // Write header (reserves space; for BGZF the block size is patched after deflation)
        final int headerSize = writeHeader(output, format);

        // Extract input bytes for deflater (byte[] API for compatibility with LibdeflateDeflater)
        final byte[] inputBytes;
        final int inputOff;
        input.position(inputPos);
        if (input.hasArray()) {
            inputBytes = input.array();
            inputOff = input.arrayOffset() + inputPos;
        } else {
            inputBytes = new byte[inputSize];
            input.get(inputBytes);
            inputOff = 0;
        }

        // Deflate into a temporary byte[] then copy to output buffer
        deflater.reset();
        deflater.setInput(inputBytes, inputOff, inputSize);
        deflater.finish();
        while (!deflater.finished()) {
            final int n =
                    deflater.deflate(output.array(), output.arrayOffset() + output.position(), output.remaining());
            output.position(output.position() + n);
        }

        // Write trailer: CRC32 + ISIZE (little-endian)
        output.order(ByteOrder.LITTLE_ENDIAN);
        output.putInt((int) crc32.getValue());
        output.putInt(inputSize);

        // For BGZF, patch the total block size into the header
        if (format == Format.BGZF) {
            final int totalBlockSize = output.position() - outputStart;
            output.order(ByteOrder.LITTLE_ENDIAN);
            output.putShort(
                    outputStart + BlockCompressedStreamConstants.BLOCK_LENGTH_OFFSET, (short) (totalBlockSize - 1));
        }

        return output.position() - outputStart;
    }

    /**
     * Compress data and return a new ByteBuffer containing the compressed result.
     *
     * @param input data to compress (from position to limit; position is advanced to limit)
     * @return a new ByteBuffer containing the compressed data, positioned at 0 with limit at the end
     */
    public ByteBuffer compress(final ByteBuffer input) {
        return compress(input, Format.GZIP);
    }

    /**
     * Compress data and return a new ByteBuffer containing the compressed result.
     *
     * @param input  data to compress (from position to limit; position is advanced to limit)
     * @param format the output format
     * @return a new ByteBuffer containing the compressed data, positioned at 0 with limit at the end
     */
    public ByteBuffer compress(final ByteBuffer input, final Format format) {
        // Worst case: incompressible data + header + trailer. Deflater overhead is at most
        // 5 bytes per 32KB block + a few bytes for the zlib wrapper.
        final int maxCompressed = input.remaining() + (input.remaining() / 16000 + 1) * 5 + 256;
        final int headerSize = format == Format.BGZF ? BGZF_HEADER_SIZE : GZIP_HEADER_SIZE;
        final ByteBuffer output = ByteBuffer.allocate(headerSize + maxCompressed + GZIP_TRAILER_SIZE);
        compress(input, output, format);
        output.flip();
        return output;
    }

    /** Write a GZIP or BGZF header to the output buffer. Returns the header size. */
    private int writeHeader(final ByteBuffer output, final Format format) {
        if (format == Format.BGZF) {
            output.put(GZIP_ID1);
            output.put(GZIP_ID2);
            output.put(GZIP_CM_DEFLATE);
            output.put((byte) FEXTRA); // FLG: FEXTRA set
            output.putInt(0); // MTIME
            output.put((byte) 0); // XFL
            output.put((byte) 0xFF); // OS: unknown
            output.order(ByteOrder.LITTLE_ENDIAN);
            output.putShort(BlockCompressedStreamConstants.GZIP_XLEN); // XLEN = 6
            output.put(BlockCompressedStreamConstants.BGZF_ID1); // SI1 = 'B'
            output.put(BlockCompressedStreamConstants.BGZF_ID2); // SI2 = 'C'
            output.putShort(BlockCompressedStreamConstants.BGZF_LEN); // SLEN = 2
            output.putShort((short) 0); // BSIZE placeholder — patched after deflation
            return BGZF_HEADER_SIZE;
        } else {
            output.put(GZIP_ID1);
            output.put(GZIP_ID2);
            output.put(GZIP_CM_DEFLATE);
            output.put((byte) 0); // FLG: no optional fields
            output.putInt(0); // MTIME
            output.put((byte) 0); // XFL
            output.put((byte) 0xFF); // OS: unknown
            return GZIP_HEADER_SIZE;
        }
    }

    // --------------------------------------------------------------------------------------------
    // Decompression
    // --------------------------------------------------------------------------------------------

    /**
     * Decompress GZIP or BGZF data from {@code input} into {@code output}.
     * Handles both standard GZIP and BGZF transparently.
     *
     * @param input  compressed data (from position to limit; position is advanced)
     * @param output buffer to write decompressed data into (from position; position is advanced)
     * @return number of decompressed bytes written to output
     */
    public int decompress(final ByteBuffer input, final ByteBuffer output) {
        input.order(ByteOrder.LITTLE_ENDIAN);

        // Parse and validate the GZIP header
        if (input.remaining() < GZIP_HEADER_SIZE + GZIP_TRAILER_SIZE) {
            throw new IllegalArgumentException("Input too small to be a valid GZIP block");
        }

        final byte id1 = input.get();
        final byte id2 = input.get();
        final byte cm = input.get();
        final int flg = input.get() & 0xFF;
        if (id1 != GZIP_ID1 || id2 != GZIP_ID2 || cm != GZIP_CM_DEFLATE) {
            throw new IllegalArgumentException("Invalid GZIP header");
        }

        input.position(input.position() + 6); // skip MTIME(4) + XFL(1) + OS(1)

        // Handle optional GZIP fields based on FLG bits
        if ((flg & FEXTRA) != 0) {
            final int xlen = input.getShort() & 0xFFFF;
            input.position(input.position() + xlen); // skip extra field (includes BGZF subfield if present)
        }
        if ((flg & FNAME) != 0) {
            while (input.get() != 0) {} // skip null-terminated filename
        }
        if ((flg & FCOMMENT) != 0) {
            while (input.get() != 0) {} // skip null-terminated comment
        }
        if ((flg & FHCRC) != 0) {
            input.position(input.position() + 2); // skip header CRC16
        }

        // The deflated data is between the current position and 8 bytes before the end
        final int deflatedStart = input.position();
        final int deflatedEnd = input.limit() - GZIP_TRAILER_SIZE;
        final int deflatedSize = deflatedEnd - deflatedStart;
        if (deflatedSize < 0) {
            throw new IllegalArgumentException("Invalid GZIP block: no room for deflated data and trailer");
        }

        // Extract deflated bytes for inflater (byte[] API for compatibility with LibdeflateInflater)
        final byte[] deflatedBytes;
        final int deflatedOff;
        if (input.hasArray()) {
            deflatedBytes = input.array();
            deflatedOff = input.arrayOffset() + deflatedStart;
        } else {
            deflatedBytes = new byte[deflatedSize];
            input.position(deflatedStart);
            input.get(deflatedBytes);
            deflatedOff = 0;
        }

        inflater.reset();
        inflater.setInput(deflatedBytes, deflatedOff, deflatedSize);

        // Inflate into output
        try {
            int totalInflated = 0;
            while (!inflater.finished() && output.hasRemaining()) {
                final int n =
                        inflater.inflate(output.array(), output.arrayOffset() + output.position(), output.remaining());
                output.position(output.position() + n);
                totalInflated += n;
            }

            // Read trailer: CRC32 + ISIZE
            input.position(deflatedEnd);
            final int expectedCrc = input.getInt();
            final int expectedSize = input.getInt();

            if (totalInflated != expectedSize) {
                throw new IllegalStateException(
                        String.format("GZIP ISIZE mismatch: expected %d, got %d", expectedSize, totalInflated));
            }

            // Validate CRC32 if enabled
            if (checkCrcs) {
                crc32.reset();
                final ByteBuffer outputSlice = output.duplicate();
                outputSlice.flip();
                // Position to where we started writing
                outputSlice.position(output.position() - totalInflated);
                crc32.update(outputSlice);
                if ((int) crc32.getValue() != expectedCrc) {
                    throw new IllegalStateException(String.format(
                            "GZIP CRC32 mismatch: expected %08x, got %08x", expectedCrc, (int) crc32.getValue()));
                }
            }

            return totalInflated;
        } catch (final DataFormatException e) {
            throw new IllegalStateException("Error inflating GZIP data", e);
        }
    }

    /**
     * Decompress GZIP or BGZF data and return a new ByteBuffer containing the result.
     * Reads the ISIZE field from the GZIP trailer to determine the output size.
     *
     * @param input compressed data (from position to limit; position is advanced)
     * @return a new ByteBuffer containing the decompressed data, positioned at 0 with limit at the end
     */
    public ByteBuffer decompress(final ByteBuffer input) {
        // Read ISIZE from the last 4 bytes of the GZIP block to size the output
        final int isizeOffset = input.limit() - 4;
        final int isize = input.duplicate()
                .order(ByteOrder.LITTLE_ENDIAN)
                .position(isizeOffset)
                .getInt();
        final ByteBuffer output = ByteBuffer.allocate(isize);
        decompress(input, output);
        output.flip();
        return output;
    }
}
