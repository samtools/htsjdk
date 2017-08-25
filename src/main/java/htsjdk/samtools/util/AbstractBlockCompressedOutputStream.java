/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.Deflater;

import static htsjdk.samtools.util.BlockCompressedStreamConstants.DEFAULT_COMPRESSION_LEVEL;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.GZIP_ID1;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.GZIP_ID2;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.GZIP_XLEN;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.GZIP_XFL;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.GZIP_FLG;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.BLOCK_HEADER_LENGTH;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.GZIP_CM_DEFLATE;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.GZIP_OS_UNKNOWN;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.BGZF_ID1;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.BGZF_ID2;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.BGZF_LEN;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.BLOCK_FOOTER_LENGTH;
import static htsjdk.samtools.util.BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK;

/**
 * Writer for a file that is a series of gzip blocks (BGZF format).  The caller just treats it as an
 * OutputStream, and under the covers a gzip block is written when the amount of uncompressed as-yet-unwritten
 * bytes reaches a threshold.
 *
 * The advantage of BGZF over conventional gzip is that BGZF allows for seeking without having to scan through
 * the entire file up to the position being sought.
 *
 * Note that the flush() method should not be called by client
 * unless you know what you're doing, because it forces a gzip block to be written even if the
 * number of buffered bytes has not reached threshold.  close(), on the other hand, must be called
 * when done writing in order to force the last gzip block to be written.
 *
 * c.f. http://samtools.sourceforge.net/SAM1.pdf for details of BGZF file format.
 */
public abstract class AbstractBlockCompressedOutputStream extends OutputStream implements LocationAware {

    protected static DeflaterFactory defaultDeflaterFactory = new DeflaterFactory();
    protected static int defaultCompressionLevel = DEFAULT_COMPRESSION_LEVEL;

    protected final BinaryCodec codec;
    protected final byte[] uncompressedBuffer = new byte[DEFAULT_UNCOMPRESSED_BLOCK_SIZE];
    protected int numUncompressedBytes = 0;
    protected long mBlockAddress = 0;
    protected File file = null;

    // Really a local variable, but allocate once to reduce GC burden.
    protected final byte[] singleByteArray = new byte[1];


    /**
     * Sets the default {@link DeflaterFactory} that will be used for all instances unless specified otherwise in the constructor.
     * If this method is not called the default is a factory that will create the JDK {@link Deflater}.
     * @param deflaterFactory non-null default factory.
     */
    public static void setDefaultDeflaterFactory(final DeflaterFactory deflaterFactory) {
        if (deflaterFactory == null) {
            throw new IllegalArgumentException("null deflaterFactory");
        }
        defaultDeflaterFactory = deflaterFactory;
    }

    public static DeflaterFactory getDefaultDeflaterFactory() {
        return defaultDeflaterFactory;
    }

    /**
     * Sets the GZip compression level for subsequent BlockCompressedOutputStream object creation
     * that do not specify the compression level.
     * @param compressionLevel 1 <= compressionLevel <= 9
     */
    public static void setDefaultCompressionLevel(final int compressionLevel) {
        if (compressionLevel < Deflater.NO_COMPRESSION || compressionLevel > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("Invalid compression level: " + compressionLevel);
        }
        defaultCompressionLevel = compressionLevel;
    }

    public static int getDefaultCompressionLevel() {
        return defaultCompressionLevel;
    }

    /**
     * Prepare to compress at the given compression level
     * @param file file to output
     */
    public AbstractBlockCompressedOutputStream(final File file) {
        this.file = file;
        codec = new BinaryCodec(file, true);
    }


    /**
     * Prepare to compress at the given compression level
     * @param os output stream opened on the file
     * @param file file to output
     */
    public AbstractBlockCompressedOutputStream(final OutputStream os, final File file) {
        this.file = file;
        codec = new BinaryCodec(os);
        if (file != null) {
            codec.setOutputFileName(file.getAbsolutePath());
        }
    }

    /**
     * Create new {@link AbstractBlockCompressedOutputStream} or return exactly the same if it already the {@link AbstractBlockCompressedOutputStream}
     * @param location May be null.  Used for error messages, and for checking file termination.
     * @param output May or not already be a BlockCompressedOutputStream.
     * @return A BlockCompressedOutputStream, either by wrapping the given OutputStream, or by casting if it already
     *         is a BCOS.
     */
    public static AbstractBlockCompressedOutputStream maybeBgzfWrapOutputStream(final File location, OutputStream output) {
        if (!(output instanceof AbstractBlockCompressedOutputStream)) {
                return Defaults.ZIP_THREADS > 0
                        ? new ParallelBlockCompressedOutputStream(output, location)
                        : new BlockCompressedOutputStream(output, location);
        } else {
        return (AbstractBlockCompressedOutputStream)output;
        }
    }

    /**
     * Writes b.length bytes from the specified byte array to this output stream. The general contract for write(b)
     * is that it should have exactly the same effect as the call write(b, 0, b.length).
     * @param bytes the data
     */
    @Override
    public void write(final byte[] bytes) throws IOException {
        write(bytes, 0, bytes.length);
    }

