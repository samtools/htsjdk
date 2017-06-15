/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

import htsjdk.samtools.util.zip.DeflaterFactory;

import java.io.File;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Single threaded realisation of {@link AbstractBlockCompressedOutputStream}
 * Each block compressed one-by-one synchronously.
 *
 * @see AbstractBlockCompressedOutputStream
 */
public class BlockCompressedOutputStream
        extends AbstractBlockCompressedOutputStream {

    private final byte[] compressedBuffer =
            new byte[BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE -
                    BlockCompressedStreamConstants.BLOCK_HEADER_LENGTH];
    private final Deflater deflater;

    // A second deflater is created for the very unlikely case where the regular deflation actually makes
    // things bigger, and the compressed block is too big.  It should be possible to downshift the
    // primary deflater to NO_COMPRESSION level, recompress, and then restore it to its original setting,
    // but in practice that doesn't work.
    // The motivation for deflating at NO_COMPRESSION level is that it will predictably produce compressed
    // output that is 10 bytes larger than the input, and the threshold at which a block is generated is such that
    // the size of tbe final gzip block will always be <= 64K.  This is preferred over the previous method,
    // which would attempt to compress up to 64K bytes, and if the resulting compressed block was too large,
    // try compressing fewer input bytes (aka "downshifting').  The problem with downshifting is that
    // getFilePointer might return an inaccurate value.
    // I assume (AW 29-Oct-2013) that there is no value in using hardware-assisted deflater for no-compression mode,
    // so just use JDK standard.
    private final Deflater noCompressionDeflater = new Deflater(Deflater.NO_COMPRESSION, true);
    private final CRC32 crc32 = new CRC32();

    /**
     * Uses default compression level, which is 5 unless changed by setCompressionLevel
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #BlockCompressedOutputStream(File, int, DeflaterFactory)} to specify a custom factory.
     */
    public BlockCompressedOutputStream(final String filename) {
        this(filename, defaultCompressionLevel);
    }

    /**
     * Uses default compression level, which is 5 unless changed by setCompressionLevel
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #BlockCompressedOutputStream(File, int, DeflaterFactory)} to specify a custom factory.
     */
    public BlockCompressedOutputStream(final File file) {
        this(file, defaultCompressionLevel);
    }

    /**
     * Prepare to compress at the given compression level
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * @param compressionLevel 1 <= compressionLevel <= 9
     */
    public BlockCompressedOutputStream(final String filename, final int compressionLevel) {
        this(new File(filename), compressionLevel);
    }

    /**
     * Prepare to compress at the given compression level
     * @param compressionLevel 1 <= compressionLevel <= 9
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #BlockCompressedOutputStream(File, int, DeflaterFactory)} to specify a custom factory.
     */
    public BlockCompressedOutputStream(final File file, final int compressionLevel) {
        this(file, compressionLevel, defaultDeflaterFactory);
    }

    /**
     * Prepare to compress at the given compression level
     * @param compressionLevel 1 <= compressionLevel <= 9
     * @param deflaterFactory custom factory to create deflaters (overrides the default)
     */
    public BlockCompressedOutputStream(final File file, final int compressionLevel, final DeflaterFactory deflaterFactory) {
       super(file);
        deflater = deflaterFactory.makeDeflater(compressionLevel, true);
    }

    /**
     * Uses default compression level, which is 5 unless changed by setCompressionLevel
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #BlockCompressedOutputStream(OutputStream, File, int, DeflaterFactory)} to specify a custom factory.
     *
     * @param file may be null
     */
    public BlockCompressedOutputStream(final OutputStream os, final File file) {
        this(os, file, defaultCompressionLevel);
    }

    /**
     * Note: this constructor uses the default {@link DeflaterFactory}, see {@link #getDefaultDeflaterFactory()}.
     * Use {@link #BlockCompressedOutputStream(OutputStream, File, int, DeflaterFactory)} to specify a custom factory.
     */
    public BlockCompressedOutputStream(final OutputStream os, final File file, final int compressionLevel) {
        this(os, file, compressionLevel, defaultDeflaterFactory);
    }

    /**
     * Creates the output stream.
     * @param os output stream to create a BlockCompressedOutputStream from
     * @param file file to which to write the output or null if not available
     * @param compressionLevel the compression level (0-9)
     * @param deflaterFactory custom factory to create deflaters (overrides the default)
     */
    public BlockCompressedOutputStream(final OutputStream os, final File file, final int compressionLevel, final DeflaterFactory deflaterFactory) {
        super(os, file);
        deflater = deflaterFactory.makeDeflater(compressionLevel, true);
    }

    @Override
    protected int deflateBlock() {
        if (numUncompressedBytes == 0) {
            return 0;
        }
        final int bytesToCompress = numUncompressedBytes;
        // Compress the input
        deflater.reset();
        deflater.setInput(uncompressedBuffer, 0, bytesToCompress);
        deflater.finish();
        int compressedSize = deflater.deflate(compressedBuffer, 0, compressedBuffer.length);

        // If it didn't all fit in compressedBuffer.length, set compression level to NO_COMPRESSION
        // and try again.  This should always fit.
        if (!deflater.finished()) {
            noCompressionDeflater.reset();
            noCompressionDeflater.setInput(uncompressedBuffer, 0, bytesToCompress);
            noCompressionDeflater.finish();
            compressedSize = noCompressionDeflater.deflate(compressedBuffer, 0, compressedBuffer.length);
            if (!noCompressionDeflater.finished()) {
                throw new IllegalStateException("unpossible");
            }
        }
        // Data compressed small enough, so write it out.
        crc32.reset();
        crc32.update(uncompressedBuffer, 0, bytesToCompress);

        final int totalBlockSize = writeGzipBlock(this.compressedBuffer, compressedSize, bytesToCompress, crc32.getValue());
        assert (bytesToCompress <= numUncompressedBytes);

        // Clear out from uncompressedBuffer the data that was written
        if (bytesToCompress == numUncompressedBytes) {
            numUncompressedBytes = 0;
        } else {
            System.arraycopy(uncompressedBuffer, bytesToCompress, uncompressedBuffer, 0,
                    numUncompressedBytes - bytesToCompress);
            numUncompressedBytes -= bytesToCompress;
        }
        mBlockAddress += totalBlockSize;
        return totalBlockSize;
    }

}