    /**
     * Writes len bytes from the specified byte array starting at offset off to this output stream. The general
     * contract for write(b, off, len) is that some of the bytes in the array b are written to the output stream in order;
     * element b[off] is the first byte written and b[off+len-1] is the last byte written by this operation.
     *
     * @param bytes the data
     * @param startIndex the start offset in the data
     * @param numBytes the number of bytes to write
     */
    @Override
    public void write(final byte[] bytes, int startIndex, int numBytes) throws IOException {
        assert(numUncompressedBytes < uncompressedBuffer.length);
        while (numBytes > 0) {
            final int bytesToWrite = Math.min(uncompressedBuffer.length - numUncompressedBytes, numBytes);
            System.arraycopy(bytes, startIndex, uncompressedBuffer, numUncompressedBytes, bytesToWrite);
            numUncompressedBytes += bytesToWrite;
            startIndex += bytesToWrite;
            numBytes -= bytesToWrite;
            assert(numBytes >= 0);
            if (numUncompressedBytes == uncompressedBuffer.length) {
                deflateBlock();
            }
        }
    }

    /**
     * WARNING: flush() affects the output format, because it causes the current contents of uncompressedBuffer
     * to be compressed and written, even if it isn't full.  Unless you know what you're doing, don't call flush().
     * Instead, call close(), which will flush any unwritten data before closing the underlying stream.
     *
     */
    @Override
    public void flush() throws IOException {
        while (numUncompressedBytes > 0) {
            deflateBlock();
        }
        codec.getOutputStream().flush();
    }

    /**
     * close() must be called in order to flush any remaining buffered bytes.  An unclosed file will likely be
     * defective.
     *
     */
    @Override
    public void close() throws IOException {
        flush();
        // For debugging...
        // if (numberOfThrottleBacks > 0) {
        //     System.err.println("In BlockCompressedOutputStream, had to throttle back " + numberOfThrottleBacks +
        //                        " times for file " + codec.getOutputFileName());
        // }
        codec.writeBytes(EMPTY_GZIP_BLOCK);
        codec.close();
        // Can't re-open something that is not a regular file, e.g. a named pipe or an output stream
        if (this.file == null || !this.file.isFile() || !Files.isRegularFile(this.file.toPath())) return;
        if (BlockCompressedInputStream.checkTermination(this.file) !=
                BlockCompressedInputStream.FileTermination.HAS_TERMINATOR_BLOCK) {
            throw new IOException("Terminator block not found after closing BGZF file " + this.file);
        }
    }

    /**
     * Writes the specified byte to this output stream. The general contract for write is that one byte is written
     * to the output stream. The byte to be written is the eight low-order bits of the argument b.
     * The 24 high-order bits of b are ignored.
     * @param bite
     * @throws IOException
     */
    @Override
    public void write(final int bite) throws IOException {
        singleByteArray[0] = (byte)bite;
        write(singleByteArray);
    }

    /** Encode virtual file pointer
     * Upper 48 bits is the byte offset into the compressed stream of a block.
     * Lower 16 bits is the byte offset into the uncompressed stream inside the block.
     */
    public long getFilePointer(){
        return BlockCompressedFilePointerUtil.makeFilePointer(mBlockAddress, numUncompressedBytes);
    }

    @Override
    public long getPosition() {
        return getFilePointer();
    }

    /**
     * Attempt to write the data in uncompressedBuffer to the underlying file in a gzip block.
     * If the entire uncompressedBuffer does not fit in the maximum allowed size, reduce the amount
     * of data to be compressed, and slide the excess down in uncompressedBuffer so it can be picked
     * up in the next deflate event.
     * @return size of gzip block that was written.
     */
    protected abstract int deflateBlock();

    /**
     * Writes the entire gzip block, assuming the compressed data is stored in uncompressedBuffer
     * @return  size of gzip block that was written.
     */
    protected int writeGzipBlock(final byte[] compressedBuffer, final int compressedSize, final int uncompressedSize, final long crc) {
        // Init gzip header
        codec.writeByte(GZIP_ID1);
        codec.writeByte(GZIP_ID2);
        codec.writeByte(GZIP_CM_DEFLATE);
        codec.writeByte(GZIP_FLG);
        codec.writeInt(0); // Modification time
        codec.writeByte(GZIP_XFL);
        codec.writeByte(GZIP_OS_UNKNOWN);
        codec.writeShort(GZIP_XLEN);
        codec.writeByte(BGZF_ID1);
        codec.writeByte(BGZF_ID2);
        codec.writeShort(BGZF_LEN);
        final int totalBlockSize = compressedSize + BLOCK_HEADER_LENGTH +
                BLOCK_FOOTER_LENGTH;

        // I don't know why we store block size - 1, but that is what the spec says
        codec.writeShort((short)(totalBlockSize - 1));
        codec.writeBytes(compressedBuffer, 0, compressedSize);
        codec.writeInt((int)crc);
        codec.writeInt(uncompressedSize);
        return totalBlockSize;
    }
}
